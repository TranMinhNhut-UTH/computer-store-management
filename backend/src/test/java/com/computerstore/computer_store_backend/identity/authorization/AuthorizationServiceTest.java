package com.computerstore.computer_store_backend.identity.authorization;

import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Permission codes here are test fixtures only, not a production permission catalog. */
class AuthorizationServiceTest {
    private final RoleResolutionService resolver = mock(RoleResolutionService.class);
    private final AuthorizationService guard = new AuthorizationService(resolver);
    private final CurrentActor actor = new CurrentActor(42L, "employee");

    @Test void matchingServerRoleReturnsTheTrustedActor() {
        access(Set.of(EmployeeRole.MANAGER), Set.of());
        assertSame(actor, guard.requireAnyRole(EmployeeRole.MANAGER));
    }
    @Test void multipleAllowedRolesUseAnyMatchWithoutChangingTheUsersRoles() {
        access(Set.of(EmployeeRole.SALES_STAFF, EmployeeRole.WAREHOUSE_STAFF), Set.of());
        assertSame(actor, guard.requireAnyRole(EmployeeRole.MANAGER, EmployeeRole.WAREHOUSE_STAFF));
    }
    @Test void missingRoleOrEmptyRequirementsDenyByDefault() {
        access(Set.of(), Set.of()); denied(() -> guard.requireAnyRole(EmployeeRole.ADMIN));
        access(Set.of(EmployeeRole.ADMIN), Set.of());
        denied(() -> guard.requireAnyRole()); denied(() -> guard.requireAnyRole((EmployeeRole[]) null));
        denied(() -> guard.requireAnyRole(EmployeeRole.ADMIN, null));
    }
    @Test void adminDoesNotImplyAnotherRoleOrAnyPermission() {
        access(Set.of(EmployeeRole.ADMIN), Set.of());
        denied(() -> guard.requireAnyRole(EmployeeRole.MANAGER));
        denied(() -> guard.requirePermission("FIXTURE_UNMAPPED"));
    }
    @Test void onlyActuallyMappedCanonicalPermissionReturnsActor() {
        access(Set.of(EmployeeRole.MANAGER), Set.of("FIXTURE_MAPPED"));
        assertSame(actor, guard.requirePermission("FIXTURE_MAPPED"));
        denied(() -> guard.requirePermission("FIXTURE_UNKNOWN"));
    }
    @Test void missingMalformedAndWildcardPermissionRequirementsNeverGrantAccess() {
        access(Set.of(EmployeeRole.ADMIN), Set.of("FIXTURE_MAPPED"));
        for (String required : new String[]{null, "", " ", "*", "fixture_mapped", " FIXTURE_MAPPED", "X".repeat(101)}) {
            denied(() -> guard.requirePermission(required));
        }
    }
    @Test void unauthenticatedCallerRetains401InsteadOfBecomingAnAuthorization403() {
        when(resolver.resolveCurrent()).thenThrow(new BusinessException(ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.detail()));
        assertEquals(ErrorCode.UNAUTHENTICATED, assertThrows(BusinessException.class,
                () -> guard.requireAnyRole(EmployeeRole.ADMIN)).code());
        assertEquals(ErrorCode.UNAUTHENTICATED, assertThrows(BusinessException.class,
                () -> guard.requirePermission("FIXTURE_MAPPED")).code());
    }

    private void access(Set<EmployeeRole> roles, Set<String> permissions) {
        when(resolver.resolveCurrent()).thenReturn(new ResolvedAccess(actor, roles, permissions));
    }
    private void denied(Runnable action) {
        var error = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.ACCESS_DENIED, error.code()); assertEquals("Access is denied.", error.publicDetail());
        assertNull(error.getCause());
    }
}
