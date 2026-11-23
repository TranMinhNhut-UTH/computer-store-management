package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.validation.IdentifierNormalizer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;

    public EmployeeUserDetailsService(AppUserRepository users) { this.users = users; }

    @Override
    @Transactional(readOnly = true)
    public EmployeePrincipal loadUserByUsername(String username) {
        String normalized;
        try { normalized = IdentifierNormalizer.username(username); }
        catch (BusinessException exception) { throw unavailable(); }
        if (normalized == null || normalized.isEmpty()) throw unavailable();
        var user = users.findByUsername(normalized).orElseThrow(EmployeeUserDetailsService::unavailable);
        if (user.getId() == null || user.getUsername() == null || user.getPasswordHash() == null
                || user.getPasswordHash().isBlank()) throw unavailable();
        // Disabled users remain distinguishable internally through DaoAuthenticationProvider's check.
        // Null active (invalid database state) also fails closed.
        return new EmployeePrincipal(user.getId(), user.getUsername(), user.getPasswordHash(),
                Boolean.TRUE.equals(user.getActive()), user.getSessionVersion());
    }

    private static UsernameNotFoundException unavailable() {
        return new UsernameNotFoundException("Invalid credentials.");
    }
}
