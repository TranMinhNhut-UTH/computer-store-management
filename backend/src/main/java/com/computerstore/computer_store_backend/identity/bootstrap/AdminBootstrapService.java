package com.computerstore.computer_store_backend.identity.bootstrap;

import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.entity.UserRole;
import com.computerstore.computer_store_backend.identity.repository.AppRoleRepository;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.repository.UserRoleRepository;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Map;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapException.Reason.*;

/** Explicitly constructed by the manual CLI; deliberately not a component or startup runner. */
public final class AdminBootstrapService {
    public enum Result { DISABLED, CREATED }
    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final UserRoleRepository userRoles;
    private final EntityManager entities;
    private final PasswordEncoder encoder;
    private final UtcTimeProvider time;
    private final TransactionTemplate transaction;

    public AdminBootstrapService(AppUserRepository users, AppRoleRepository roles, UserRoleRepository userRoles,
            EntityManager entities, PasswordEncoder encoder, UtcTimeProvider time, PlatformTransactionManager manager) {
        this.users = users; this.roles = roles; this.userRoles = userRoles;
        this.entities = entities; this.encoder = encoder; this.time = time;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(30);
    }

    public Result bootstrap(boolean explicitlyEnabled, AdminBootstrapRequest input) {
        if (!explicitlyEnabled) return Result.DISABLED;
        if (input == null) throw new AdminBootstrapException(INVALID_INPUT);
        try {
            return transaction.execute(status -> {
                if (!"computer_store_dev".equals(entities.createNativeQuery("SELECT DATABASE()").getSingleResult())) {
                    throw new AdminBootstrapException(UNSAFE_DATABASE_TARGET);
                }
                var admin = roles.findByCode("ADMIN").orElseThrow(() -> new AdminBootstrapException(ADMIN_ROLE_MISSING));
                // All CLI executions serialize on the already-seeded role, including different usernames.
                entities.lock(admin, LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 5000));
                var existing = entities.createQuery("select ur.id from UserRole ur where ur.role.id = :roleId", Long.class)
                        .setParameter("roleId", admin.getId()).setMaxResults(1).getResultList();
                // Inactive ADMIN accounts count too: this tool never recovers or modifies an account.
                if (!existing.isEmpty()) throw new AdminBootstrapException(ADMIN_ALREADY_EXISTS);
                if (users.existsByUsername(input.username())) throw new AdminBootstrapException(USERNAME_EXISTS);

                // Existing JPA entities expose protected no-arg constructors; no Entity changes are needed.
                var user = BeanUtils.instantiateClass(AppUser.class);
                var now = time.now();
                user.setUsername(input.username());
                user.setFullName(input.fullName());
                user.setPasswordHash(input.encodePassword(encoder));
                user.setActive(true);
                user.setCreatedAt(now);
                user.setUpdatedAt(now);
                users.saveAndFlush(user);
                var link = BeanUtils.instantiateClass(UserRole.class);
                link.setUser(user);
                link.setRole(admin);
                link.setAssignedAt(now);
                userRoles.saveAndFlush(link);
                return Result.CREATED;
            });
        } catch (AdminBootstrapException safe) {
            throw safe;
        } catch (RuntimeException ignored) {
            // Includes lock/UNIQUE/flush/commit failures. Never retry, expose SQL, or retain a secret-bearing cause.
            throw new AdminBootstrapException(FAILED);
        }
    }
}
