package com.computerstore.computer_store_backend.identity.authorization;

import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Part 3A mapping guard; snapshots supplied here are isolated test fixtures only. */
class PermissionPolicyTest {
    private final RoleResolutionService resolver = mock(RoleResolutionService.class);
    private final PermissionPolicy policy = new PermissionPolicy(new AuthorizationService(resolver));
    private final CurrentActor actor = new CurrentActor(42L, "employee");

    @ParameterizedTest @EnumSource(PermissionCode.class)
    void mappedCodesStillRequireCatalogEligibility(PermissionCode permission) {
        access(Set.of(EmployeeRole.ADMIN), Set.of(permission.code()));
        if (permission.status() == PermissionCode.Status.PROVISIONAL_DENY) {
            denied(() -> policy.requirePermission(permission.code()));
        } else {
            assertSame(actor, policy.requirePermission(permission.code()));
        }
        verify(resolver).resolveCurrent();
    }

    @ParameterizedTest @EnumSource(EmployeeRole.class)
    void noRoleHasDefaultGrantsNotEvenAdmin(EmployeeRole role) {
        access(Set.of(role), Set.of());
        for (var permission : PermissionCode.values()) {
            denied(() -> policy.requirePermission(permission.code()));
        }
    }

    @Test void eligibilityAloneCannotReplaceTheExistingMappingCheck() {
        access(Set.of(EmployeeRole.MANAGER), Set.of(PermissionCode.PRODUCT_READ.code()));
        assertSame(actor, policy.requirePermission(PermissionCode.PRODUCT_READ.code()));
        denied(() -> policy.requirePermission(PermissionCode.PRODUCT_PRICE_UPDATE.code()));
        verify(resolver, times(2)).resolveCurrent();
    }

    @Test void evenCanonicalMappedCodesOutsideTheCatalogAreDenied() {
        access(Set.of(EmployeeRole.ADMIN), Set.of("UNLISTED_PERMISSION", "FIXTURE_MAPPED"));
        denied(() -> policy.requirePermission("UNLISTED_PERMISSION"));
        denied(() -> policy.requirePermission("FIXTURE_MAPPED"));
    }

    @Test void nullMalformedAndWildcardRequirementsNeverGrantAccess() {
        access(Set.of(EmployeeRole.ADMIN), Set.of(PermissionCode.PRODUCT_READ.code()));
        for (String required : new String[]{null, "", " ", "*", "product_read", " PRODUCT_READ", "PRODUCT_READ ",
                "X".repeat(101)}) {
            denied(() -> policy.requirePermission(required));
        }
    }

    @Test void anonymousIs401ForEligibleProvisionalUnknownAndNullRequirements() {
        when(resolver.resolveCurrent()).thenThrow(new BusinessException(
                ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.detail()));
        for (var permission : PermissionCode.values()) unauthenticated(permission.code());
        unauthenticated("UNLISTED_PERMISSION");
        unauthenticated(null);
    }

    @Test void eachInvocationRechecksServerMappingInsteadOfCachingAPreviousGrant() {
        when(resolver.resolveCurrent()).thenReturn(
                new ResolvedAccess(actor, Set.of(EmployeeRole.MANAGER), Set.of("PRODUCT_READ")),
                new ResolvedAccess(actor, Set.of(EmployeeRole.MANAGER), Set.of()));
        assertSame(actor, policy.requirePermission(PermissionCode.PRODUCT_READ.code()));
        denied(() -> policy.requirePermission(PermissionCode.PRODUCT_READ.code()));
        verify(resolver, times(2)).resolveCurrent();
    }

    @Test void infrastructureFailureDoesNotFallBackToCatalogOrAdminGrants() {
        var unavailable = new IllegalStateException("fixture infrastructure failure");
        when(resolver.resolveCurrent()).thenThrow(unavailable);
        assertSame(unavailable, assertThrows(IllegalStateException.class,
                () -> policy.requirePermission(PermissionCode.PRODUCT_READ.code())));
    }

    private void access(Set<EmployeeRole> roles, Set<String> permissions) {
        when(resolver.resolveCurrent()).thenReturn(new ResolvedAccess(actor, roles, permissions));
    }

    private void unauthenticated(String required) {
        var error = assertThrows(BusinessException.class, () -> policy.requirePermission(required));
        assertEquals(ErrorCode.UNAUTHENTICATED, error.code());
        assertEquals("Authentication is required.", error.publicDetail());
        assertNull(error.getCause());
    }

    private void denied(Runnable action) {
        var error = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.ACCESS_DENIED, error.code());
        assertEquals("Access is denied.", error.publicDetail());
        assertNull(error.getCause());
    }
}
