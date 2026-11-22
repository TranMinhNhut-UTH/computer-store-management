package com.computerstore.computer_store_backend.identity.authorization.registration;

import com.computerstore.computer_store_backend.identity.authorization.EmployeeRole;
import com.computerstore.computer_store_backend.identity.authorization.PermissionCode;
import com.computerstore.computer_store_backend.identity.authorization.registration.PermissionCatalogManifest.Entry;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.computerstore.computer_store_backend.identity.authorization.EmployeeRole.*;
import static com.computerstore.computer_store_backend.identity.authorization.PermissionCode.*;

/** Review-only Product payload and reconciliation. No beans, runner, JDBC or Flyway migration. */
public final class ProductPermissionRollout {
    private ProductPermissionRollout() { }

    public record Grant(EmployeeRole role, PermissionCode permission) {
        public Grant {
            if (role == null || permission == null || permission.module() != PermissionCode.Module.PRODUCT
                    || permission.status() != PermissionCode.Status.ELIGIBLE_PROPOSED
                    || role == ADMIN || (role != MANAGER && permission != PRODUCT_READ)) {
                throw invalid("Grant is outside the Product matrix baseline.");
            }
        }
    }

    // Literal metadata. Adding a code to the live enum must not expand this snapshot.
    private static final PermissionCatalogManifest STAGE_A = PermissionCatalogManifest.validate(List.of(
            new Entry("PRODUCT_READ", "PRODUCT_READ", null),
            new Entry("PRODUCT_CREATE", "PRODUCT_CREATE", null),
            new Entry("PRODUCT_UPDATE", "PRODUCT_UPDATE", null),
            new Entry("PRODUCT_PRICE_UPDATE", "PRODUCT_PRICE_UPDATE", null),
            new Entry("PRODUCT_DEACTIVATE", "PRODUCT_DEACTIVATE", null)));

    private static final List<Grant> STAGE_B = List.of(
            new Grant(MANAGER, PRODUCT_READ),
            new Grant(MANAGER, PRODUCT_CREATE),
            new Grant(MANAGER, PRODUCT_UPDATE),
            new Grant(MANAGER, PRODUCT_PRICE_UPDATE),
            new Grant(MANAGER, PRODUCT_DEACTIVATE),
            new Grant(SALES_STAFF, PRODUCT_READ),
            new Grant(WAREHOUSE_STAFF, PRODUCT_READ));

    public static PermissionCatalogManifest proposedRegistration() { return STAGE_A; }
    public static List<Grant> baselineGrantProposals() { return STAGE_B; }

    /** Existing rows must be scoped to Product; unrelated module rows are outside this review. */
    public static List<Entry> missingRegistration(List<Entry> existingProductRows) {
        Map<String, Entry> existing = new HashMap<>();
        for (Entry row : existingProductRows) {
            if (row == null || !STAGE_A.entries().contains(row)) {
                throw invalid("Unknown Product permission or conflicting metadata.");
            }
            if (existing.putIfAbsent(row.code(), row) != null) {
                throw invalid("Duplicate Product permission row.");
            }
        }
        return STAGE_A.entries().stream().filter(entry -> !existing.containsKey(entry.code())).toList();
    }

    /** Read-only preflight for the baseline proposal, never a grant approval. */
    public static List<Grant> missingGrants(List<String> roleCodes, List<Entry> existingProductRows,
            List<Grant> existingProductGrants) {
        var roles = EnumSet.noneOf(EmployeeRole.class);
        for (String code : roleCodes) {
            if (code == null || !roles.add(EmployeeRole.valueOf(code))) {
                throw invalid("Missing or duplicate role code.");
            }
        }
        if (!roles.equals(EnumSet.allOf(EmployeeRole.class))) throw invalid("A V1 role is missing.");
        if (!missingRegistration(existingProductRows).isEmpty()) throw invalid("Product registration is incomplete.");
        Set<Grant> existing = new HashSet<>();
        for (Grant grant : existingProductGrants) {
            if (!STAGE_B.contains(grant) || !existing.add(grant)) {
                throw invalid("Unexpected or duplicate Product grant.");
            }
        }
        return STAGE_B.stream().filter(grant -> !existing.contains(grant)).toList();
    }

    private static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException(reason); }
}
