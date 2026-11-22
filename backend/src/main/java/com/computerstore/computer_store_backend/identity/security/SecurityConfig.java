package com.computerstore.computer_store_backend.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import java.util.List;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    // Production cookie deployment, session timeouts and permission mapping remain pending.
    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
            ApiAuthenticationEntryPoint entryPoint, ApiAccessDeniedHandler deniedHandler,
            SecurityContextRepository contexts, CsrfTokenRepository csrfTokens,
            XorCsrfTokenRequestAttributeHandler csrfHandler) throws Exception {
        // Supplier V7 live read-back passed. SupplierService enforces each operation grant.
        return buildSupplierBoundary(http, entryPoint, deniedHandler, contexts, csrfTokens, csrfHandler, true);
    }

    SecurityFilterChain buildSupplierBoundary(HttpSecurity http,
            ApiAuthenticationEntryPoint entryPoint, ApiAccessDeniedHandler deniedHandler,
            SecurityContextRepository contexts, CsrfTokenRepository csrfTokens,
            XorCsrfTokenRequestAttributeHandler csrfHandler, boolean supplierReleased) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> {
                    authorize
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        // Category V1 only: service PermissionPolicy enforces the exact operation grant.
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/{id}").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/categories/{id}").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories/{id}/deactivate").authenticated();
                    if (supplierReleased) {
                        authorize.requestMatchers(HttpMethod.GET, "/api/v1/suppliers", "/api/v1/suppliers/{id}").authenticated()
                                .requestMatchers(HttpMethod.POST, "/api/v1/suppliers", "/api/v1/suppliers/{id}/deactivate").authenticated()
                                .requestMatchers(HttpMethod.PUT, "/api/v1/suppliers/{id}").authenticated();
                    }
                    authorize.anyRequest().denyAll();
                })
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens).csrfTokenRequestHandler(csrfHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context
                        .securityContextRepository(contexts)
                        .requireExplicitSave(true))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationProvider employeeAuthenticationProvider(EmployeeUserDetailsService users, PasswordEncoder encoder) {
        return new EmployeeAuthenticationProvider(users, encoder);
    }

    @Bean
    AuthenticationManager employeeAuthenticationManager(AuthenticationProvider employeeAuthenticationProvider) {
        return new ProviderManager(employeeAuthenticationProvider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }

    @Bean
    XorCsrfTokenRequestAttributeHandler csrfRequestHandler() { return new XorCsrfTokenRequestAttributeHandler(); }

    @Bean
    SessionAuthenticationStrategy loginSessionAuthenticationStrategy(CsrfTokenRepository tokens,
            XorCsrfTokenRequestAttributeHandler handler) {
        var rotateCsrf = new CsrfAuthenticationStrategy(tokens);
        rotateCsrf.setRequestHandler(handler);
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), rotateCsrf));
    }
}
