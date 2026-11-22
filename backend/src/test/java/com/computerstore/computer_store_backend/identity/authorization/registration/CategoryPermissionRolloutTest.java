package com.computerstore.computer_store_backend.identity.authorization.registration;

import com.computerstore.computer_store_backend.identity.authorization.*;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.api.migration.JavaMigration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryPermissionRolloutTest {
    @Test void stageAIsExactlyFourCategoryCodesWithExplicitMetadataAndStableFingerprint() {
        var manifest = CategoryPermissionRollout.stageA();
        assertEquals(List.of("CATEGORY_CREATE", "CATEGORY_DEACTIVATE", "CATEGORY_READ", "CATEGORY_UPDATE"),
                manifest.entries().stream().map(PermissionCatalogManifest.Entry::code).toList());
        for (var entry : manifest.entries()) {
            assertEquals(entry.code(), entry.name());
            assertNull(entry.description());
        }
        // Independent length-prefixed SHA-256 golden vector, not a Flyway checksum or DB approval.
        assertEquals("3c99e12abed2c665096bf3489d75d97754c9002ee7f9f8389aa96ad3aa331bf6", manifest.contentSha256());
        assertThrows(UnsupportedOperationException.class, () -> manifest.entries().clear());
    }

    @Test void stageBIsExactlySixDistinctPairsAndNeverAdminOrOtherModules() {
        var pairs = CategoryPermissionRollout.stageB().stream()
                .map(grant -> grant.role().name() + ":" + grant.permission().code()).toList();
        assertEquals(6, pairs.size());
        assertEquals(Set.of("MANAGER:CATEGORY_READ", "MANAGER:CATEGORY_CREATE", "MANAGER:CATEGORY_UPDATE",
                "MANAGER:CATEGORY_DEACTIVATE", "SALES_STAFF:CATEGORY_READ", "WAREHOUSE_STAFF:CATEGORY_READ"), Set.copyOf(pairs));
        assertThrows(UnsupportedOperationException.class, () -> CategoryPermissionRollout.stageB().clear());
    }

    @ParameterizedTest @EnumSource(value = PermissionCode.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"CATEGORY_READ", "CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DEACTIVATE"})
    void everyOtherCatalogPermissionIncludingAllNineProvisionalIsOutsideThisRollout(PermissionCode code) {
        assertThrows(IllegalArgumentException.class,
                () -> new CategoryPermissionRollout.Grant(EmployeeRole.MANAGER, code));
    }

    @Test void noAdminGrantOrStaffMutationCanBeAddedToThisSlice() {
        for (var permission : List.of(PermissionCode.CATEGORY_READ, PermissionCode.CATEGORY_CREATE,
                PermissionCode.CATEGORY_UPDATE, PermissionCode.CATEGORY_DEACTIVATE)) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CategoryPermissionRollout.Grant(EmployeeRole.ADMIN, permission));
            if (permission != PermissionCode.CATEGORY_READ) {
                for (var role : List.of(EmployeeRole.SALES_STAFF, EmployeeRole.WAREHOUSE_STAFF)) {
                    assertThrows(IllegalArgumentException.class, () -> new CategoryPermissionRollout.Grant(role, permission));
                }
            }
        }
    }

    @Test void preparedManifestsDoNotProduceRuntimePermissions() {
        var resolver = mock(RoleResolutionService.class);
        when(resolver.resolveCurrent()).thenReturn(new ResolvedAccess(
                new CurrentActor(42L, "employee"), Set.of(EmployeeRole.MANAGER), Set.of()));
        var policy = new PermissionPolicy(new AuthorizationService(resolver));
        CategoryPermissionRollout.stageA();
        CategoryPermissionRollout.stageB();
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(BusinessException.class,
                () -> policy.requirePermission("CATEGORY_READ")).code());
    }

    @Test void preparationHasNoStartupDatabaseOrExecutableMigrationBean() {
        assertFalse(JavaMigration.class.isAssignableFrom(CategoryPermissionRollout.class));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.scan(CategoryPermissionRollout.class.getPackageName());
            context.refresh();
            assertTrue(context.getBeansOfType(CategoryPermissionRollout.class).isEmpty());
            assertTrue(context.getBeansOfType(JavaMigration.class).isEmpty());
            assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
            assertFalse(context.containsBean("flyway"));
        }
    }
}
