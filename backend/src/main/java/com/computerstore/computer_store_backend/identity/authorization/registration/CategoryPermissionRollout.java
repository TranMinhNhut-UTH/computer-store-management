package com.computerstore.computer_store_backend.identity.authorization.registration;

import com.computerstore.computer_store_backend.identity.authorization.EmployeeRole;
import com.computerstore.computer_store_backend.identity.authorization.PermissionCode;
import java.util.List;
import java.util.Objects;

/**
 * Category V1 policy selected for this slice only. Preparation data, never runtime grants:
 * no Spring bean, JDBC, Flyway migration, startup runner or account provisioning.
 * Applying either stage to a database requires separate operational authorization.
 */
public final class CategoryPermissionRollout {
    private CategoryPermissionRollout() { }

    public record Grant(EmployeeRole role, PermissionCode permission) {
        public Grant {
            Objects.requireNonNull(role);
            Objects.requireNonNull(permission);
            if (permission.module() != PermissionCode.Module.CATEGORY
                    || permission.status() != PermissionCode.Status.ELIGIBLE_PROPOSED
                    || role == EmployeeRole.ADMIN
                    || (role != EmployeeRole.MANAGER && permission != PermissionCode.CATEGORY_READ)) {
                throw new IllegalArgumentException("Grant is outside the selected Category V1 policy.");
            }
        }
    }

    private static final PermissionCatalogManifest STAGE_A = PermissionCatalogManifest.validate(List.of(
            new PermissionCatalogManifest.Entry("CATEGORY_READ", "CATEGORY_READ", null),
            new PermissionCatalogManifest.Entry("CATEGORY_CREATE", "CATEGORY_CREATE", null),
            new PermissionCatalogManifest.Entry("CATEGORY_UPDATE", "CATEGORY_UPDATE", null),
            new PermissionCatalogManifest.Entry("CATEGORY_DEACTIVATE", "CATEGORY_DEACTIVATE", null)));

    private static final List<Grant> STAGE_B = List.of(
            new Grant(EmployeeRole.MANAGER, PermissionCode.CATEGORY_READ),
            new Grant(EmployeeRole.MANAGER, PermissionCode.CATEGORY_CREATE),
            new Grant(EmployeeRole.MANAGER, PermissionCode.CATEGORY_UPDATE),
            new Grant(EmployeeRole.MANAGER, PermissionCode.CATEGORY_DEACTIVATE),
            new Grant(EmployeeRole.SALES_STAFF, PermissionCode.CATEGORY_READ),
            new Grant(EmployeeRole.WAREHOUSE_STAFF, PermissionCode.CATEGORY_READ));

    public static PermissionCatalogManifest stageA() { return STAGE_A; }
    public static List<Grant> stageB() { return STAGE_B; }
}
