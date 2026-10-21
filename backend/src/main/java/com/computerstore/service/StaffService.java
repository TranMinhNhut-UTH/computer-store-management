package com.computerstore.service;

import com.computerstore.dto.PagedResult;
import com.computerstore.dto.StaffDtos.*;
import com.computerstore.dto.UserDto;
import com.computerstore.entity.Role;
import com.computerstore.entity.RoleCodes;
import com.computerstore.entity.User;
import com.computerstore.entity.UserStatus;
import com.computerstore.exception.AppException;
import com.computerstore.repository.RefreshTokenRepository;
import com.computerstore.repository.RoleRepository;
import com.computerstore.repository.UserRepository;
import com.computerstore.util.IdentifierUtil;
import com.computerstore.util.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

/** FR-05: Quản lý tạo và quản lý tài khoản nhân viên (phân quyền theo vai trò). */
@Service
@RequiredArgsConstructor
@Transactional
public class StaffService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final CurrentUser currentUser;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PagedResult<UserDto> list(String search, String roleCode, UserStatus status, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        String keyword = StringUtils.hasText(search) ? search.trim() : null;
        String role = StringUtils.hasText(roleCode) ? roleCode.trim() : null;

        Page<User> result = users.searchStaff(RoleCodes.CUSTOMER, role, status, keyword,
                PageRequest.of(p - 1, s, Sort.by("fullName")));
        return new PagedResult<>(result.getContent().stream().map(UserDto::from).toList(),
                result.getTotalElements(), p, s);
    }

    public UserDto create(CreateStaffRequest req) {
        Role role = staffRole(req.roleCode());
        String email = IdentifierUtil.normalizeEmail(req.email());
        String phone = StringUtils.hasText(req.phoneNumber()) ? IdentifierUtil.normalizePhone(req.phoneNumber()) : null;
        PasswordPolicy.ensureStrong(req.password());

        if (users.existsByEmail(email)) throw new AppException(409, "Email đã được sử dụng.");
        if (phone != null && users.existsByPhoneNumber(phone)) throw new AppException(409, "Số điện thoại đã được sử dụng.");

        User user = new User();
        user.setRole(role);
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setVerified(true);          // do Quản lý tạo nên không cần OTP
        users.save(user);

        audit.log("STAFF_CREATE", "User", String.valueOf(user.getId()), null,
                AuditService.fields("fullName", user.getFullName(), "email", email,
                        "phoneNumber", phone, "role", role.getCode()), null);
        return UserDto.from(user);
    }

    public UserDto update(Long id, UpdateStaffRequest req) {
        User user = findStaff(id);
        Role role = staffRole(req.roleCode());
        String phone = StringUtils.hasText(req.phoneNumber()) ? IdentifierUtil.normalizePhone(req.phoneNumber()) : null;

        if (id.equals(currentUser.userId().orElse(null)) && !role.getCode().equals(user.getRole().getCode())) {
            throw new AppException(400, "Không thể tự thay đổi vai trò của chính mình.");
        }
        if (phone != null && users.existsByPhoneNumberAndIdNot(phone, id)) {
            throw new AppException(409, "Số điện thoại đã được sử dụng.");
        }

        var old = AuditService.fields("fullName", user.getFullName(),
                "phoneNumber", user.getPhoneNumber(), "role", user.getRole().getCode());
        user.setFullName(req.fullName().trim());
        user.setPhoneNumber(phone);
        user.setRole(role);

        audit.log("STAFF_UPDATE", "User", String.valueOf(id), old,
                AuditService.fields("fullName", user.getFullName(), "phoneNumber", phone, "role", role.getCode()), null);
        return UserDto.from(user);
    }

    public void setStatus(Long id, UserStatus status) {
        if (id.equals(currentUser.userId().orElse(null))) {
            throw new AppException(400, "Không thể tự khóa tài khoản của chính mình.");
        }
        User user = findStaff(id);
        UserStatus old = user.getStatus();
        user.setStatus(status);

        if (status == UserStatus.LOCKED) {
            Instant now = Instant.now();
            refreshTokens.findByUserIdAndRevokedAtIsNull(id).forEach(t -> t.setRevokedAt(now));
        }
        audit.log(status == UserStatus.LOCKED ? "STAFF_LOCK" : "STAFF_UNLOCK", "User", String.valueOf(id),
                AuditService.fields("status", old), AuditService.fields("status", status), null);
    }

    private User findStaff(Long id) {
        return users.findByIdAndRoleCodeNot(id, RoleCodes.CUSTOMER)
                .orElseThrow(() -> new AppException(404, "Không tìm thấy nhân viên."));
    }

    private Role staffRole(String code) {
        if (!RoleCodes.STAFF.contains(code)) {
            throw new AppException(400, "Vai trò không hợp lệ cho nhân viên.");
        }
        return roles.findByCode(code).orElseThrow(() -> new AppException(500, "Hệ thống chưa khởi tạo vai trò."));
    }
}
