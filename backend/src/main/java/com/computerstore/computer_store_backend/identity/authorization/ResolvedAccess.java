package com.computerstore.computer_store_backend.identity.authorization;

import java.util.Objects;
import java.util.Set;

/** Immutable, fully materialized snapshot for one guard invocation, never cached in the session. */
public record ResolvedAccess(CurrentActor actor, Set<EmployeeRole> roles, Set<String> permissionCodes) {
    public ResolvedAccess {
        Objects.requireNonNull(actor, "actor");
        roles = Set.copyOf(roles);
        permissionCodes = Set.copyOf(permissionCodes);
    }
}
