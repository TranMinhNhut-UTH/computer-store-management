package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.identity.dto.request.LoginRequest;
import com.computerstore.computer_store_backend.identity.dto.response.CurrentUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationSessionService {
    private final AuthenticationManager manager;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;
    private final Clock clock;
    private final CsrfLogoutHandler csrfLogout;
    private final SecurityContextLogoutHandler contextLogout;

    public AuthenticationSessionService(AuthenticationManager manager, SessionAuthenticationStrategy sessions,
            SecurityContextRepository contexts, CsrfTokenRepository tokens, Clock clock) {
        this.manager = manager;
        this.sessions = sessions;
        this.contexts = contexts;
        this.clock = clock;
        this.csrfLogout = new CsrfLogoutHandler(tokens);
        this.contextLogout = new SecurityContextLogoutHandler();
        this.contextLogout.setSecurityContextRepository(contexts);
    }

    public CurrentUserResponse login(LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        var credentials = UsernamePasswordAuthenticationToken.unauthenticated(input.username(), input.password());
        try {
            Authentication authentication = manager.authenticate(credentials);
            var verified = (EmployeePrincipal) authentication.getPrincipal();
            verified.eraseCredentials();
            var principal = verified.forSession(clock.instant());
            var sessionAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, authentication.getAuthorities());
            sessionAuthentication.setDetails(authentication.getDetails());
            sessions.onAuthentication(sessionAuthentication, request, response);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(sessionAuthentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            return CurrentUserResponse.from(principal);
        } catch (AuthenticationException exception) {
            // Never leave authenticated state behind after a failed login, including re-login.
            SecurityContextHolder.clearContext();
            contexts.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
            throw exception;
        } finally {
            credentials.eraseCredentials();
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        csrfLogout.logout(request, response, authentication);
        contextLogout.logout(request, response, authentication);
        String cookieName = request.getServletContext().getSessionCookieConfig().getName();
        new CookieClearingLogoutHandler(cookieName == null ? "JSESSIONID" : cookieName)
                .logout(request, response, authentication);
    }
}
