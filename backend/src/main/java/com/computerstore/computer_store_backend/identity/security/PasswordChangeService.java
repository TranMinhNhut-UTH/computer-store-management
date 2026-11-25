package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.identity.authorization.CurrentActorService;
import com.computerstore.computer_store_backend.identity.dto.request.ChangePasswordRequest;
import com.computerstore.computer_store_backend.identity.repository.AppRoleRepository;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import java.nio.CharBuffer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordChangeService {
    private final AppRoleRepository roles;
    private final CurrentActorService actors;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final UtcTimeProvider time;

    public PasswordChangeService(AppRoleRepository roles, CurrentActorService actors, AppUserRepository users,
            PasswordEncoder passwords, UtcTimeProvider time) {
        this.roles = roles;
        this.actors = actors;
        this.users = users;
        this.passwords = passwords;
        this.time = time;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void change(ChangePasswordRequest input) {
        var current = input.currentPassword() == null ? null : CharBuffer.wrap(input.currentPassword());
        var replacement = input.newPassword() == null ? null : CharBuffer.wrap(input.newPassword());
        if (!BcryptPasswordInput.isValid(current) || !BcryptPasswordInput.isValid(replacement)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.detail());
        }
        // Use the same lock as Staff writes before reading the actor, hash or version.
        // A waiting request must recheck its session after the previous write commits.
        roles.lockAdminRole().orElseThrow(() ->
                new BusinessException(ErrorCode.BUSINESS_CONFLICT, "The account could not be updated."));
        var actor = actors.requireActor();
        var user = users.findById(actor.userId()).orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.detail()));
        if (!passwords.matches(current, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The current password is incorrect.");
        }
        user.setPasswordHash(passwords.encode(replacement));
        user.revokeSessions();
        user.setUpdatedAt(time.now());
        users.saveAndFlush(user);
    }
}
