package com.computerstore.computer_store_backend.identity.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Erases both token credentials and the principal hash even when called without ProviderManager. */
public final class EmployeeAuthenticationProvider extends DaoAuthenticationProvider {
    public EmployeeAuthenticationProvider(EmployeeUserDetailsService users, PasswordEncoder encoder) {
        super(users);
        setPasswordEncoder(encoder);
        setHideUserNotFoundExceptions(true);
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!(authentication.getCredentials() instanceof CharSequence password) || !BcryptPasswordInput.isValid(password)) {
            throw new BadCredentialsException("Invalid credentials.");
        }
        return super.authenticate(authentication);
    }

    @Override
    protected Authentication createSuccessAuthentication(Object principal, Authentication authentication,
            UserDetails user) {
        Authentication result = super.createSuccessAuthentication(principal, authentication, user);
        // Authorization mapping (including factor-based authorization) is not approved yet.
        // Retain only the snapshot's explicitly supplied authorities, currently empty.
        var sanitized = UsernamePasswordAuthenticationToken.authenticated(
                result.getPrincipal(), null, user.getAuthorities());
        sanitized.setDetails(result.getDetails());
        if (result instanceof CredentialsContainer credentials) credentials.eraseCredentials();
        sanitized.eraseCredentials();
        return sanitized;
    }
}
