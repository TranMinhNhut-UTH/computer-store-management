package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapException;
import com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapRequest;
import com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapService;
import com.computerstore.computer_store_backend.identity.bootstrap.BootstrapDatabaseTarget;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.repository.AppRoleRepository;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.repository.UserRoleRepository;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import java.io.Console;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import static com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapException.Reason.*;

/** Separate, opt-in CLI. No SpringApplication, component scan, runner, HTTP server or Flyway. */
public final class LocalAdminBootstrap {
    private LocalAdminBootstrap() { }

    public static void main(String[] args) {
        Console console = System.console();
        Input input = console == null ? null : new Input() {
            public String line(String prompt) { return console.readLine("%s", prompt); }
            public char[] password(String prompt) { return console.readPassword("%s", prompt); }
        };
        int exitCode = run(args, input, System.getenv(), LocalAdminBootstrap::execute, System.out);
        if (exitCode != 0) System.exit(exitCode);
    }

    interface Input {
        String line(String prompt);
        char[] password(String prompt);
    }

    @FunctionalInterface interface Action {
        AdminBootstrapService.Result create(BootstrapDatabaseTarget database, AdminBootstrapRequest request);
    }

    static int run(String[] args, Input input, Map<String, String> environment, Action action, PrintStream output) {
        if (args.length == 0) {
            output.println("Admin bootstrap disabled. Explicit --bootstrap-admin is required.");
            return 0;
        }
        if (args.length != 1 || !"--bootstrap-admin".equals(args[0]) || input == null) {
            output.println("Admin bootstrap stopped. Use only --bootstrap-admin in an interactive terminal.");
            return 1;
        }
        char[] password = null, confirmation = null;
        try {
            var database = BootstrapDatabaseTarget.fromEnvironment(environment);
            if (!"computer_store_dev".equals(input.line("Confirm target database (computer_store_dev): "))) {
                throw new AdminBootstrapException(UNSAFE_DATABASE_TARGET);
            }
            String username = input.line("CSM username: ");
            String fullName = input.line("Employee full name (required): ");
            password = input.password("CSM password (hidden; distinct from MySQL password): ");
            confirmation = input.password("Confirm CSM password (hidden): ");
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                throw new AdminBootstrapException(INVALID_INPUT);
            }
            try (var request = new AdminBootstrapRequest(username, fullName, password)) {
                if (request.passwordEquals(database.password())) throw new AdminBootstrapException(INVALID_INPUT);
                if (action.create(database, request) != AdminBootstrapService.Result.CREATED) {
                    throw new AdminBootstrapException(FAILED);
                }
            }
            output.println("Admin bootstrap committed. No business permissions were granted.");
            return 0;
        } catch (AdminBootstrapException safe) {
            output.println(safe.getMessage());
            return 1;
        } catch (RuntimeException ignored) {
            output.println("Admin bootstrap stopped: FAILED. Verify database state before retrying.");
            return 1;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }

    private static AdminBootstrapService.Result execute(BootstrapDatabaseTarget database, AdminBootstrapRequest request) {
        // This short-lived process deliberately suppresses framework/SQL logging before opening a connection.
        // Refuse an unexpected logging backend rather than risk emitting credentials in an exception.
        if (!(LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext logging)) {
            throw new AdminBootstrapException(FAILED);
        }
        logging.getLoggerList().forEach(logger -> logger.setLevel(ch.qos.logback.classic.Level.OFF));
        logging.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.OFF);
        java.util.logging.LogManager.getLogManager().reset();
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.OFF);

        var dataSource = new DriverManagerDataSource(database.url(), database.username(), database.password());
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(AppUser.class.getPackageName());
        var vendor = new HibernateJpaVendorAdapter();
        vendor.setGenerateDdl(false);
        vendor.setShowSql(false);
        factory.setJpaVendorAdapter(vendor);
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.show_sql", "false",
                "hibernate.format_sql", "false",
                "hibernate.use_sql_comments", "false",
                "hibernate.jdbc.time_zone", "UTC"));
        try {
            factory.afterPropertiesSet();
            var entityManagerFactory = java.util.Objects.requireNonNull(factory.getObject());
            var entities = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
            var repositories = new JpaRepositoryFactory(entities);
            var service = new AdminBootstrapService(repositories.getRepository(AppUserRepository.class),
                    repositories.getRepository(AppRoleRepository.class), repositories.getRepository(UserRoleRepository.class),
                    entities, new SecurityConfig().passwordEncoder(), new UtcTimeProvider(Clock.systemUTC()),
                    new JpaTransactionManager(entityManagerFactory));
            return service.bootstrap(true, request);
        } finally {
            factory.destroy();
        }
    }
}
