package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.HealthController;
import com.computerstore.computer_store_backend.identity.authorization.CurrentActor;
import com.computerstore.computer_store_backend.identity.authorization.EmployeeRole;
import com.computerstore.computer_store_backend.identity.authorization.ResolvedAccess;
import com.computerstore.computer_store_backend.identity.authorization.RoleResolutionService;
import com.computerstore.computer_store_backend.identity.controller.UiCapabilitiesController;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit MVC/security-only context: no application scan, JPA, datasource or Flyway. */
@SpringJUnitConfig(SecurityConfigTest.FixtureConfig.class)
@WebAppConfiguration
class SecurityConfigTest {
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void uiCapabilitiesProjectsOnlyCurrentCatalogGrants() throws Exception {
        var roles = context.getBean(RoleResolutionService.class);
        reset(roles);
        String path = "/api/v1/auth/ui-capabilities";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(roles);

        var actor = new CurrentActor(1L, "fixture");
        when(roles.resolveCurrent()).thenReturn(new ResolvedAccess(actor, Set.of(EmployeeRole.ADMIN), Set.of()));
        mvc.perform(get(path).with(user("fixture").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions").isEmpty());

        when(roles.resolveCurrent()).thenReturn(new ResolvedAccess(actor, Set.of(EmployeeRole.SALES_STAFF),
                Set.of("CATEGORY_READ", "CUSTOMER_READ", "PERMISSION_READ", "SALE_CONFIRM", "SUPPLIER_UPDATE")));
        mvc.perform(get(path).with(user("fixture")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").value(contains("CATEGORY_READ", "CUSTOMER_READ")));
        mvc.perform(post(path).with(user("fixture")).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void healthIsPublicAndDoesNotCreateSession() throws Exception {
        var result = mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(content().string("Computer Store API is running")).andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    @Test void anonymousApiRequestGetsJson401EvenWhenHtmlIsAccepted() throws Exception {
        mvc.perform(get("/api/private").queryParam("secret", "LEAK").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Authentication is required."))
                .andExpect(jsonPath("$.instance").value("/api/private"))
                .andExpect(jsonPath("$.properties").doesNotExist())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(not(containsString("LEAK"))));
    }

    @Test void authenticatedUserCannotAccessUnapprovedApi() throws Exception {
        mvc.perform(get("/api/private").with(user("fixture").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.detail").value("Access is denied."));
    }

    @Test void missingCsrfIsRejectedBeforePublicOrProtectedEndpoint() throws Exception {
        for (String path : List.of("/api/health", "/api/private")) {
            for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PATCH, HttpMethod.DELETE)) {
                for (boolean authenticated : List.of(false, true)) {
                    var request = request(method, path);
                    if (authenticated) request.with(user("fixture"));
                    mvc.perform(request)
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
                }
            }
        }
    }

    @Test void invalidCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/private").with(user("fixture")).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test void csrfTokenDoesNotGrantAccessToDeniedApi() throws Exception {
        mvc.perform(post("/api/private").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/private").with(user("fixture")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test void sessionAuthenticationIsReadButDoesNotBypassDenyAll() throws Exception {
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("fixture", null, List.of()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        mvc.perform(get("/api/private").session(session)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test void loginPageBasicAuthenticationAndNonApiPathsAreNotOpened() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/private").with(httpBasic("fixture", "not-a-real-password")))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(get("/unapproved").with(user("fixture"))).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user("fixture")).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void noDefaultUserIsCreatedEvenWithBootUserAutoConfiguration() {
        assertEquals(1, context.getBeansOfType(UserDetailsService.class).size());
        assertInstanceOf(EmployeeUserDetailsService.class, context.getBean(UserDetailsService.class));
        assertEquals(1, context.getBeansOfType(AuthenticationProvider.class).size());
        var provider = context.getBean(AuthenticationProvider.class);
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("fixture", "not-a-real-password")));
        assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
    }

    @Test void handlersNeverEchoExceptionMessagesOrQueryParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        request.setQueryString("password=LEAK");
        MockHttpServletResponse unauthenticated = new MockHttpServletResponse();
        context.getBean(ApiAuthenticationEntryPoint.class).commence(request, unauthenticated,
                new BadCredentialsException("SQL password LEAK"));
        MockHttpServletResponse denied = new MockHttpServletResponse();
        context.getBean(ApiAccessDeniedHandler.class).handle(request, denied,
                new AccessDeniedException("SQL password LEAK"));
        for (var response : List.of(unauthenticated, denied)) {
            String body = response.getContentAsString();
            assertFalse(body.contains("LEAK"));
            assertFalse(body.contains("Exception"));
            assertFalse(body.contains("stackTrace"));
        }
    }

    @Test void corsDoesNotAllowUnapprovedOrigin() throws Exception {
        mvc.perform(options("/api/private").header("Origin", "https://unapproved.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
            EmployeeUserDetailsService.class, HealthController.class, PrivateFixture.class,
            UserDetailsServiceAutoConfiguration.class, UiCapabilitiesController.class})
    static class FixtureConfig {
        @Bean RoleResolutionService roles() { return mock(RoleResolutionService.class); }
        @Bean AppUserRepository appUserRepository() {
            return org.mockito.Mockito.mock(AppUserRepository.class);
        }
    }

    @RestController
    static class PrivateFixture {
        @GetMapping("/api/private") String read() { return "must not be exposed"; }
        @PostMapping("/api/private") void write(HttpServletRequest request) {
            throw new AssertionError("Denied request reached controller");
        }
    }
}
