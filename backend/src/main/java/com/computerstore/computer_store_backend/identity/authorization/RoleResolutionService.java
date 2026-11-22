package com.computerstore.computer_store_backend.identity.authorization;

import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleResolutionService {
    static final String ROLES_QUERY = "select distinct r.code from UserRole ur join ur.role r where ur.user.id = :userId";
    static final String PERMISSIONS_QUERY = "select distinct p.code from UserRole ur, RolePermission rp "
            + "join rp.permission p where ur.user.id = :userId and rp.role = ur.role and ur.role.code in :roleCodes";
    private final CurrentActorService actors;
    private final EntityManager entities;

    public RoleResolutionService(CurrentActorService actors, EntityManager entities) {
        this.actors = actors; this.entities = entities;
    }

    /** One read-only transaction: active account check plus at most two scalar queries, independent of role count. */
    @Transactional(readOnly = true)
    public ResolvedAccess resolveCurrent() {
        var actor = actors.requireActor();
        var codes = entities.createQuery(ROLES_QUERY, String.class)
                .setParameter("userId", actor.userId()).getResultList();
        Set<EmployeeRole> roles = new HashSet<>();
        for (String code : codes) {
            if (code == null) continue;
            try { roles.add(EmployeeRole.valueOf(code)); }
            catch (IllegalArgumentException ignored) { /* Unknown database roles grant nothing. */ }
        }
        if (roles.isEmpty()) return new ResolvedAccess(actor, roles, Set.of());
        var mapped = entities.createQuery(PERMISSIONS_QUERY, String.class)
                .setParameter("userId", actor.userId())
                .setParameter("roleCodes", roles.stream().map(Enum::name).toList()).getResultList();
        Set<String> permissions = new HashSet<>();
        for (String code : mapped) {
            if (isCanonicalPermissionCode(code)) permissions.add(code);
        }
        return new ResolvedAccess(actor, roles, permissions);
    }

    // V1 binary permission identifiers are uppercase ASCII, max VARCHAR(100); no catalog is invented here.
    static boolean isCanonicalPermissionCode(String code) {
        return code != null && code.matches("[A-Z0-9._-]{1,100}");
    }
}
