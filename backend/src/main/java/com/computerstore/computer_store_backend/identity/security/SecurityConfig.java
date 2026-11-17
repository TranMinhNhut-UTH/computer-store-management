package com.computerstore.computer_store_backend.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    // Foundation only: SPA CSRF exchange, login/logout, cookie deployment and session timeouts
    // remain pending approval. A future login flow must explicitly save the authenticated context.
    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
            ApiAuthenticationEntryPoint entryPoint, ApiAccessDeniedHandler deniedHandler) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .csrf(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context
                        .securityContextRepository(new HttpSessionSecurityContextRepository())
                        .requireExplicitSave(true))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .build();
    }

    /** Temporary fail-closed provider: prevents Boot's generated user; no credentials or login flow.
     * Replace when the approved database authentication implementation is introduced.
     */
    @Bean
    AuthenticationProvider unavailableAuthenticationProvider() {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                throw new BadCredentialsException("Authentication is not available.");
            }

            @Override
            public boolean supports(Class<?> authentication) { return true; }
        };
    }
}
