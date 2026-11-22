package com.computerstore.computer_store_backend.identity.authorization.registration;

import com.computerstore.computer_store_backend.identity.authorization.PermissionCode;
import com.computerstore.computer_store_backend.identity.authorization.registration.PermissionCatalogManifest.Entry;
import com.computerstore.computer_store_backend.identity.authorization.registration.ProductPermissionRollout.Grant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.computerstore.computer_store_backend.identity.authorization.EmployeeRole.*;
import static com.computerstore.computer_store_backend.identity.authorization.PermissionCode.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductPermissionRolloutTest {
    private static final List<String> ROLES = List.of("ADMIN", "MANAGER", "SALES_STAFF", "WAREHOUSE_STAFF");
    private final List<Entry> catalog = ProductPermissionRollout.approvedRegistration().entries();
    private final List<Grant> grants = ProductPermissionRollout.approvedGrants();

    @Test void registrationPayloadAndFingerprintAreFrozen() {
        assertEquals(List.of("PRODUCT_CREATE", "PRODUCT_DEACTIVATE", "PRODUCT_PRICE_UPDATE",
                "PRODUCT_READ", "PRODUCT_UPDATE"), catalog.stream().map(Entry::code).toList());
        assertTrue(catalog.stream().allMatch(entry -> entry.code().equals(entry.name()) && entry.description() == null));
        assertEquals("f7f1eb15a362171740a64ac2ff1fe9b7732c0f13b6d8d2f132d109b05873b6af",
                ProductPermissionRollout.approvedRegistration().contentSha256());
        for (Entry entry : catalog) {
            var code = PermissionCode.fromCode(entry.code()).orElseThrow();
            assertEquals(PermissionCode.Module.PRODUCT, code.module());
            assertEquals(PermissionCode.Status.ELIGIBLE_PROPOSED, code.status());
        }
        assertThrows(UnsupportedOperationException.class, () -> catalog.removeFirst());
        assertThrows(UnsupportedOperationException.class, () -> grants.clear());
    }

    @Test void exactApprovedGrantsExcludeWarehouseWritesAndAdmin() {
        assertEquals(7, grants.size());
        assertEquals(Set.of(new Grant(MANAGER, PRODUCT_READ), new Grant(MANAGER, PRODUCT_CREATE),
                new Grant(MANAGER, PRODUCT_UPDATE), new Grant(MANAGER, PRODUCT_PRICE_UPDATE),
                new Grant(MANAGER, PRODUCT_DEACTIVATE), new Grant(SALES_STAFF, PRODUCT_READ),
                new Grant(WAREHOUSE_STAFF, PRODUCT_READ)), Set.copyOf(grants));
        assertThrows(IllegalArgumentException.class, () -> new Grant(WAREHOUSE_STAFF, PRODUCT_CREATE));
        assertThrows(IllegalArgumentException.class, () -> new Grant(WAREHOUSE_STAFF, PRODUCT_UPDATE));
        assertThrows(IllegalArgumentException.class, () -> new Grant(SALES_STAFF, PRODUCT_PRICE_UPDATE));
        assertThrows(IllegalArgumentException.class, () -> new Grant(ADMIN, PRODUCT_READ));
        assertThrows(IllegalArgumentException.class, () -> new Grant(MANAGER, CATEGORY_READ));
        assertThrows(IllegalArgumentException.class, () -> new Grant(null, PRODUCT_READ));
        assertThrows(IllegalArgumentException.class, () -> new Grant(MANAGER, null));
    }

    @Test void matchingSnapshotsCanBeAdoptedWithoutDuplicateInserts() {
        assertEquals(catalog, ProductPermissionRollout.missingRegistration(List.of()));
        assertEquals(catalog.subList(1, catalog.size()), ProductPermissionRollout.missingRegistration(catalog.subList(0, 1)));
        assertEquals(List.of(), ProductPermissionRollout.missingRegistration(catalog));
        assertEquals(grants, ProductPermissionRollout.missingGrants(ROLES, catalog, List.of()));
        assertEquals(grants.subList(1, grants.size()), ProductPermissionRollout.missingGrants(ROLES, catalog, grants.subList(0, 1)));
        assertEquals(List.of(), ProductPermissionRollout.missingGrants(ROLES, catalog, grants));
    }

    @Test void conflictsOrUnknownPermissionCodesRejectTheWholePlan() {
        var row = catalog.getFirst();
        for (var entries : List.of(List.of(row, row),
                List.of(new Entry(row.code(), "wrong", null)),
                List.of(new Entry(row.code(), row.name(), "")),
                List.of(new Entry("PRODUCT_UNKNOWN", "PRODUCT_UNKNOWN", null)),
                List.of(new Entry("CATEGORY_READ", "CATEGORY_READ", null)))) {
            assertThrows(IllegalArgumentException.class, () -> ProductPermissionRollout.missingRegistration(entries));
        }
    }

    @Test void unknownMissingOrDuplicateRolesAndDuplicateGrantsRejectTheWholePlan() {
        for (var roles : List.of(List.of("ADMIN", "MANAGER", "SALES_STAFF", "WAREHOUSE_STAFF", "OTHER"),
                List.of("ADMIN", "MANAGER", "SALES_STAFF"),
                List.of("ADMIN", "MANAGER", "SALES_STAFF", "WAREHOUSE_STAFF", "ADMIN"))) {
            assertThrows(IllegalArgumentException.class, () -> ProductPermissionRollout.missingGrants(roles, catalog, List.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> ProductPermissionRollout.missingGrants(ROLES, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ProductPermissionRollout.missingGrants(ROLES, catalog, List.of(grants.getFirst(), grants.getFirst())));
    }
}
