package com.computerstore.computer_store_backend.identity.authorization;

import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.util.Arrays;
import org.springframework.stereotype.Service;

/** Guards for future server-side use cases. Calling code must choose an explicitly approved policy. */
@Service
public class AuthorizationService {
    private final RoleResolutionService access;

    public AuthorizationService(RoleResolutionService access) { this.access = access; }

    public CurrentActor requireAnyRole(EmployeeRole... allowedRoles) {
        var current = access.resolveCurrent();
        if (allowedRoles == null || allowedRoles.length == 0 || Arrays.stream(allowedRoles).anyMatch(role -> role == null)
                || Arrays.stream(allowedRoles).noneMatch(current.roles()::contains)) {
            throw denied();
        }
        return current.actor();
    }

    /** The required code must come from an approved server policy, never a request-provided permission. */
    public CurrentActor requirePermission(String requiredCode) {
        return resolveWithPermission(requiredCode).actor();
    }

    ResolvedAccess resolveWithPermission(String requiredCode) {
        var current = access.resolveCurrent();
        if (!RoleResolutionService.isCanonicalPermissionCode(requiredCode)
                || !current.permissionCodes().contains(requiredCode)) {
            throw denied();
        }
        return current;
    }

    private static BusinessException denied() {
        return new BusinessException(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.detail());
    }
}
