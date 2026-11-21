package com.computerstore.computer_store_backend.identity.authorization;

import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.security.EmployeePrincipal;
import com.computerstore.computer_store_backend.shared.error.GlobalExceptionHandler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Test-only controller and standalone MVC: error/actor boundary only. Real filter/CSRF policy has separate regression tests. */
class AuthorizationHttpTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AppUser user = mock(AppUser.class);
    private final EmployeePrincipal principal = mock(EmployeePrincipal.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        when(principal.getUserId()).thenReturn(42L); when(principal.getUsername()).thenReturn("employee");
        when(principal.isEnabled()).thenReturn(true); when(user.getActive()).thenReturn(true);
        when(user.getUsername()).thenReturn("employee"); when(users.findById(42L)).thenReturn(Optional.of(user));
        var entities = mock(EntityManager.class);
        @SuppressWarnings("unchecked") TypedQuery<String> roles = mock(TypedQuery.class);
        when(entities.createQuery(RoleResolutionService.ROLES_QUERY, String.class)).thenReturn(roles);
        when(roles.setParameter("userId", 42L)).thenReturn(roles); when(roles.getResultList()).thenReturn(List.of());
        var actors = new CurrentActorService(users);
        var guard = new AuthorizationService(new RoleResolutionService(actors, entities));
        mvc = MockMvcBuilders.standaloneSetup(new Fixture(actors, guard))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void anonymousActorHasSafe401ProblemDetail() throws Exception {
        mvc.perform(get("/fixture/actor").queryParam("actorId", "999").queryParam("secret", "INTERNAL_SENTINEL"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Authentication is required."))
                .andExpect(jsonPath("$.instance").value("/fixture/actor"))
                .andExpect(content().string(not(containsString("INTERNAL_SENTINEL"))));
        verifyNoInteractions(users);
    }
    @Test void bodyQueryAndHeadersCannotOverrideTheCurrentActor() throws Exception {
        signIn();
        mvc.perform(post("/fixture/actor").queryParam("actorId", "999").header("X-Actor-ID", "999")
                        .header("X-Roles", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorId\":999,\"username\":\"forged\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.username").value("employee"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.roles").doesNotExist());
        verify(users).findById(42L); verify(users, never()).findById(999L);
    }
    @Test void unmappedPermissionHasSafe403AndNeverDisclosesTheRequiredCode() throws Exception {
        signIn();
        mvc.perform(get("/fixture/guarded").queryParam("permission", "FAKE_CLIENT_PERMISSION"))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.detail").value("Access is denied."))
                .andExpect(jsonPath("$.instance").value("/fixture/guarded"))
                .andExpect(content().string(not(containsString("FIXTURE_PERMISSION"))))
                .andExpect(content().string(not(containsString("FAKE_CLIENT_PERMISSION"))));
    }
    @Test void inactiveAccountIs401BeforePermissionCheck() throws Exception {
        signIn(); when(user.getActive()).thenReturn(false);
        mvc.perform(get("/fixture/guarded")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
    @Test void infrastructureFailureIsSanitized500RatherThanAnAllowOrMisleading403() throws Exception {
        signIn(); when(users.findById(42L)).thenThrow(new IllegalStateException("SQL INTERNAL_SENTINEL"));
        mvc.perform(get("/fixture/guarded")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("INTERNAL_SENTINEL"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    private void signIn() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
    @RestController static class Fixture {
        private final CurrentActorService actors;
        private final AuthorizationService guard;
        Fixture(CurrentActorService actors, AuthorizationService guard) { this.actors = actors; this.guard = guard; }
        @GetMapping("/fixture/actor") CurrentActor actor() { return actors.requireActor(); }
        @PostMapping("/fixture/actor") CurrentActor writeActor() { return actors.requireActor(); }
        @GetMapping("/fixture/guarded") CurrentActor guarded() { return guard.requirePermission("FIXTURE_PERMISSION"); }
    }
}
