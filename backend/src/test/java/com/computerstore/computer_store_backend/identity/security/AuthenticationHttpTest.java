package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.HealthController;
import com.computerstore.computer_store_backend.identity.authorization.CurrentActorService;
import com.computerstore.computer_store_backend.identity.controller.AuthenticationController;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.shared.error.GlobalExceptionHandler;
import com.computerstore.computer_store_backend.shared.validation.IdentifierNormalizer;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the real filter chain, session strategies, CSRF repository and controller; only provider is mocked. */
@SpringJUnitConfig(AuthenticationHttpTest.FixtureConfig.class)
@WebAppConfiguration
class AuthenticationHttpTest {
    private static final String AUTH = "/api/v1/auth";
    private static final String BODY = "{\"username\":\" EMPLOYEE \",\"password\":\" password with spaces \"}";
    @Autowired WebApplicationContext context;
    @Autowired AppUserRepository users;
    @MockitoBean(name = "employeeAuthenticationProvider") AuthenticationProvider provider;
    private MockMvc mvc;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        reset(users);
        var account = mock(AppUser.class);
        when(account.getActive()).thenReturn(true);
        when(account.getUsername()).thenReturn("employee");
        when(users.findById(9007199254740993L)).thenReturn(Optional.of(account));
        when(provider.supports(any())).thenReturn(true);
        when(provider.authenticate(any())).thenAnswer(invocation -> {
            Authentication input = invocation.getArgument(0);
            String username = IdentifierNormalizer.username(input.getName());
            if ("disabled".equals(username)) throw new DisabledException("Internal disabled details LEAK");
            if ("failure".equals(username)) throw new IllegalStateException("SQL password LEAK");
            if ("unavailable".equals(username)) throw new InternalAuthenticationServiceException("DB LEAK");
            if (!"employee".equals(username) || !" password with spaces ".equals(input.getCredentials())) {
                throw new BadCredentialsException("Internal credentials LEAK");
            }
            var current = users.findById(9007199254740993L).orElseThrow();
            var principal = new EmployeePrincipal(9007199254740993L, "employee", "HASH_MUST_NOT_LEAK", true,
                    current.getSessionVersion());
            return UsernamePasswordAuthenticationToken.authenticated(principal, input.getCredentials(), List.of());
        });
    }

    @Test void csrfEndpointReturnsMaskedTokenAndHeaderWithoutSessionId() throws Exception {
        var token = csrf(null);
        assertEquals("X-CSRF-TOKEN", token.header());
        assertFalse(token.token().isBlank());
        assertNotNull(token.session());
        mvc.perform(get(AUTH + "/csrf").session(token.session()))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.sessionId").doesNotExist());
        verifyNoInteractions(provider);
    }

    @Test void missingCsrfAndWrongCsrfRejectLoginBeforeProvider() throws Exception {
        mvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        var token = csrf(null);
        mvc.perform(post(AUTH + "/login").session(token.session()).header(token.header(), "invalid")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verify(provider, never()).authenticate(any());
    }

    @Test void successfulLoginRotatesSessionIdAndPersistsSafeAuthenticationForMe() throws Exception {
        var token = csrf(null);
        String oldId = token.session().getId();
        var loggedIn = login(token, BODY);
        assertNotEquals(oldId, loggedIn.getId());
        var securityContext = (SecurityContext) loggedIn.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(securityContext);
        var authentication = securityContext.getAuthentication();
        assertTrue(authentication.isAuthenticated());
        assertNull(authentication.getCredentials());
        var principal = assertInstanceOf(EmployeePrincipal.class, authentication.getPrincipal());
        assertNull(principal.getPassword());
        assertTrue(principal.getAuthorities().isEmpty());
        mvc.perform(get(AUTH + "/me").session(loggedIn).param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("9007199254740993"))
                .andExpect(jsonPath("$.username").value("employee"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.authorities").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());
    }

    @Test void meRechecksCurrentAccountOnTheExistingSession() throws Exception {
        var session = login(csrf(null), BODY);
        var account = users.findById(9007199254740993L).orElseThrow();
        mvc.perform(get(AUTH + "/me").session(session)).andExpect(status().isOk());

        when(account.getActive()).thenReturn(false);
        when(account.getSessionVersion()).thenReturn(1L);
        mvc.perform(get(AUTH + "/me").session(session)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.username").doesNotExist());
        assertNotNull(session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));

        when(account.getActive()).thenReturn(true);
        mvc.perform(get(AUTH + "/me").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(post(AUTH + "/logout").session(session))
                .andExpect(status().isUnauthorized());
        var fresh = login(csrf(null), BODY);
        mvc.perform(get(AUTH + "/me").session(fresh)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionVersion").doesNotExist());
        when(users.findById(9007199254740993L)).thenReturn(Optional.empty());
        mvc.perform(get(AUTH + "/me").session(fresh)).andExpect(status().isUnauthorized());
    }

    @Test void anonymousMeAndAnonymousLogoutWithValidCsrfReturn401() throws Exception {
        mvc.perform(get(AUTH + "/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        var token = csrf(null);
        mvc.perform(post(AUTH + "/logout").session(token.session()).header(token.header(), token.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test void invalidCredentialsAndDisabledAccountReturnIdenticalPublicErrors() throws Exception {
        String firstBody = null;
        for (String username : List.of("missing", "employee", "disabled")) {
            var token = csrf(null);
            String body = "{\"username\":\"" + username + "\",\"password\":\"incorrect\"}";
            var result = mvc.perform(post(AUTH + "/login").session(token.session())
                    .header(token.header(), token.token()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(content().string(not(containsString("LEAK")))).andReturn();
            if (firstBody == null) firstBody = result.getResponse().getContentAsString();
            else assertEquals(firstBody, result.getResponse().getContentAsString());
            assertNull(token.session().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
            mvc.perform(get(AUTH + "/me").session(token.session())).andExpect(status().isUnauthorized());
        }
    }

    @Test void validationAndMalformedJsonReturn400WithoutAuthentication() throws Exception {
        for (String body : List.of("{}", "{\"username\":\"\",\"password\":\"\"}", "{malformed")) {
            var token = csrf(null);
            mvc.perform(post(AUTH + "/login").session(token.session()).header(token.header(), token.token())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verify(provider, never()).authenticate(any());
    }

    @Test void unexpectedAuthenticationFailureIsSanitized500() throws Exception {
        var token = csrf(null);
        mvc.perform(post(AUTH + "/login").session(token.session()).header(token.header(), token.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"failure\",\"password\":\"wrong\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("LEAK"))));
        assertNull(token.session().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }

    @Test void authenticationInfrastructureFailureReturnsSanitized500InsteadOfCredentials401() throws Exception {
        var token = csrf(null);
        mvc.perform(post(AUTH + "/login").session(token.session()).header(token.header(), token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"unavailable\",\"password\":\"wrong\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("LEAK"))));
        assertNull(token.session().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }

    @Test void passwordIsNotTrimmed() throws Exception {
        var token = csrf(null);
        mvc.perform(post(AUTH + "/login").session(token.session()).header(token.header(), token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"employee\",\"password\":\"password with spaces\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void loginClearsOldCsrfAndNewTokenWorksForLogout() throws Exception {
        var oldToken = csrf(null);
        var session = login(oldToken, BODY);
        mvc.perform(post(AUTH + "/logout").session(session).header(oldToken.header(), oldToken.token()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertFalse(session.isInvalid());
        var newToken = csrf(session);
        mvc.perform(post(AUTH + "/logout").session(session).header(newToken.header(), newToken.token()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertTrue(session.isInvalid());
    }

    @Test void logoutRequiresCsrfAndDoesNotLogOutOnRejectedRequest() throws Exception {
        var session = login(csrf(null), BODY);
        mvc.perform(post(AUTH + "/logout").session(session))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get(AUTH + "/me").session(session)).andExpect(status().isOk());
    }

    @Test void logoutInvalidatesSessionClearsCookieAndRequiresFreshCsrfBeforeNextLogin() throws Exception {
        var session = login(csrf(null), BODY);
        var token = csrf(session);
        String oldSessionId = session.getId();
        mvc.perform(post(AUTH + "/logout").session(session).header(token.header(), token.token()))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("JSESSIONID", 0));
        assertTrue(session.isInvalid());
        mvc.perform(get(AUTH + "/me").cookie(new Cookie("JSESSIONID", oldSessionId)))
                .andExpect(status().isUnauthorized());
        var fresh = csrf(null);
        assertNotEquals(oldSessionId, fresh.session().getId());
        mvc.perform(post(AUTH + "/login").session(fresh.session()).header(token.header(), token.token())
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        login(fresh, BODY);
    }

    @Test void failedReloginDoesNotRetainAuthenticatedState() throws Exception {
        var session = login(csrf(null), BODY);
        var token = csrf(session);
        mvc.perform(post(AUTH + "/login").session(session).header(token.header(), token.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"missing\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(AUTH + "/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test void businessApisAndWrongMethodsRemainDenied() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        var session = login(csrf(null), BODY);
        mvc.perform(get("/api/products").session(session)).andExpect(status().isForbidden());
        mvc.perform(get(AUTH + "/login").session(session)).andExpect(status().isForbidden());
        mvc.perform(get(AUTH + "/logout").session(session)).andExpect(status().isForbidden());
        var token = csrf(session);
        mvc.perform(post(AUTH + "/me").session(session).header(token.header(), token.token()))
                .andExpect(status().isForbidden());
    }

    @Test void rereadingCsrfDoesNotRotateStoredTokenBeforeLogin() throws Exception {
        var first = csrf(null);
        csrf(first.session());
        login(first, BODY);
    }

    private Token csrf(MockHttpSession session) throws Exception {
        var request = get(AUTH + "/csrf");
        if (session != null) request.session(session);
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertEquals(2, body.size());
        var actualSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotEquals(actualSession.getId(), body.get("token").asText());
        assertFalse(body.has("sessionId"));
        return new Token(actualSession, body.get("headerName").asText(), body.get("token").asText());
    }

    private MockHttpSession login(Token token, String body) throws Exception {
        MvcResult result = mvc.perform(post(AUTH + "/login").session(token.session())
                .header(token.header(), token.token()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value("9007199254740993"))
                .andExpect(jsonPath("$.username").value("employee"))
                .andExpect(content().string(not(containsString("HASH_MUST_NOT_LEAK")))).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertEquals(2, json.readTree(result.getResponse().getContentAsString()).size());
        assertFalse(json.readTree(result.getResponse().getContentAsString()).has("sessionId"));
        return session;
    }

    record Token(MockHttpSession session, String header, String token) { }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
            EmployeeUserDetailsService.class, AuthenticationSessionService.class, AuthenticationController.class,
            CurrentActorService.class,
            GlobalExceptionHandler.class, HealthController.class})
    static class FixtureConfig {
        @Bean AppUserRepository appUserRepository() { return mock(AppUserRepository.class); }
    }
}
