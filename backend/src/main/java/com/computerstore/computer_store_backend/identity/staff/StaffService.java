package com.computerstore.computer_store_backend.identity.staff;

import com.computerstore.computer_store_backend.identity.authorization.EmployeeRole;
import com.computerstore.computer_store_backend.identity.authorization.PermissionCode;
import com.computerstore.computer_store_backend.identity.authorization.ResolvedAccess;
import com.computerstore.computer_store_backend.identity.authorization.RoleResolutionService;
import com.computerstore.computer_store_backend.identity.entity.AppRole;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.entity.UserRole;
import com.computerstore.computer_store_backend.identity.repository.AppRoleRepository;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.repository.UserRoleRepository;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.pagination.ListPagination;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import com.computerstore.computer_store_backend.shared.validation.IdentifierNormalizer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static com.computerstore.computer_store_backend.identity.authorization.PermissionCode.*;

@Service
@Transactional(readOnly = true)
public class StaffService {
    private static final List<PermissionCode> ACTIONS =
            List.of(EMPLOYEE_CREATE, EMPLOYEE_UPDATE, EMPLOYEE_ROLE_ASSIGN, EMPLOYEE_LOCK, EMPLOYEE_UNLOCK);
    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final UserRoleRepository assignments;
    private final RoleResolutionService authorization;
    private final PasswordEncoder passwords;
    private final UtcTimeProvider time;

    public StaffService(AppUserRepository users, AppRoleRepository roles, UserRoleRepository assignments,
            RoleResolutionService authorization, PasswordEncoder passwords, UtcTimeProvider time) {
        this.users = users;
        this.roles = roles;
        this.assignments = assignments;
        this.authorization = authorization;
        this.passwords = passwords;
        this.time = time;
    }

