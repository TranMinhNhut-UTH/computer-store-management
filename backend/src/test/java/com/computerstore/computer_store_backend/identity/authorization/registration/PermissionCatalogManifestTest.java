package com.computerstore.computer_store_backend.identity.authorization.registration;

import com.computerstore.computer_store_backend.identity.authorization.AuthorizationService;
import com.computerstore.computer_store_backend.identity.authorization.CurrentActor;
import com.computerstore.computer_store_backend.identity.authorization.EmployeeRole;
import com.computerstore.computer_store_backend.identity.authorization.PermissionCode;
import com.computerstore.computer_store_backend.identity.authorization.PermissionPolicy;
import com.computerstore.computer_store_backend.identity.authorization.ResolvedAccess;
import com.computerstore.computer_store_backend.identity.authorization.RoleResolutionService;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.repository.RolePermissionRepository;
import com.computerstore.computer_store_backend.identity.repository.UserRoleRepository;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.api.migration.JavaMigration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionCatalogManifestTest {
    @Test void acceptsOnlyTheSuppliedSubsetWithoutFillingTheRestOfTheCatalog() {
        var entry = entry("PRODUCT_READ");
        var manifest = PermissionCatalogManifest.validate(List.of(entry));
        assertEquals(List.of(entry), manifest.entries());
        assertEquals(1, manifest.entries().size());
    }

    @Test void everyEligibleCodeCanBeValidatedIndividuallyWithoutBecomingAnApprovedSeed() {
        var eligible = Arrays.stream(PermissionCode.values())
                .filter(code -> code.status() == PermissionCode.Status.ELIGIBLE_PROPOSED).toList();
        assertEquals(51, eligible.size());
        for (var code : eligible) {
            assertEquals(List.of(entry(code.code())),
                    PermissionCatalogManifest.validate(List.of(entry(code.code()))).entries());
        }
    }

    @Test void rejectsDuplicateCodesEvenWhenTheirMetadataIsIdentical() {
        invalid(List.of(entry("PRODUCT_READ"), entry("PRODUCT_READ")));
    }

    @Test void rejectsDuplicateCodesWithConflictingMetadataInsteadOfOverwriting() {
        invalid(List.of(entry("PRODUCT_READ"), new PermissionCatalogManifest.Entry("PRODUCT_READ", "Other", "Other")));
    }

    @Test void rejectsNullOrEmptyManifestInsteadOfTreatingItAsAnApprovedNoOp() {
        invalid(null);
        invalid(List.of());
    }

    @Test void rejectsNullEntries() {
        invalid(Arrays.asList(entry("PRODUCT_READ"), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_READ", "PERMISSION_READ", "CUSTOMER_DEACTIVATE", "ADJUSTMENT_REJECT",
            "ADJUSTMENT_CANCEL", "SALE_COMPLETE", "PAYMENT_VOID", "REFUND_READ", "REFUND_CREATE"})
    void rejectsEveryProvisionalCodeEvenInsideAnOtherwiseEligibleManifest(String code) {
        invalid(List.of(entry("PRODUCT_READ"), entry(code)));
    }

    @Test void rejectsCanonicalUnknownPermissions() {
        invalid(List.of(entry("UNLISTED_PERMISSION")));
    }

    @Test void rejectsInvalidCodesWithoutTrimmingOrChangingCase() {
        for (String code : new String[]{null, "", " ", "*", "product_read", " PRODUCT_READ", "PRODUCT_READ ",
                "PRODUCT READ", "PRODUCT.READ", "PRODUCT-READ", "PR\u00d6DUCT_READ", "PRODUCT_READ\n"}) {
            invalid(List.of(entry(code)));
        }
    }

    @Test void rejectsOversizedAndBoundaryLengthUnknownCodes() {
        invalid(List.of(entry("X".repeat(101))));
        invalid(List.of(entry("X".repeat(100))));
    }

    @Test void enforcesRequiredNameAndVarchar120CharacterLimit() {
        for (String name : new String[]{null, "", " ", "\t\n", "n".repeat(121)}) {
            invalid(List.of(new PermissionCatalogManifest.Entry("PRODUCT_READ", name, null)));
        }
        var name = "\uD83D\uDE80".repeat(120);
        assertEquals(name, PermissionCatalogManifest.validate(List.of(
                new PermissionCatalogManifest.Entry("PRODUCT_READ", name, null))).entries().getFirst().name());
        invalid(List.of(new PermissionCatalogManifest.Entry("PRODUCT_READ", name + "x", null)));
    }

    @Test void acceptsNullDescriptionButEnforcesVarchar255CharacterLimit() {
        var description = "\uD83D\uDE80".repeat(255);
        assertEquals(description, PermissionCatalogManifest.validate(List.of(
                new PermissionCatalogManifest.Entry("PRODUCT_READ", "Products", description)))
                .entries().getFirst().description());
        invalid(List.of(new PermissionCatalogManifest.Entry("PRODUCT_READ", "Products", description + "x")));
        assertNull(PermissionCatalogManifest.validate(List.of(entry("PRODUCT_READ"))).entries().getFirst().description());
    }

    @Test void rejectsMalformedUnicodeRatherThanHashingReplacementCharacters() {
        invalid(List.of(new PermissionCatalogManifest.Entry("PRODUCT_READ", "\uD800", null)));
        invalid(List.of(new PermissionCatalogManifest.Entry("PRODUCT_READ", "Products", "\uDC00")));
    }

    @Test void preservesExactApprovedMetadataCandidatesWithoutNormalization() {
        var original = new PermissionCatalogManifest.Entry("PRODUCT_READ", " Products ", "A\r\nB\nC");
        assertEquals(original, PermissionCatalogManifest.validate(List.of(original)).entries().getFirst());
    }

    @Test void snapshotIsImmutableAndDoesNotSortOrRetainTheCallersMutableList() {
        var source = new ArrayList<>(List.of(entry("PRODUCT_READ"), entry("INVENTORY_READ")));
        var manifest = PermissionCatalogManifest.validate(source);
        assertEquals("PRODUCT_READ", source.getFirst().code());
        source.clear();
        assertEquals(List.of(entry("INVENTORY_READ"), entry("PRODUCT_READ")), manifest.entries());
        assertThrows(UnsupportedOperationException.class, () -> manifest.entries().clear());
    }

    @Test void contentFingerprintHasAnIndependentGoldenVectorAndIsOrderIndependent() {
        var product = new PermissionCatalogManifest.Entry("PRODUCT_READ", "Products", "Read products");
        var stock = new PermissionCatalogManifest.Entry("INVENTORY_READ", "Stock", null);
        var first = PermissionCatalogManifest.validate(List.of(product, stock));
        var second = PermissionCatalogManifest.validate(List.of(stock, product));
        assertEquals("43fc3b11f277c24af5ca2ec591e9ace9022958a0adbddaac6d4e97282cbbb5ae", first.contentSha256());
        assertEquals(first.contentSha256(), second.contentSha256());
    }

    @Test void fingerprintChangesWhenCodeNameDescriptionOrEntrySetChanges() {
        var base = PermissionCatalogManifest.validate(List.of(
                new PermissionCatalogManifest.Entry("PRODUCT_READ", "Label", "Description")));
        for (var changed : List.of(
                new PermissionCatalogManifest.Entry("INVENTORY_READ", "Label", "Description"),
                new PermissionCatalogManifest.Entry("PRODUCT_READ", "New label", "Description"),
                new PermissionCatalogManifest.Entry("PRODUCT_READ", "Label", "New description"))) {
            assertNotEquals(base.contentSha256(), PermissionCatalogManifest.validate(List.of(changed)).contentSha256());
        }
        assertNotEquals(base.contentSha256(), PermissionCatalogManifest.validate(List.of(
                base.entries().getFirst(), entry("INVENTORY_READ"))).contentSha256());
    }

    @Test void fingerprintDistinguishesNullEmptyAndFieldBoundaries() {
        var hashes = List.of(
                new PermissionCatalogManifest.Entry("PRODUCT_READ", "a", null),
                new PermissionCatalogManifest.Entry("PRODUCT_READ", "a", ""),
                new PermissionCatalogManifest.Entry("PRODUCT_READ", "a", "bc"),
                new PermissionCatalogManifest.Entry("PRODUCT_READ", "ab", "c"))
                .stream().map(e -> PermissionCatalogManifest.validate(List.of(e)).contentSha256()).toList();
        assertEquals(4, Set.copyOf(hashes).size());
    }

    @Test void validationDoesNotCreateRuntimeGrantsEvenForAdmin() {
        var resolver = mock(RoleResolutionService.class);
        when(resolver.resolveCurrent()).thenReturn(new ResolvedAccess(
                new CurrentActor(42L, "employee"), Set.of(EmployeeRole.ADMIN), Set.of()));
        var policy = new PermissionPolicy(new AuthorizationService(resolver));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(BusinessException.class,
                () -> policy.requirePermission("EMPLOYEE_CREATE")).code());
        PermissionCatalogManifest.validate(List.of(entry("EMPLOYEE_CREATE")));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(BusinessException.class,
                () -> policy.requirePermission("EMPLOYEE_CREATE")).code());
    }

    @Test void registrationPackageHasNoAutomaticMigrationAccountOrGrantEntrypoint() {
        assertFalse(JavaMigration.class.isAssignableFrom(PermissionCatalogManifest.class));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.scan(PermissionCatalogManifest.class.getPackageName());
            context.refresh();
            PermissionCatalogManifest.validate(List.of(entry("EMPLOYEE_CREATE")));
            assertTrue(context.getBeansOfType(PermissionCatalogManifest.class).isEmpty());
            assertTrue(context.getBeansOfType(JavaMigration.class).isEmpty());
            assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
            assertTrue(context.getBeansOfType(AppUserRepository.class).isEmpty());
            assertTrue(context.getBeansOfType(UserRoleRepository.class).isEmpty());
            assertTrue(context.getBeansOfType(RolePermissionRepository.class).isEmpty());
        }
    }

    @Test void invalidPayloadIsNotEchoedInValidationErrors() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> PermissionCatalogManifest.validate(List.of(entry("UNTRUSTED_SENTINEL"))));
        assertFalse(error.getMessage().contains("UNTRUSTED_SENTINEL"));
        assertNull(error.getCause());
    }

    private static PermissionCatalogManifest.Entry entry(String code) {
        return new PermissionCatalogManifest.Entry(code, "Proposed label", null);
    }

    private static void invalid(List<PermissionCatalogManifest.Entry> entries) {
        assertThrows(IllegalArgumentException.class, () -> PermissionCatalogManifest.validate(entries));
    }
}
