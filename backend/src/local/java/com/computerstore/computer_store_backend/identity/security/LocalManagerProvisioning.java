package com.computerstore.computer_store_backend.identity.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapException;
import com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapRequest;
import com.computerstore.computer_store_backend.identity.bootstrap.BootstrapDatabaseTarget;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.entity.UserRole;
import com.computerstore.computer_store_backend.identity.repository.AppRoleRepository;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import jakarta.persistence.EntityManager;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Standalone test-classpath tool. Its Maven profile never adds it to the application artifact. */
public final class LocalManagerProvisioning {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/computer_store_dev";
    private static final String OPT_IN = "--provision-manager";
    private static final String ACKNOWLEDGEMENT = "--ack-local-development-only";

    private LocalManagerProvisioning() { }

    public static void main(String[] args) {
        var console = System.console();
        Input input = console == null ? null : new Input() {
            public String line(String prompt) { return console.readLine("%s", prompt); }
            public char[] password(String prompt) { return console.readPassword("%s", prompt); }
        };
        int status = run(args, input, System.getenv(), LocalManagerProvisioning::execute, System.out);
        if (status != 0) System.exit(status);
    }

    interface Input {
        String line(String prompt);
        char[] password(String prompt);
    }

    @FunctionalInterface
    interface Provisioner {
        void create(BootstrapDatabaseTarget database, AdminBootstrapRequest request);
    }

    static int run(String[] args, Input input, Map<String, String> env, Provisioner provisioner, PrintStream output) {
        if (args.length != 2 || !Arrays.asList(args).containsAll(java.util.List.of(OPT_IN, ACKNOWLEDGEMENT))
                || input == null) {
            output.println("Use --provision-manager --ack-local-development-only in an interactive terminal.");
            return 1;
        }
        if (!URL.equals(env.get("DB_URL"))) {
            output.println("Provisioning stopped: DB_URL must be the exact local computer_store_dev URL.");
            return 1;
        }

        char[] password = null;
        char[] confirmation = null;
        try {
            var database = BootstrapDatabaseTarget.fromEnvironment(env);
            String username = input.line("CSM username: ");
            String fullName = input.line("Employee full name: ");
            password = input.password("CSM password (hidden): ");
            confirmation = input.password("Confirm CSM password (hidden): ");
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                throw new Stop("Passwords are required and must match.");
            }
            // Reuse the existing account field validation and erasable password holder.
            try (var request = new AdminBootstrapRequest(username, fullName, password)) {
                if (request.passwordEquals(database.password())) {
                    throw new Stop("Use a CSM password distinct from the MySQL password.");
                }
                provisioner.create(database, request);
            }
            output.println("Local MANAGER account committed.");
            return 0;
        } catch (Stop stop) {
            output.println(stop.getMessage());
        } catch (AdminBootstrapException invalid) {
            output.println("Provisioning stopped: " + invalid.reason().name() + ".");
        } catch (RuntimeException ignored) {
            output.println("Provisioning failed. Verify the local account state before retrying.");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
        return 1;
    }

    private static void execute(BootstrapDatabaseTarget database, AdminBootstrapRequest request) {
        // This CLI owns its process. Silence persistence logs before constructing the datasource.
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext logging)) {
            throw new Stop("Provisioning stopped: unsupported logging configuration.");
        }
        logging.getLoggerList().forEach(logger -> logger.setLevel(Level.OFF));
        logging.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(Level.OFF);
        java.util.logging.LogManager.getLogManager().reset();
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.OFF);

        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource(database.url(), database.username(), database.password()));
        factory.setPackagesToScan(AppUser.class.getPackageName());
        var vendor = new HibernateJpaVendorAdapter();
        vendor.setGenerateDdl(false);
        vendor.setShowSql(false);
        factory.setJpaVendorAdapter(vendor);
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.jdbc.time_zone", "UTC"));
        try {
            factory.afterPropertiesSet();
            var entityManagerFactory = Objects.requireNonNull(factory.getObject());
            var entities = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
            var repositories = new JpaRepositoryFactory(entities);
            create(request, repositories.getRepository(AppUserRepository.class),
                    repositories.getRepository(AppRoleRepository.class), entities,
                    new SecurityConfig().passwordEncoder(), new UtcTimeProvider(Clock.systemUTC()),
                    new JpaTransactionManager(entityManagerFactory));
        } finally {
            factory.destroy();
        }
    }

    static void create(AdminBootstrapRequest request, AppUserRepository users, AppRoleRepository roles,
            EntityManager entities, PasswordEncoder encoder, UtcTimeProvider time, PlatformTransactionManager manager) {
        var transaction = new TransactionTemplate(manager);
        transaction.setTimeout(30);
        transaction.executeWithoutResult(status -> {
            if (!"computer_store_dev".equals(entities.createNativeQuery("SELECT DATABASE()").getSingleResult())) {
                throw new Stop("Provisioning stopped: unexpected connected schema.");
            }
            var tables = (Number) entities.createNativeQuery("""
                    SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = 'computer_store_dev'
                      AND TABLE_NAME IN ('app_user', 'user_role') AND ENGINE = 'InnoDB'
                    """).getSingleResult();
            if (tables.intValue() != 2) throw new Stop("Provisioning stopped: transactional identity tables required.");

            var role = roles.findByCode("MANAGER").filter(found -> "MANAGER".equals(found.getCode()))
                    .orElseThrow(() -> new Stop("Provisioning stopped: MANAGER role missing."));
            if (users.existsByUsername(request.username())) {
                throw new Stop("Provisioning stopped: username already exists.");
            }

            var now = time.now();
            var user = BeanUtils.instantiateClass(AppUser.class);
            user.setUsername(request.username());
            user.setFullName(request.fullName());
            user.setPasswordHash(request.encodePassword(encoder));
            user.setActive(true);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            entities.persist(user);

            var assignment = BeanUtils.instantiateClass(UserRole.class);
            assignment.setUser(user);
            assignment.setRole(role);
            assignment.setAssignedAt(now);
            entities.persist(assignment);
            // Surface concurrent UNIQUE conflicts and link failures before returning success.
            entities.flush();
        });
    }

    private static final class Stop extends RuntimeException {
        private Stop(String message) { super(message); }
    }
}
