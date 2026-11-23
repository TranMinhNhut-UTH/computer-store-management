package com.computerstore.computer_store_backend.operations.session.v1;

import com.computerstore.computer_store_backend.operations.category.v1.V2__register_category_permissions;
import com.computerstore.computer_store_backend.operations.category.v1.V3__grant_category_permissions;
import com.computerstore.computer_store_backend.operations.product.v1.V4__register_product_permissions;
import com.computerstore.computer_store_backend.operations.product.v1.V5__grant_product_permissions;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Explicit local-only rollout. The application does not scan this migration location. */
public final class SessionVersionMigrationRunner {
    private static final String SCHEMA = "computer_store_dev";
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/" + SCHEMA;
    private static final String VERSION = "12";
    private static final List<Integer> CHECKSUMS = List.of(-1142478933, 947719693, 253774843,
            -1732158983, 155246154, -1416705504, 982398228, 151122022, 985142827,
            1376292394, 792526816, -710087236);

    private SessionVersionMigrationRunner() {}

    public static void main(String[] args) {
        String phase = "approval, local target and credentials";
        try {
            requireApproval(args, System.getenv("DB_URL"));
            var source = new DriverManagerDataSource(URL, credential("DB_USERNAME"), credential("DB_PASSWORD"));
            phase = "connected target";
            verifyTarget(source);
            phase = "V1–V11 history, V12 execution and post-verification";
            apply(source, SCHEMA);
            System.out.println("PASS: V12 applied and verified (checksum -710087236).");
        } catch (Exception failure) {
            System.err.println("P01 rollout stopped during " + phase + ".");
            System.exit(1);
        }
    }

    static void requireApproval(String[] args, String url) {
        var flags = Arrays.asList(args);
        if (flags.size() != 2 || !flags.contains("--apply-p01-session-version")
                || !flags.contains("--confirm-target=computer_store_dev") || !URL.equals(url)) {
            throw new IllegalArgumentException("Explicit P01 approval and the exact local target are required.");
        }
    }

    private static String credential(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Local credentials are required.");
        return value;
    }

    private static void verifyTarget(DataSource source) throws SQLException {
        try (var connection = source.getConnection()) {
            connection.setReadOnly(true);
            try (var statement = connection.createStatement();
                    var target = statement.executeQuery("SELECT DATABASE(), @@port, VERSION()")) {
                require(target.next() && SCHEMA.equals(target.getString(1)) && target.getInt(2) == 3306
                        && target.getString(3).startsWith("8.0."), "Unexpected target.");
            }
        }
    }

    // The CLI passes only the verified local target. Tests exercise the same migration on a disposable source.
    static void apply(DataSource source, String schema) throws SQLException {
        var flyway = Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema)
                .locations("classpath:db/migration", "classpath:operations/supplier/v1",
                        "classpath:operations/customer/v1", "classpath:operations/staff/v1",
                        "classpath:operations/session/v1")
                .javaMigrations(new V2__register_category_permissions(), new V3__grant_category_permissions(),
                        new V4__register_product_permissions(), new V5__grant_product_permissions())
                .target(VERSION).createSchemas(false).baselineOnMigrate(false).outOfOrder(false)
                .cleanDisabled(true).validateOnMigrate(true).placeholderReplacement(false).load();

        verifyHistory(flyway, MigrationState.PENDING);
        try (var connection = source.getConnection()) {
            connection.setReadOnly(true);
            try (var statement = connection.createStatement();
                    var before = statement.executeQuery("SELECT DATABASE(), COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='session_version'")) {
                require(before.next() && schema.equals(before.getString(1)) && before.getInt(2) == 0,
                        "Unexpected account schema before V12.");
            }
        }
        var result = flyway.migrate();
        require(result.success && result.migrationsExecuted == 1 && VERSION.equals(result.migrations.getFirst().version),
                "Expected exactly V12.");
        verifyHistory(flyway, MigrationState.SUCCESS);
        flyway.validate();
        try (var connection = source.getConnection()) {
            connection.setReadOnly(true);
            try (var statement = connection.createStatement();
                    var metadata = statement.executeQuery("""
                            SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS
                            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='session_version'
                            """)) {
                require(metadata.next() && "bigint".equals(metadata.getString(1))
                        && "NO".equals(metadata.getString(2)) && "0".equals(metadata.getString(3)),
                        "Unexpected version column.");
            }
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM app_user WHERE session_version <> 0")) {
                require(rows.next() && rows.getLong(1) == 0, "Unexpected versions after V12.");
            }
        }
    }

    private static void verifyHistory(Flyway flyway, MigrationState v12State) {
        var entries = flyway.info().all();
        require(entries.length == CHECKSUMS.size(), "Unexpected Flyway history.");
        for (int i = 0; i < entries.length; i++) {
            var migration = entries[i];
            var state = i == entries.length - 1 ? v12State : MigrationState.SUCCESS;
            require(migration.getVersion() != null && Integer.toString(i + 1).equals(migration.getVersion().getVersion())
                    && CHECKSUMS.get(i).equals(migration.getResolvedChecksum())
                    && (state != MigrationState.SUCCESS || CHECKSUMS.get(i).equals(migration.getAppliedChecksum()))
                    && migration.getState() == state, "Unexpected migration history or artifact.");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
