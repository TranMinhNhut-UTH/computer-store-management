package com.computerstore.computer_store_backend.identity.authorization;

import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleResolutionServiceTest {
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final EntityManager entities = mock(EntityManager.class);
    @SuppressWarnings("unchecked") private final TypedQuery<String> roles = mock(TypedQuery.class);
    @SuppressWarnings("unchecked") private final TypedQuery<String> permissions = mock(TypedQuery.class);
    private final CurrentActor actor = new CurrentActor(42L, "employee");
    private final RoleResolutionService resolver = new RoleResolutionService(actors, entities);

    @BeforeEach void setup() {
        when(actors.requireActor()).thenReturn(actor);
        when(entities.createQuery(RoleResolutionService.ROLES_QUERY, String.class)).thenReturn(roles);
        when(entities.createQuery(RoleResolutionService.PERMISSIONS_QUERY, String.class)).thenReturn(permissions);
        when(roles.setParameter("userId", 42L)).thenReturn(roles);
        when(roles.getResultList()).thenReturn(List.of("ADMIN"));
        when(permissions.setParameter("userId", 42L)).thenReturn(permissions);
        when(permissions.setParameter(eq("roleCodes"), any())).thenReturn(permissions);
        when(permissions.getResultList()).thenReturn(List.of());
    }

    @Test void usesOnlyAuthenticatedServerIdAndActualRoleCodes() {
        var current = resolver.resolveCurrent();
        assertSame(actor, current.actor()); assertEquals(Set.of(EmployeeRole.ADMIN), current.roles());
        assertTrue(current.permissionCodes().isEmpty());
        verify(roles).setParameter("userId", 42L); verify(permissions).setParameter("userId", 42L);
        var codes = ArgumentCaptor.forClass(List.class);
        verify(permissions).setParameter(eq("roleCodes"), codes.capture());
        assertEquals(List.of("ADMIN"), codes.getValue());
    }
    @Test void noRoleMeansNoPermissionsAndSkipsPermissionQuery() {
        when(roles.getResultList()).thenReturn(List.of());
        var current = resolver.resolveCurrent();
        assertTrue(current.roles().isEmpty()); assertTrue(current.permissionCodes().isEmpty());
        verify(entities, never()).createQuery(RoleResolutionService.PERMISSIONS_QUERY, String.class);
        verifyNoInteractions(permissions);
    }
    @Test void unknownOrNonCanonicalRolesGrantNothing() {
        when(roles.getResultList()).thenReturn(java.util.Arrays.asList(null, "admin", " ADMIN ", "UNAPPROVED_ROLE"));
        assertTrue(resolver.resolveCurrent().roles().isEmpty());
        verifyNoInteractions(permissions);
    }
    @Test void multipleRolesAreDeduplicatedAndMappedPermissionsFormAUnionWithConstantQueryCount() {
        when(roles.getResultList()).thenReturn(List.of("MANAGER", "SALES_STAFF", "MANAGER"));
        when(permissions.getResultList()).thenReturn(List.of("FIXTURE_A", "FIXTURE_B", "FIXTURE_A"));
        var current = resolver.resolveCurrent();
        assertEquals(Set.of(EmployeeRole.MANAGER, EmployeeRole.SALES_STAFF), current.roles());
        assertEquals(Set.of("FIXTURE_A", "FIXTURE_B"), current.permissionCodes());
        verify(entities).createQuery(RoleResolutionService.ROLES_QUERY, String.class);
        verify(entities).createQuery(RoleResolutionService.PERMISSIONS_QUERY, String.class);
        verifyNoMoreInteractions(entities);
    }
    @Test void malformedDatabasePermissionCodesAreNotNormalizedIntoGrants() {
        when(permissions.getResultList()).thenReturn(java.util.Arrays.asList(null, "", " ", "fixture", " FIXTURE ", "X".repeat(101), "FIXTURE_OK"));
        assertEquals(Set.of("FIXTURE_OK"), resolver.resolveCurrent().permissionCodes());
    }
    @Test void resolvesAgainOnEveryInvocationRatherThanUsingSessionOrLocalCache() {
        when(roles.getResultList()).thenReturn(List.of("ADMIN"), List.of("WAREHOUSE_STAFF"));
        when(permissions.getResultList()).thenReturn(List.of("FIXTURE_A"), List.of());
        assertEquals(Set.of("FIXTURE_A"), resolver.resolveCurrent().permissionCodes());
        var second = resolver.resolveCurrent();
        assertEquals(Set.of(EmployeeRole.WAREHOUSE_STAFF), second.roles()); assertTrue(second.permissionCodes().isEmpty());
        verify(actors, times(2)).requireActor(); verify(roles, times(2)).getResultList();
    }
    @Test void inactiveOrMissingActorStopsBeforeRoleQueries() {
        when(actors.requireActor()).thenThrow(new BusinessException(ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.detail()));
        assertThrows(BusinessException.class, resolver::resolveCurrent); verifyNoInteractions(entities);
    }
    @Test void projectionSnapshotIsImmutableAndDoesNotRetainMutableCollections() {
        var sourceRoles = new HashSet<>(Set.of(EmployeeRole.ADMIN));
        var sourcePermissions = new HashSet<>(Set.of("FIXTURE_A"));
        var current = new ResolvedAccess(actor, sourceRoles, sourcePermissions);
        sourceRoles.clear(); sourcePermissions.clear();
        assertEquals(Set.of(EmployeeRole.ADMIN), current.roles());
        assertThrows(UnsupportedOperationException.class, () -> current.roles().clear());
        assertThrows(UnsupportedOperationException.class, () -> current.permissionCodes().clear());
    }
    @Test void databaseFailureDoesNotReturnAnEmptyOrPrivilegedSuccessSnapshot() {
        when(roles.getResultList()).thenThrow(new IllegalStateException("DB unavailable"));
        assertThrows(IllegalStateException.class, resolver::resolveCurrent);
        verifyNoInteractions(permissions);
    }
}