    public StaffResponse.Page list(String q, Boolean active, int page, int size) {
        var access = require(EMPLOYEE_READ);
        var pagination = ListPagination.request(page, size, "fullName", StaffService::invalid);
        var search = text(q, 150, false);
        var pattern = search == null ? null
                : "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = users.searchStaff(pattern, active, pagination);
        var ids = result.getContent().stream().map(AppUser::getId).toList();
        var linked = links(ids).stream().collect(Collectors.groupingBy(link -> link.getUser().getId()));
        var items = result.getContent().stream().map(user -> response(user, linked.getOrDefault(user.getId(), List.of()))).toList();
        var allowed = ACTIONS.stream().filter(code -> has(access, code)).map(PermissionCode::code).toList();
        return new StaffResponse.Page(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), allowed);
    }

    public StaffResponse detail(String id) {
        require(EMPLOYEE_READ);
        var user = user(id);
        return response(user, links(List.of(user.getId())));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StaffResponse create(StaffRequests.Create input) {
        beginWrite(EMPLOYEE_CREATE, EMPLOYEE_ROLE_ASSIGN);
        var username = IdentifierNormalizer.username(input.username());
        if (username == null || username.isEmpty() || username.length() > 100) throw invalid();
        var selected = selectedRoles(input.roles());
        if (users.existsByUsername(username)) throw conflict("This username is already in use.");
        var user = BeanUtils.instantiateClass(AppUser.class);
        user.setUsername(username);
        updateInformation(user, input.fullName(), input.email(), input.phone());
        user.setPasswordHash(encode(input.password()));
        user.setActive(true);
        user.setCreatedAt(user.getUpdatedAt());
        save(user);
        var links = selected.stream().map(role -> link(user, role)).toList();
        assignments.saveAllAndFlush(links);
        return response(user, links);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StaffResponse update(String id, StaffRequests.Update input) {
        beginWrite(EMPLOYEE_UPDATE);
        var user = user(id);
        updateInformation(user, input.fullName(), input.email(), input.phone());
        save(user);
        return response(user, links(List.of(user.getId())));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StaffResponse assignRoles(String id, StaffRequests.AssignRoles input) {
        var access = beginWrite(EMPLOYEE_ROLE_ASSIGN);
        var user = user(id);
        var selected = selectedRoles(input.roles());
        var existing = links(List.of(user.getId()));
        var chosen = selected.stream().map(AppRole::getCode).collect(Collectors.toSet());
        var removed = existing.stream().filter(link -> !chosen.contains(link.getRole().getCode())).toList();
        if (isAdmin(removed)) protectAdmin(access, user);

        var current = existing.stream().map(link -> link.getRole().getCode()).collect(Collectors.toSet());
        var added = selected.stream().filter(role -> !current.contains(role.getCode())).map(role -> link(user, role)).toList();
        if (!removed.isEmpty() || !added.isEmpty()) {
            assignments.deleteAll(removed);
            assignments.saveAllAndFlush(added);
            user.setUpdatedAt(time.now());
        }
        var kept = new java.util.ArrayList<>(existing);
        kept.removeAll(removed);
        kept.addAll(added);
        return response(user, kept);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StaffResponse setActive(String id, boolean active) {
        var access = beginWrite(active ? EMPLOYEE_UNLOCK : EMPLOYEE_LOCK);
        var user = user(id);
        var links = links(List.of(user.getId()));
        if (!active && isAdmin(links)) protectAdmin(access, user);
        if (user.getActive() != active) {
            user.setActive(active);
            user.setUpdatedAt(time.now());
            save(user);
        }
        return response(user, links);
    }

    private ResolvedAccess beginWrite(PermissionCode... codes) {
        // Same sentinel as initial ADMIN bootstrap. Take it before reading actor, target or links.
        // READ_COMMITTED prevents a second writer from authorizing against a pre-lock snapshot.
        roles.lockAdminRole().orElseThrow(() -> conflict("The ADMIN role is unavailable."));
        return require(codes);
    }

    private ResolvedAccess require(PermissionCode... codes) {
        var access = authorization.resolveCurrent();
        if (!access.roles().contains(EmployeeRole.ADMIN)) throw denied();
        for (var code : codes) if (!has(access, code)) throw denied();
        return access;
    }

    private static boolean has(ResolvedAccess access, PermissionCode code) {
        return code.status() == PermissionCode.Status.ELIGIBLE_PROPOSED && access.permissionCodes().contains(code.code());
    }

    private void protectAdmin(ResolvedAccess access, AppUser target) {
        if (Objects.equals(access.actor().userId(), target.getId())) throw denied();
        if (target.getActive() && assignments.activeAdmins() <= 1) {
            throw conflict("At least one active administrator is required.");
        }
    }

    private List<AppRole> selectedRoles(List<String> requested) {
        if (requested == null || requested.isEmpty() || requested.size() > EmployeeRole.values().length) throw invalid();
        var codes = new HashSet<String>();
        for (var role : requested) {
            if (!validRole(role) || !codes.add(role)) throw invalid();
        }
        var selected = roles.findByCodeIn(codes);
        if (selected.size() != codes.size()) throw conflict("A requested staff role is unavailable.");
        return selected;
    }

    private static boolean validRole(String role) {
        if (role == null) return false;
        try { EmployeeRole.valueOf(role); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }

    private AppUser user(String id) {
        long value;
        try { value = Long.parseLong(id); }
        catch (NumberFormatException failure) { throw invalid(); }
        if (value <= 0 || !Long.toString(value).equals(id)) throw invalid();
        return users.findById(value).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.detail()));
    }

    private List<UserRole> links(List<Long> userIds) {
        return userIds.isEmpty() ? List.of() : assignments.findForStaff(userIds);
    }

    private UserRole link(AppUser user, AppRole role) {
        var linked = BeanUtils.instantiateClass(UserRole.class);
        linked.setUser(user);
        linked.setRole(role);
        linked.setAssignedAt(time.now());
        return linked;
    }

    private static boolean isAdmin(List<UserRole> links) {
        return links.stream().anyMatch(link -> EmployeeRole.ADMIN.name().equals(link.getRole().getCode()));
    }

    private void updateInformation(AppUser user, String fullName, String email, String phone) {
        var normalizedEmail = IdentifierNormalizer.userEmail(email);
        if (normalizedEmail != null && !normalizedEmail.isEmpty()) {
            users.findByEmail(normalizedEmail).filter(other -> !Objects.equals(other.getId(), user.getId()))
                    .ifPresent(other -> { throw conflict("This email is already in use."); });
        }
        user.setFullName(text(fullName, 150, true));
        user.setEmail(text(normalizedEmail, 254, false));
        user.setPhone(text(phone, 30, false));
        user.setUpdatedAt(time.now());
    }

    private String encode(char[] password) {
        if (password == null || password.length == 0 || CharBuffer.wrap(password).chars().allMatch(Character::isWhitespace)) {
            throw invalid();
        }
        try {
            var bytes = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password));
            try { if (bytes.remaining() > 72) throw invalid(); }
            finally {
                bytes.clear();
                while (bytes.hasRemaining()) bytes.put((byte) 0);
            }
        } catch (CharacterCodingException failure) { throw invalid(); }
        return passwords.encode(CharBuffer.wrap(password));
    }

    private void save(AppUser user) {
        try { users.saveAndFlush(user); }
        catch (DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation) {
                    var name = violation.getConstraintName();
                    if (name != null && (name.endsWith("uq_app_user_username") || name.endsWith("uq_app_user_email"))) {
                        throw conflict("This username or email is already in use.");
                    }
                }
            }
            throw failure;
        }
    }

    private static StaffResponse response(AppUser user, List<UserRole> links) {
        return new StaffResponse(user.getId().toString(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getActive(), links.stream().map(link -> link.getRole().getCode())
                        .filter(StaffService::validRole).sorted().toList(), user.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    private static String text(String value, int max, boolean required) {
        var clean = value == null ? "" : value.strip();
        if ((required && clean.isEmpty()) || clean.codePointCount(0, clean.length()) > max
                || !StandardCharsets.UTF_8.newEncoder().canEncode(clean)
                || clean.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return clean.isEmpty() ? null : clean;
    }

    static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.detail());
    }

    private static BusinessException conflict(String reason) {
        return new BusinessException(ErrorCode.BUSINESS_CONFLICT, reason);
    }

    private static BusinessException denied() {
        return new BusinessException(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.detail());
    }
}
