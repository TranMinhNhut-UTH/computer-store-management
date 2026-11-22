package com.computerstore.computer_store_backend.operations.product.v1;

import com.computerstore.computer_store_backend.operations.category.v1.V2__register_category_permissions;
import com.computerstore.computer_store_backend.operations.category.v1.V3__grant_category_permissions;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.migration.JavaMigration;

/** Standalone local runner. Spring Boot does not register these migrations. */
public final class ProductPermissionMigrationRunner {
    private ProductPermissionMigrationRunner() { }
    enum Stage {
        A(4), B(5);
        final int version;
        Stage(int version) { this.version = version; }
    }

    static Stage stage(String[] args) {
        if (args == null || args.length != 3) throw invalidOptIn();
        var flags = new HashSet<>(Arrays.asList(args));
        if (flags.size() != 3 || !flags.contains("--product-permission-rollout")
                || !flags.contains("--confirm-target=computer_store_dev")) throw invalidOptIn();
        if (flags.contains("--stage=A")) return Stage.A;
        if (flags.contains("--stage=B")) return Stage.B;
        throw invalidOptIn();
    }

    private static IllegalArgumentException invalidOptIn() {
        return new IllegalArgumentException("Explicit Product rollout, stage and target confirmation are required.");
    }

    static FluentConfiguration configuration(Stage stage) {
        if (stage == null) throw invalidOptIn();
        var v2 = new V2__register_category_permissions();
        var v3 = new V3__grant_category_permissions();
        var v4 = new V4__register_product_permissions();
        // V2/V3 must already be applied. Registration lets Flyway validate their checksums.
        JavaMigration[] migrations = stage == Stage.A ? new JavaMigration[]{v2, v3, v4}
                : new JavaMigration[]{v2, v3, v4, new V5__grant_product_permissions()};
        return Flyway.configure().locations("classpath:db/migration").javaMigrations(migrations)
                .callbacks(new SelectedVersionOnly(stage.version))
                .defaultSchema(FrozenProductRolloutV1.SCHEMA).schemas(FrozenProductRolloutV1.SCHEMA)
                .createSchemas(false).baselineOnMigrate(false).outOfOrder(false).cleanDisabled(true)
                .validateOnMigrate(true).executeInTransaction(true).group(false).target(Integer.toString(stage.version));
    }

    /** Prevent any predecessor from being executed if history changes between preflight and Flyway. */
    private record SelectedVersionOnly(int version) implements Callback {
        @Override public boolean supports(Event event, Context context) { return event == Event.BEFORE_EACH_MIGRATE; }
        @Override public boolean canHandleInTransaction(Event event, Context context) { return true; }
        @Override public String getCallbackName() { return "product-selected-version-only"; }
        @Override public void handle(Event event, Context context) {
            var migration = context.getMigrationInfo();
            if (migration == null || migration.getVersion() == null
                    || !Integer.toString(version).equals(migration.getVersion().toString())) {
                throw new FlywayException("Only the selected Product migration may execute.");
            }
        }
    }

    static void validateEnvironment(Map<String, String> environment) throws SQLException {
        FrozenProductRolloutV1.verifyUrl(environment.get("DB_URL"));
        for (var key : new String[]{"DB_USERNAME", "DB_PASSWORD"}) {
            if (environment.get(key) == null || environment.get(key).isBlank()) {
                throw new IllegalArgumentException("Local database credentials must be supplied through the environment.");
            }
        }
    }

    public static void main(String[] args) {
        try {
            Stage stage = stage(args);
            var env = System.getenv();
            validateEnvironment(env);
            String url = env.get("DB_URL"), username = env.get("DB_USERNAME"), password = env.get("DB_PASSWORD");
            verify(url, username, password, stage.version, false);
            configuration(stage).dataSource(url, username, password).load().migrate();
            verify(url, username, password, stage.version, true);
            System.out.println("Product permission stage " + stage + " verified.");
        } catch (Exception failure) {
            // Do not print driver messages, SQL or credentials.
            System.err.println("Product rollout stopped. Inspect target, history and data before retrying. No automatic repair.");
            System.exit(1);
        }
    }

    private static void verify(String url, String username, String password, int version, boolean applied) throws SQLException {
        try (var connection = DriverManager.getConnection(url, username, password)) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try { FrozenProductRolloutV1.verifyState(connection, version, applied); }
            finally { connection.rollback(); }
        }
    }
}
