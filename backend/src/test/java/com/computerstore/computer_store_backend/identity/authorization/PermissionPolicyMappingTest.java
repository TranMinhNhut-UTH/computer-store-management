package com.computerstore.computer_store_backend.identity.authorization;

import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.security.EmployeePrincipal;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.error.GlobalExceptionHandler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real actor/resolver/authorization/policy chain with mock repositories and scalar queries, never a DB.
 * MVC fixtures exist only in tests, without filters; existing security/auth tests cover real filter/CSRF policy.
 */
class PermissionPolicyMappingTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AppUser user = mock(AppUser.class);
    private final EmployeePrincipal principal = mock(EmployeePrincipal.class);
    private final EntityManager entities = mock(EntityManager.class);
    @SuppressWarnings("unchecked") private final TypedQuery<String> roles = mock(TypedQuery.class);
    @SuppressWarnings("unchecked") private final TypedQuery<String> permissions = mock(TypedQuery.class);
    private final PermissionPolicy policy = new PermissionPolicy(
            new AuthorizationService(new RoleResolutionService(new CurrentActorService(users), entities)));
    private MockMvc mvc;

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        when(principal.getUserId()).thenReturn(42L);
        when(principal.getUsername()).thenReturn("employee");
        when(principal.isEnabled()).thenReturn(true);
        when(user.getUsername()).thenReturn("employee");
        when(user.getActive()).thenReturn(true);
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(entities.createQuery(RoleResolutionService.ROLES_QUERY, String.class)).thenReturn(roles);
        when(entities.createQuery(RoleResolutionService.PERMISSIONS_QUERY, String.class)).thenReturn(permissions);
        when(roles.setParameter("userId", 42L)).thenReturn(roles);
        when(roles.getResultList()).thenReturn(List.of("MANAGER"));
        when(permissions.setParameter("userId", 42L)).thenReturn(permissions);
        when(permissions.setParameter(eq("roleCodes"), any())).thenReturn(permissions);
        when(permissions.getResultList()).thenReturn(List.of("PRODUCT_READ"));
        mvc = MockMvcBuilders.standaloneSetup(new Fixture(policy))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        signIn();
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void sameAuthenticationUsesCurrentAccountAndGrants() throws Exception {
        var context = SecurityContextHolder.getContext();
        var original = context.getAuthentication();
        mvc.perform(get("/fixture/policy/product")).andExpect(status().isOk());

        when(user.getActive()).thenReturn(false);
        mvc.perform(get("/fixture/policy/product")).andExpect(status().isUnauthorized());
        assertSame(original, context.getAuthentication());

        when(user.getActive()).thenReturn(true);
        mvc.perform(get("/fixture/policy/product")).andExpect(status().isOk());
        when(roles.getResultList()).thenReturn(List.of("SALES_STAFF"));
        when(permissions.getResultList()).thenReturn(List.of("CUSTOMER_READ"));
        mvc.perform(get("/fixture/policy/product")).andExpect(status().isForbidden());
        when(permissions.getResultList()).thenReturn(List.of("PRODUCT_READ"));
        mvc.perform(get("/fixture/policy/product")).andExpect(status().isOk());
        assertSame(original, context.getAuthentication());
    }

    @Test void eligibleAccessUsesTheServerActorAndExistingScalarMappingQueries() {
        assertEquals(new CurrentActor(42L, "employee"), policy.requirePermission(PermissionCode.PRODUCT_READ.code()));
        verify(users).findById(42L);
        verify(roles).setParameter("userId", 42L);
        verify(permissions).setParameter("userId", 42L);
        verify(permissions).setParameter("roleCodes", List.of("MANAGER"));
        verify(entities).createQuery(RoleResolutionService.ROLES_QUERY, String.class);
        verify(entities).createQuery(RoleResolutionService.PERMISSIONS_QUERY, String.class);
        verifyNoMoreInteractions(entities);
        verify(user, never()).getPasswordHash();
    }

    @Test void adminInTheDatabaseWithoutAnExplicitPermissionMappingDoesNotGrantAccess() {
        when(roles.getResultList()).thenReturn(List.of("ADMIN"));
        when(permissions.getResultList()).thenReturn(List.of());
        denied(() -> policy.requirePermission(PermissionCode.EMPLOYEE_CREATE.code()));
        verify(permissions).getResultList();
    }

    @Test void clientDetailsAndGrantedAuthoritiesDoNotReplaceServerMapping() {
        when(permissions.getResultList()).thenReturn(List.of());
        var token = UsernamePasswordAuthenticationToken.authenticated(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("PRODUCT_READ")));
        token.setDetails(Map.of("actorId", 999L, "roles", List.of("ADMIN"), "permission", "PRODUCT_READ"));
        SecurityContextHolder.getContext().setAuthentication(token);
        denied(() -> policy.requirePermission(PermissionCode.PRODUCT_READ.code()));
        verify(users).findById(42L);
        verify(users, never()).findById(999L);
    }

    @Test void unknownServerRoleDoesNotResolveAnyPermissions() {
        when(roles.getResultList()).thenReturn(List.of("UNAPPROVED_ROLE"));
        denied(() -> policy.requirePermission(PermissionCode.PRODUCT_READ.code()));
        verify(entities, never()).createQuery(RoleResolutionService.PERMISSIONS_QUERY, String.class);
        verifyNoInteractions(permissions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_READ", "PERMISSION_READ", "CUSTOMER_DEACTIVATE", "ADJUSTMENT_REJECT",
            "ADJUSTMENT_CANCEL", "SALE_COMPLETE", "PAYMENT_VOID", "REFUND_READ", "REFUND_CREATE"})
    void eachMistakenProvisionalDatabaseMappingRemainsDenied(String code) {
        when(roles.getResultList()).thenReturn(List.of("ADMIN"));
        when(permissions.getResultList()).thenReturn(List.of(code));
        denied(() -> policy.requirePermission(code));
        verify(permissions).getResultList();
    }

    @Test void inactiveEmployeeReturns401BeforeAnyRoleOrPermissionQuery() {
        when(user.getActive()).thenReturn(false);
        var error = assertThrows(BusinessException.class,
                () -> policy.requirePermission(PermissionCode.REFUND_CREATE.code()));
        assertEquals(ErrorCode.UNAUTHENTICATED, error.code());
        verifyNoInteractions(entities);
    }

    @Test void changedDatabaseMappingIsRecheckedOnTheNextCall() {
        when(permissions.getResultList()).thenReturn(List.of("PRODUCT_READ"), List.of());
        assertEquals(42L, policy.requirePermission(PermissionCode.PRODUCT_READ.code()).userId());
        denied(() -> policy.requirePermission(PermissionCode.PRODUCT_READ.code()));
        verify(users, times(2)).findById(42L);
        verify(permissions, times(2)).getResultList();
    }

    @Test void anonymousRequestsKeepSafe401ForEligibleProvisionalAndUnknownPolicies() throws Exception {
        SecurityContextHolder.clearContext();
        for (String path : List.of("/fixture/policy/product", "/fixture/policy/refund", "/fixture/policy/unknown")) {
            mvc.perform(get(path).queryParam("permission", "CLIENT_SENTINEL"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.detail").value("Authentication is required."))
                    .andExpect(jsonPath("$.instance").value(path))
                    .andExpect(content().string(not(containsString("CLIENT_SENTINEL"))));
        }
        verifyNoInteractions(users, entities);
    }

    @Test void unmappedProvisionalAndUnknownPoliciesAllKeepTheSameSafe403Contract() throws Exception {
        when(roles.getResultList()).thenReturn(List.of("ADMIN"));
        for (String route : List.of("product", "refund", "unknown")) {
            when(permissions.getResultList()).thenReturn(List.of("REFUND_CREATE", "UNLISTED_PERMISSION"));
            mvc.perform(get("/fixture/policy/" + route).queryParam("permission", "CLIENT_SENTINEL"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                    .andExpect(jsonPath("$.detail").value("Access is denied."))
                    .andExpect(content().string(not(containsString("REFUND_CREATE"))))
                    .andExpect(content().string(not(containsString("UNLISTED_PERMISSION"))))
                    .andExpect(content().string(not(containsString("CLIENT_SENTINEL"))));
        }
    }

    @Test void testControllerSelectsTheRequiredPermissionOnTheServerInsteadOfTakingItFromClient() throws Exception {
        mvc.perform(get("/fixture/policy/product").param("permission", "REFUND_CREATE").header("X-Actor-ID", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.username").value("employee"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());
    }

    @Test void persistenceFailureStaysASanitized500RatherThanAllowingOrClaiming403() throws Exception {
        when(users.findById(42L)).thenThrow(new IllegalStateException("INTERNAL_SENTINEL"));
        mvc.perform(get("/fixture/policy/product"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("INTERNAL_SENTINEL"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    private void signIn() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of()));
    }

    private void denied(Runnable action) {
        var error = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.ACCESS_DENIED, error.code());
        assertEquals("Access is denied.", error.publicDetail());
        assertNull(error.getCause());
    }

    @RestController static class Fixture {
        private final PermissionPolicy policy;
        Fixture(PermissionPolicy policy) { this.policy = policy; }
        @GetMapping("/fixture/policy/product") CurrentActor product() {
            return policy.requirePermission(PermissionCode.PRODUCT_READ.code());
        }
        @GetMapping("/fixture/policy/refund") CurrentActor refund() {
            return policy.requirePermission(PermissionCode.REFUND_CREATE.code());
        }
        @GetMapping("/fixture/policy/unknown") CurrentActor unknown() {
            return policy.requirePermission("UNLISTED_PERMISSION");
        }
    }
}
