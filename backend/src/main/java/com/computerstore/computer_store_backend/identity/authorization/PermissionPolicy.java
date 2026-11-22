package com.computerstore.computer_store_backend.identity.authorization;

import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Catalog boundary for future TV1/TV2 use cases. Required codes are chosen by server policy,
 * never by the request. Does not grant roles, approve policies or replace state/scope/transaction rules.
 */
@Service
public class PermissionPolicy {
    private final AuthorizationService authorization;

    public PermissionPolicy(AuthorizationService authorization) {
        this.authorization = authorization;
    }

    public CurrentActor requirePermission(String requiredCode) {
        // Reuse the existing identity/mapping check first, preserving anonymous 401 and its transaction contract.
        var actor = authorization.requirePermission(requiredCode);
        requireEligible(requiredCode);
        return actor;
    }

    /** Authorize a read and report only the eligible actions offered by that use case. */
    public List<String> availableActions(PermissionCode required, PermissionCode... offered) {
        var access = authorization.resolveWithPermission(required.code());
        requireEligible(required.code());
        return Arrays.stream(offered)
                .filter(code -> code.status() == PermissionCode.Status.ELIGIBLE_PROPOSED)
                .map(PermissionCode::code)
                .filter(access.permissionCodes()::contains)
                .toList();
    }

    private static void requireEligible(String code) {
        // A mistaken DB mapping cannot activate provisional or uncatalogued permissions.
        if (PermissionCode.fromCode(code).filter(permission ->
                permission.status() == PermissionCode.Status.ELIGIBLE_PROPOSED).isEmpty()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.detail());
        }
    }
}
