package com.computerstore.computer_store_backend.operations.category.v1;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;

/** Plain Java entrypoint, never SpringApplication/CommandLineRunner/component. No default stage. */
public final class CategoryPermissionMigrationRunner {
    private CategoryPermissionMigrationRunner() { }
    enum Stage { A, B }

    static Stage stage(String[] args) {
        if (args == null || args.length != 3 || new HashSet<>(Arrays.asList(args)).size() != 3
                || !Arrays.asList(args).contains("--category-permission-rollout")
                || !Arrays.asList(args).contains("--confirm-target=computer_store_dev")) {
            throw new IllegalArgumentException("Explicit Category rollout, stage and target confirmation are required.");
        }
        if (Arrays.asList(args).contains("--stage=A")) return Stage.A;
        if (Arrays.asList(args).contains("--stage=B")) return Stage.B;
        throw new IllegalArgumentException("Exactly one explicit stage A or B is required.");
    }

    static FluentConfiguration configuration(Stage stage) {
        if (stage == null) throw new IllegalArgumentException("Stage is required.");
        JavaMigration[] migrations = stage == Stage.A ? new JavaMigration[]{new V2__register_category_permissions()}
                : new JavaMigration[]{new V2__register_category_permissions(), new V3__grant_category_permissions()};
        return Flyway.configure().locations("classpath:db/migration").javaMigrations(migrations)
                .defaultSchema(FrozenCategoryRolloutV1.SCHEMA).schemas(FrozenCategoryRolloutV1.SCHEMA)
                .createSchemas(false).baselineOnMigrate(false).outOfOrder(false).cleanDisabled(true)
                .validateOnMigrate(true).executeInTransaction(true).group(false).target(stage == Stage.A ? "2" : "3");
    }

    static void validateEnvironment(Map<String, String> env) throws SQLException {
        FrozenCategoryRolloutV1.verifyUrl(env.get("DB_URL"));
        if (env.get("DB_USERNAME") == null || env.get("DB_USERNAME").isBlank()
                || env.get("DB_PASSWORD") == null || env.get("DB_PASSWORD").isBlank()) {
            throw new IllegalArgumentException("Local database credentials must be supplied through the environment.");
        }
    }

    public static void main(String[] args) {
        try {
            var selected = stage(args); // Validate opt-in BEFORE reading credentials or connecting.
            var environment = System.getenv();
            validateEnvironment(environment);
            var url = environment.get("DB_URL");
            var username = environment.get("DB_USERNAME");
            var password = environment.get("DB_PASSWORD");
            // Prevent Flyway from creating/applying V1 on an empty or unexpected target.
            try (var connection = DriverManager.getConnection(url, username, password)) {
                connection.setReadOnly(true);
                FrozenCategoryRolloutV1.verifyTargetAndHistory(connection, selected == Stage.B);
            }
            configuration(selected).dataSource(url, username, password).load().migrate();
            System.out.println("Category permission stage " + selected + " completed; verify database state before continuing.");
        } catch (Exception failure) {
            // Driver/Flyway exception messages can include URL or connection information.
            System.err.println("Category rollout stopped. No automatic retry/repair; inspect the target and history securely.");
            System.exit(1);
        }
    }
}
