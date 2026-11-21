package com.computerstore.computer_store_backend.identity.authorization;

import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
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
        var permission = PermissionCode.fromCode(requiredCode);
        // A mistaken DB mapping cannot activate provisional or uncatalogued permissions.
        if (permission.isEmpty() || permission.get().status() != PermissionCode.Status.ELIGIBLE_PROPOSED) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.detail());
        }
        return actor;
    }
}
