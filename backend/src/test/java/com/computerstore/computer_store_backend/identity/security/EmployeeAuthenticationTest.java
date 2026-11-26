package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeAuthenticationTest {
    private AppUserRepository repository;
    private EmployeeUserDetailsService users;
    private PasswordEncoder encoder;
    private String rawPassword;
    private String hash;
    private AppUser user;

    @BeforeEach void setup() {
        repository = mock(AppUserRepository.class);
        users = new EmployeeUserDetailsService(repository);
        encoder = new SecurityConfig().passwordEncoder();
        rawPassword = UUID.randomUUID().toString();
        hash = encoder.encode(rawPassword);
        user = mock(AppUser.class);
        when(user.getId()).thenReturn(42L);
        when(user.getUsername()).thenReturn("employee");
        when(user.getPasswordHash()).thenReturn(hash);
        when(user.getActive()).thenReturn(true);
        when(repository.findByUsername("employee")).thenReturn(Optional.of(user));
    }

    @Test void loadsDetachedPrincipalByNormalizedUsernameAndRetainsId() {
        var principal = users.loadUserByUsername(" \tEMPLOYEE ");
        assertEquals(42L, principal.getUserId());
        assertEquals("employee", principal.getUsername());
        assertTrue(principal.isEnabled());
        assertTrue(principal.getAuthorities().isEmpty());
        verify(repository).findByUsername("employee");
        verifyNoMoreInteractions(repository);
    }

    @Test void missingUsernameIsInternalNotFoundWithSafeMessage() {
        var exception = assertThrows(UsernameNotFoundException.class, () -> users.loadUserByUsername("missing"));
        assertEquals("Invalid credentials.", exception.getMessage());
    }

    @Test void invalidAndBlankUsernamesFailBeforeRepositoryAccess() {
        for (String name : new String[]{null, "", " \t ", "tên", "employee@example.com", "a b"}) {
            assertThrows(UsernameNotFoundException.class, () -> users.loadUserByUsername(name));
        }
        verifyNoInteractions(repository);
    }

    @Test void inactiveIsTheOnlySchemaStateForLockedOrDisabledAccount() {
        when(user.getActive()).thenReturn(false);
        var principal = users.loadUserByUsername("employee");
        assertFalse(principal.isEnabled());
        assertTrue(principal.isAccountNonLocked()); // No independent locked column in V1.
        assertThrows(DisabledException.class, () -> provider().authenticate(token(rawPassword)));
    }

    @Test void nullActiveFailsClosed() {
        when(user.getActive()).thenReturn(null);
        assertThrows(DisabledException.class, () -> provider().authenticate(token(rawPassword)));
    }

    @Test void missingIdOrHashCannotAuthenticate() {
        when(user.getId()).thenReturn(null);
        assertThrows(UsernameNotFoundException.class, () -> users.loadUserByUsername("employee"));
        when(user.getId()).thenReturn(42L);
        for (String invalidHash : new String[]{null, "", " "}) {
            when(user.getPasswordHash()).thenReturn(invalidHash);
            assertThrows(BadCredentialsException.class, () -> provider().authenticate(token(rawPassword)));
        }
    }

    @Test void bcryptHashesWithSaltAndMatchesOnlyCorrectPassword() {
        assertNotEquals(rawPassword, hash);
        assertTrue(hash.startsWith("$2"));
        assertTrue(encoder.matches(rawPassword, hash));
        assertFalse(encoder.matches(UUID.randomUUID().toString(), hash));
        assertNotEquals(hash, encoder.encode(rawPassword));
        assertFalse(encoder.matches(rawPassword, rawPassword));
    }

    @Test void wrongPasswordAndMissingUserBecomeBadCredentials() {
        assertThrows(BadCredentialsException.class, () -> provider().authenticate(token(UUID.randomUUID().toString())));
        assertThrows(BadCredentialsException.class, () -> provider().authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("missing", rawPassword)));
    }

    @Test void validatesUtf8BytesBeforeLookupAndAuthenticatesExactBoundaryPasswords() {
        var provider = provider();
        for (String invalid : new String[]{"A".repeat(73), "\u00e9".repeat(37),
                "\uD83D\uDE00".repeat(18) + "a", new String(new char[]{'a', 0xd800})}) {
            var failure = assertThrows(BadCredentialsException.class, () -> provider.authenticate(token(invalid)));
            assertEquals("Invalid credentials.", failure.getMessage());
        }
        verifyNoInteractions(repository);

        for (String allowed : new String[]{" " + "A".repeat(70) + " ", "\u00e9".repeat(36), "\uD83D\uDE00".repeat(18)}) {
            assertEquals(72, allowed.getBytes(StandardCharsets.UTF_8).length);
            when(user.getPasswordHash()).thenReturn(encoder.encode(allowed));
            assertTrue(provider.authenticate(token(allowed)).isAuthenticated());
        }
    }

    @Test void successfulAuthenticationErasesHashAndTokenCredentialsEvenWithoutManager() throws Exception {
        var authenticated = provider().authenticate(token(rawPassword));
        assertTrue(authenticated.isAuthenticated());
        assertNull(authenticated.getCredentials());
        var principal = assertInstanceOf(EmployeePrincipal.class, authenticated.getPrincipal());
        assertEquals(42L, principal.getUserId());
        assertNull(principal.getPassword());
        assertTrue(authenticated.getAuthorities().isEmpty());
        assertFalse(authenticated.toString().contains(hash));
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authenticated);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        String serialized = new String(serialize(session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)), StandardCharsets.ISO_8859_1);
        assertFalse(serialized.contains(hash));
        assertFalse(serialized.contains(rawPassword));
    }

    @Test void managerUsesProviderAndKeepsUserIdWithoutAssigningRoles() {
        var manager = new ProviderManager(provider());
        var result = manager.authenticate(token(rawPassword));
        assertEquals(42L, ((EmployeePrincipal) result.getPrincipal()).getUserId());
        assertTrue(result.getAuthorities().isEmpty());
        assertNull(result.getCredentials());
    }

    @Test void principalDoesNotExposeHashThroughJsonJavaSerializationOrToString() throws Exception {
        var principal = users.loadUserByUsername("employee");
        String json = JsonMapper.builder().build().writeValueAsString(principal);
        assertFalse(json.contains(hash));
        assertFalse(json.contains("password"));
        assertFalse(principal.toString().contains(hash));
        byte[] bytes = serialize(principal);
        assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains(hash));
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            var restored = (EmployeePrincipal) input.readObject();
            assertNull(restored.getPassword());
            assertEquals(42L, restored.getUserId());
        }
        principal.eraseCredentials();
        assertNull(principal.getPassword());
    }

    private EmployeeAuthenticationProvider provider() { return new EmployeeAuthenticationProvider(users, encoder); }
    private UsernamePasswordAuthenticationToken token(String password) {
        return UsernamePasswordAuthenticationToken.unauthenticated("employee", password);
    }
    private byte[] serialize(Object value) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var stream = new ObjectOutputStream(output)) { stream.writeObject(value); }
        return output.toByteArray();
    }
}
