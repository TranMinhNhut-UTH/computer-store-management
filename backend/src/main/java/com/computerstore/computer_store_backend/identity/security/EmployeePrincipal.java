package com.computerstore.computer_store_backend.identity.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Detached security snapshot. Credential exists only during password verification. */
public final class EmployeePrincipal implements UserDetails, CredentialsContainer {
    @Serial private static final long serialVersionUID = 1L;
    private final Long userId;
    private final String username;
    private final boolean enabled;
    private final Long sessionVersion;
    private final EmployeeSessionLifetime sessionLifetime;
    private transient String passwordHash;

    EmployeePrincipal(Long userId, String username, String passwordHash, boolean enabled, long sessionVersion) {
        this(userId, username, passwordHash, enabled, sessionVersion, null);
    }

    private EmployeePrincipal(Long userId, String username, String passwordHash, boolean enabled, long sessionVersion,
            EmployeeSessionLifetime sessionLifetime) {
        this.userId = Objects.requireNonNull(userId);
        this.username = Objects.requireNonNull(username);
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.sessionVersion = sessionVersion;
        this.sessionLifetime = sessionLifetime;
    }

    EmployeePrincipal forSession(Instant authenticatedAt) {
        return new EmployeePrincipal(userId, username, null, enabled, sessionVersion,
                new EmployeeSessionLifetime(authenticatedAt));
    }

    EmployeeSessionLifetime sessionLifetime() { return sessionLifetime; }

    public Long getUserId() { return userId; }
    @Override public String getUsername() { return username; }
    @Override @JsonIgnore public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return enabled; }
    @JsonIgnore public Long getSessionVersion() { return sessionVersion; }
    public static boolean matchesAccount(EmployeePrincipal principal, String currentUsername,
            Boolean active, long currentVersion) {
        return principal.isEnabled() && Boolean.TRUE.equals(active) && currentUsername != null
                && currentUsername.equals(principal.getUsername())
                && Objects.equals(principal.getSessionVersion(), currentVersion);
    }
    // V1 has only is_active: no independent lock/expiration columns exist.
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    // Permission/role mapping remains pending; do not infer or grant authorities.
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public void eraseCredentials() { passwordHash = null; }
    @Override public String toString() { return "EmployeePrincipal[userId=" + userId + "]"; }
}
