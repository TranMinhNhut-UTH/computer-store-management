package com.computerstore.computer_store_backend.identity.authorization;

import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.security.EmployeePrincipal;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentActorService {
    private final AppUserRepository users;

    public CurrentActorService(AppUserRepository users) { this.users = users; }

    /** Identity is taken exclusively from the authenticated employee principal, never client input. */
    @Transactional(readOnly = true)
    public CurrentActor requireActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof EmployeePrincipal principal)
                || !principal.isEnabled() || principal.getUserId() == null || principal.getUserId() <= 0
                || principal.getUsername() == null || principal.getUsername().isBlank()) {
            throw unauthenticated();
        }
        var user = users.findById(principal.getUserId()).orElseThrow(CurrentActorService::unauthenticated);
        if (!EmployeePrincipal.matchesAccount(principal, user.getUsername(), user.getActive(), user.getSessionVersion())) {
            throw unauthenticated();
        }
        return new CurrentActor(principal.getUserId(), principal.getUsername(), user.getSessionVersion());
    }

    private static BusinessException unauthenticated() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.detail());
    }
}
