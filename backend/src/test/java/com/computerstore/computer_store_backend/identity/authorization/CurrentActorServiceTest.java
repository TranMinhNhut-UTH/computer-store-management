package com.computerstore.computer_store_backend.identity.authorization;

import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.security.EmployeePrincipal;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentActorServiceTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final EmployeePrincipal principal = mock(EmployeePrincipal.class);
    private final AppUser user = mock(AppUser.class);
    private final CurrentActorService actors = new CurrentActorService(users);

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        when(principal.getUserId()).thenReturn(42L);
        when(principal.getUsername()).thenReturn("employee");
        when(principal.isEnabled()).thenReturn(true);
        when(principal.getSessionVersion()).thenReturn(0L);
        when(user.getUsername()).thenReturn("employee");
        when(user.getActive()).thenReturn(true);
        when(users.findById(42L)).thenReturn(Optional.of(user));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void missingContextHasNoActorAndDoesNotQueryDatabase() {
        unauthenticated(); verifyNoInteractions(users);
    }
    @Test void anonymousTokenCannotBecomeAnActorEvenWithAnEmployeePrincipalAndAdminAuthority() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("fixture", principal,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        unauthenticated(); verifyNoInteractions(users);
    }
    @Test void unauthenticatedEmployeeTokenIsRejected() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(principal, null));
        unauthenticated(); verifyNoInteractions(users);
    }
    @Test void genericUserEntityMapAndIdPrincipalsAreNotAccepted() {
        for (Object unexpected : List.of("employee", user, Map.of("userId", 42L), 42L)) {
            signIn(unexpected); unauthenticated();
        }
        verifyNoInteractions(users);
    }
    @Test void returnsOnlyIdAndUsernameFromTheAuthenticatedEmployee() {
        signIn(principal);
        assertEquals(new CurrentActor(42L, "employee"), actors.requireActor());
        verify(users).findById(42L);
        verifyNoMoreInteractions(users);
        verify(principal, never()).getPassword();
        verify(user, never()).getPasswordHash();
    }
    @Test void clientDetailsAndGrantedAuthoritiesCannotSpoofActorIdentity() {
        var token = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("actorId", 999L, "username", "forged", "roles", List.of("ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(token);
        assertEquals(new CurrentActor(42L, "employee"), actors.requireActor());
        verify(users).findById(42L); verify(users, never()).findById(999L);
    }
    @Test void disabledPrincipalFailsBeforeDatabaseRead() {
        when(principal.isEnabled()).thenReturn(false); signIn(principal);
        unauthenticated(); verifyNoInteractions(users);
    }
    @Test void invalidPrincipalIdsAndUsernamesFailClosed() {
        signIn(principal);
        for (Long id : new Long[]{null, 0L, -1L}) { when(principal.getUserId()).thenReturn(id); unauthenticated(); }
        when(principal.getUserId()).thenReturn(42L);
        for (String name : new String[]{null, "", " "}) { when(principal.getUsername()).thenReturn(name); unauthenticated(); }
        verifyNoInteractions(users);
    }
    @Test void removedAccountIsUnauthenticated() {
        when(users.findById(42L)).thenReturn(Optional.empty()); signIn(principal); unauthenticated();
    }
    @Test void inactiveOrInvalidActiveFlagRejectsOldEnabledSnapshotWithoutClaimingSessionRevocation() {
        signIn(principal);
        var original = SecurityContextHolder.getContext().getAuthentication();
        for (Boolean active : new Boolean[]{false, null}) { when(user.getActive()).thenReturn(active); unauthenticated(); }
        assertSame(original, SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void usernameChangeRequiresLoginAgainInsteadOfSilentlyRewritingIdentity() {
        signIn(principal); when(user.getUsername()).thenReturn("changed"); unauthenticated();
    }
    @Test void actorSerializationContainsNoJpaOrCredentialState() {
        signIn(principal);
        var json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(actors.requireActor()));
        assertEquals(2, json.size()); assertEquals(42L, json.get("userId").longValue());
        assertEquals("employee", json.get("username").asString());
        assertNull(json.get("password")); assertNull(json.get("passwordHash")); assertNull(json.get("user"));
    }

    private void signIn(Object value) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(value, null, List.of()));
    }
    private void unauthenticated() {
        var error = assertThrows(BusinessException.class, actors::requireActor);
        assertEquals(ErrorCode.UNAUTHENTICATED, error.code());
        assertEquals("Authentication is required.", error.publicDetail()); assertNull(error.getCause());
    }
}
