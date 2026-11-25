package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reject stale employee cookies before CSRF or route authorization can run. */
final class SessionVersionFilter extends OncePerRequestFilter {
    private final AppUserRepository users;
    private final ApiAuthenticationEntryPoint entryPoint;
    private final OrRequestMatcher publicRoutes;

    SessionVersionFilter(AppUserRepository users, ApiAuthenticationEntryPoint entryPoint) {
        this.users = users;
        this.entryPoint = entryPoint;
        var paths = PathPatternRequestMatcher.withDefaults();
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
        }
        chain.doFilter(request, response);
    }
}
