package com.computerstore.computer_store_backend.identity.authorization;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PermissionCodeTest {
    // Independent contract expectations, not a role mapping or a production seed.
    private static final Set<String> EXPECTED_CODES = Set.of("""
            EMPLOYEE_READ EMPLOYEE_CREATE EMPLOYEE_UPDATE EMPLOYEE_LOCK EMPLOYEE_UNLOCK EMPLOYEE_ROLE_ASSIGN
            ROLE_READ PERMISSION_READ
            CATEGORY_READ CATEGORY_CREATE CATEGORY_UPDATE CATEGORY_DEACTIVATE
            PRODUCT_READ PRODUCT_CREATE PRODUCT_UPDATE PRODUCT_PRICE_UPDATE PRODUCT_DEACTIVATE
            SUPPLIER_READ SUPPLIER_CREATE SUPPLIER_UPDATE SUPPLIER_DEACTIVATE
            CUSTOMER_READ CUSTOMER_CREATE CUSTOMER_UPDATE CUSTOMER_DEACTIVATE
            PURCHASE_READ PURCHASE_CREATE PURCHASE_UPDATE PURCHASE_CONFIRM PURCHASE_CANCEL
            INVENTORY_READ INVENTORY_LEDGER_READ STOCKTAKE_READ STOCKTAKE_START STOCKTAKE_COUNT
            ADJUSTMENT_READ ADJUSTMENT_PROPOSE ADJUSTMENT_APPROVE ADJUSTMENT_REJECT ADJUSTMENT_CANCEL
            SALE_READ SALE_CREATE SALE_UPDATE SALE_CONFIRM SALE_COMPLETE SALE_CANCEL
            PAYMENT_READ PAYMENT_CREATE PAYMENT_VOID
            RETURN_READ RETURN_CREATE RETURN_UPDATE RETURN_CONFIRM RETURN_CANCEL
            REFUND_READ REFUND_CREATE
            REPORT_REVENUE_READ REPORT_SALES_READ REPORT_INVENTORY_READ REPORT_LOW_STOCK_READ
            """.strip().split("\\s+"));
    private static final Set<String> HELD_CODES = Set.of(
            "ROLE_READ", "PERMISSION_READ", "CUSTOMER_DEACTIVATE", "ADJUSTMENT_REJECT", "ADJUSTMENT_CANCEL",
            "SALE_COMPLETE", "PAYMENT_VOID", "REFUND_READ", "REFUND_CREATE");

    @Test void containsExactlyTheSixtyContractCodesWithoutDuplicatesOrAdditions() {
        var codes = Arrays.stream(PermissionCode.values()).map(PermissionCode::code).toList();
        assertEquals(60, codes.size());
        assertEquals(60, Set.copyOf(codes).size());
        assertEquals(EXPECTED_CODES, Set.copyOf(codes));
    }

    @Test void exactlyNineCodesAreProvisionalDenyAndFiftyOneAreOnlyEligibleProposed() {
        var held = Arrays.stream(PermissionCode.values())
                .filter(code -> code.status() == PermissionCode.Status.PROVISIONAL_DENY)
                .map(PermissionCode::code).collect(Collectors.toSet());
        assertEquals(HELD_CODES, held);
        assertEquals(51, Arrays.stream(PermissionCode.values())
                .filter(code -> code.status() == PermissionCode.Status.ELIGIBLE_PROPOSED).count());
        assertEquals(Set.of(PermissionCode.Status.ELIGIBLE_PROPOSED, PermissionCode.Status.PROVISIONAL_DENY),
                Set.of(PermissionCode.Status.values()));
    }

    @Test void everyCodeMatchesExistingCanonicalDatabaseIdentifiersAndRoundTripsExactly() {
        for (var permission : PermissionCode.values()) {
            assertTrue(permission.code().matches("[A-Z][A-Z0-9_]{0,99}"));
            assertTrue(RoleResolutionService.isCanonicalPermissionCode(permission.code()));
            assertSame(permission, PermissionCode.fromCode(permission.code()).orElseThrow());
        }
    }

    @Test void unknownNullNonCanonicalAndWildcardCodesAreNotNormalizedOrInvented() {
        for (String code : new String[]{null, "", " ", "*", "product_read", " PRODUCT_READ", "PRODUCT_READ ",
                "UNLISTED_PERMISSION", "PRODUCT.DELETE", "X".repeat(101)}) {
            assertTrue(PermissionCode.fromCode(code).isEmpty());
        }
    }

    @Test void catalogIsGroupedIntoTheSixteenContractModules() {
        var counts = Arrays.stream(PermissionCode.values()).collect(Collectors.groupingBy(
                PermissionCode::module, Collectors.counting()));
        assertEquals(Map.ofEntries(
                Map.entry(PermissionCode.Module.IDENTITY_EMPLOYEE, 6L),
                Map.entry(PermissionCode.Module.ROLE_PERMISSION, 2L),
                Map.entry(PermissionCode.Module.CATEGORY, 4L),
                Map.entry(PermissionCode.Module.PRODUCT, 5L),
                Map.entry(PermissionCode.Module.SUPPLIER, 4L),
                Map.entry(PermissionCode.Module.CUSTOMER, 4L),
                Map.entry(PermissionCode.Module.PURCHASING, 5L),
                Map.entry(PermissionCode.Module.INVENTORY, 1L),
                Map.entry(PermissionCode.Module.INVENTORY_LEDGER, 1L),
                Map.entry(PermissionCode.Module.STOCKTAKE, 3L),
                Map.entry(PermissionCode.Module.INVENTORY_ADJUSTMENT, 5L),
                Map.entry(PermissionCode.Module.SALES, 6L),
                Map.entry(PermissionCode.Module.PAYMENT, 3L),
                Map.entry(PermissionCode.Module.RETURN, 5L),
                Map.entry(PermissionCode.Module.REFUND, 2L),
                Map.entry(PermissionCode.Module.REPORTS, 4L)), counts);
    }
}
