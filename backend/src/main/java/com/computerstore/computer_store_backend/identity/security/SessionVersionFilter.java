package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reject stale employee cookies before CSRF or route authorization can run. */
final class SessionVersionFilter extends OncePerRequestFilter {
    private static final String VALIDATED_LIFETIME = SessionVersionFilter.class.getName() + ".lifetime";
    private final AppUserRepository users;
    private final ApiAuthenticationEntryPoint entryPoint;
    private final Clock clock;
    private final OrRequestMatcher publicRoutes;
    private final RequestMatcher apiRoutes;

    SessionVersionFilter(AppUserRepository users, ApiAuthenticationEntryPoint entryPoint, Clock clock) {
        this.users = users;
        this.entryPoint = entryPoint;
        this.clock = clock;
        var paths = PathPatternRequestMatcher.withDefaults();
        apiRoutes = paths.matcher("/api/**");
        publicRoutes = new OrRequestMatcher(paths.matcher(HttpMethod.GET, "/api/health"),
                paths.matcher(HttpMethod.GET, "/api/v1/auth/csrf"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/login"));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return publicRoutes.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof EmployeePrincipal principal) {
            var lifetime = principal.sessionLifetime();
            if (lifetime == null || !lifetime.access(clock, false)) {
                entryPoint.commence(request, response, null);
                return;
            }
            boolean valid;
            try {
                valid = users.findById(principal.getUserId())
                        .filter(account -> EmployeePrincipal.matchesAccount(principal, account.getUsername(),
                                account.getActive(), account.getSessionVersion()))
                        .isPresent();
            } catch (DataAccessException exception) {
                var error = ErrorCode.INTERNAL_ERROR;
                SecurityProblemWriter.write(request, response, error.status(), error.name(), error.detail());
                return;
            }
            if (!valid) {
                entryPoint.commence(request, response, null);
                return;
            }
            request.setAttribute(VALIDATED_LIFETIME, lifetime);
        } else if (authentication == null && apiRoutes.matches(request)
                && request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid()) {
            // The servlet container can evict idle sessions before the application sees their principal.
            entryPoint.commence(request, response, null);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Only requests admitted by the HTTP security chain can renew the idle deadline. */
    static final class Activity extends OncePerRequestFilter {
        private final Clock clock;
        private final ApiAuthenticationEntryPoint entryPoint;

        Activity(Clock clock, ApiAuthenticationEntryPoint entryPoint) {
            this.clock = clock;
            this.entryPoint = entryPoint;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (request.getAttribute(VALIDATED_LIFETIME) instanceof EmployeeSessionLifetime lifetime
                    && !lifetime.access(clock, true)) {
                entryPoint.commence(request, response, null);
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
