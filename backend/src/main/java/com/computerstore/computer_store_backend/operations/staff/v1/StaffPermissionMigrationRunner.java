package com.computerstore.computer_store_backend.operations.staff.v1;

import com.computerstore.computer_store_backend.operations.category.v1.V2__register_category_permissions;
import com.computerstore.computer_store_backend.operations.category.v1.V3__grant_category_permissions;
import com.computerstore.computer_store_backend.operations.product.v1.V4__register_product_permissions;
import com.computerstore.computer_store_backend.operations.product.v1.V5__grant_product_permissions;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;

/** Manual local entry point. The application does not discover the Staff SQL location. */
public final class StaffPermissionMigrationRunner {
    static final String SCHEMA = "computer_store_dev";
    static final String URL = "jdbc:mysql://127.0.0.1:3306/" + SCHEMA + "?sslMode=DISABLED";
    private static final String OPT_IN = "--staff-permission-rollout";
    private static final String APPROVAL = "--approve-iam-005";
    private static final String TARGET = "--confirm-target=" + SCHEMA;
    private static final List<Integer> CHECKSUMS = List.of(-1142478933, 947719693, 253774843,
            -1732158983, 155246154, -1416705504, 982398228, 151122022, 985142827, 1376292394, 792526816);
    private static final List<String> DESCRIPTIONS = List.of("create initial schema", "register category permissions",
            "grant category permissions", "register product permissions", "grant product permissions",
            "register supplier permissions", "grant supplier permissions",
            "register customer permissions", "grant customer permissions",
            "register staff permissions", "grant staff permissions");
    private static final Set<String> TABLES = Set.of("app_role", "permission", "role_permission", "flyway_schema_history");

    private StaffPermissionMigrationRunner() { }

    record Stage(int before, int target) { }

    static Stage stage(String[] args) {
        var flags = Arrays.asList(args);
        if (flags.size() == 4 && flags.contains(OPT_IN) && flags.contains(TARGET) && flags.contains(APPROVAL)) {
            if (flags.contains("--stage=A")) return new Stage(9, 10);
            if (flags.contains("--stage=B")) return new Stage(10, 11);
        }
        throw new IllegalArgumentException("Explicit Staff rollout, local target and one stage are required.");
    }

    static FluentConfiguration configuration(Stage stage, JavaMigration[] previous) {
        return Flyway.configure().locations("classpath:db/migration", "classpath:operations/supplier/v1",
                        "classpath:operations/customer/v1", "classpath:operations/staff/v1")
                .javaMigrations(previous).defaultSchema(SCHEMA).schemas(SCHEMA)
                .target(Integer.toString(stage.target())).createSchemas(false).baselineOnMigrate(false)
                .outOfOrder(false).cleanDisabled(true).validateOnMigrate(true).placeholderReplacement(false);
    }

    static JavaMigration[] previousMigrations() {
        var migrations = new JavaMigration[] {
            new V2__register_category_permissions(), new V3__grant_category_permissions(),
            new V4__register_product_permissions(), new V5__grant_product_permissions()
        };
        for (int index = 0; index < migrations.length; index++) {
            var migration = migrations[index];
            int version = index + 2;
            if (!Integer.toString(version).equals(migration.getVersion().getVersion())
                    || !CHECKSUMS.get(version - 1).equals(migration.getChecksum())
                    || !DESCRIPTIONS.get(version - 1).equals(migration.getDescription())) {
                throw new IllegalStateException("Unexpected Category or Product migration artifact.");
            }
        }
        return migrations;
    }

    static void verifyResolved(MigrationInfo[] entries, Stage stage) {
        if (entries.length != CHECKSUMS.size()) throw new IllegalStateException("Unexpected resolved migrations.");
        // Flyway 12.4's MigrationInfoImpl exposes descriptions through getDescription().
        // The installed descriptions were already checked against Flyway history.
        for (int index = 0; index < entries.length; index++) {
            var migration = entries[index];
            int version = index + 1;
            var expectedState = version <= stage.before() ? MigrationState.SUCCESS
                    : version == stage.target() ? MigrationState.PENDING : MigrationState.ABOVE_TARGET;
            if (migration.getVersion() == null || !Integer.toString(version).equals(migration.getVersion().getVersion())
                    || !CHECKSUMS.get(index).equals(migration.getResolvedChecksum())
                    || !DESCRIPTIONS.get(index).equals(migration.getDescription())
                    || migration.getState() != expectedState) {
                throw new IllegalStateException("Unexpected migration state before Staff rollout.");
            }
        }
    }

    private static void verifyTarget(String user, String password, Stage stage) throws SQLException {
        try (var connection = DriverManager.getConnection(URL, user, password)) {
            connection.setReadOnly(true);
            try (var statement = connection.createStatement();
                    var row = statement.executeQuery("SELECT DATABASE(), @@port, VERSION()")) {
                if (!row.next() || !SCHEMA.equals(row.getString(1)) || row.getInt(2) != 3306
                        || !row.getString(3).startsWith("8.0.")) {
                    throw new SQLException("Unexpected target.");
                }
            }
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("""
                            SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES
                            WHERE TABLE_SCHEMA = DATABASE()
                              AND TABLE_NAME IN ('app_role','permission','role_permission','flyway_schema_history')
                            """)) {
                var actual = new ArrayList<String>();
                while (rows.next()) {
                    if (!"InnoDB".equalsIgnoreCase(rows.getString(2))) throw new SQLException("Expected InnoDB.");
                    actual.add(rows.getString(1));
                }
                if (actual.size() != TABLES.size() || !actual.containsAll(TABLES)) throw new SQLException("Missing rollout tables.");
            }
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("""
                            SELECT version, checksum, success, type, description
                            FROM flyway_schema_history ORDER BY installed_rank
                            """)) {
                for (int index = 0; index < stage.before(); index++) {
                    String type = index > 0 && index < 5 ? "JDBC" : "SQL";
                    if (!rows.next() || !Integer.toString(index + 1).equals(rows.getString(1))
                            || !CHECKSUMS.get(index).equals(rows.getObject(2)) || !rows.getBoolean(3)
                            || !type.equals(rows.getString(4)) || !DESCRIPTIONS.get(index).equals(rows.getString(5))) {
                        throw new SQLException("Unexpected installed migration history.");
                    }
                }
                if (rows.next()) throw new SQLException("Unexpected additional migration history.");
            }
        }
    }

    public static void main(String[] args) {
        String phase = "arguments and local credentials";
        try {
            if (Arrays.equals(args, new String[] {"--verify-artifacts"})) {
                phase = "migration artifacts";
                previousMigrations();
                System.out.println("Category V2/V3 and Product V4/V5 artifacts match expected metadata.");
                return;
            }
            var selected = stage(args);
            phase = "migration artifacts";
            var previous = previousMigrations();
            phase = "local credentials";
            var user = System.getenv("DB_USERNAME");
            var password = System.getenv("DB_PASSWORD");
            if (user == null || user.isBlank() || password == null || password.isBlank()) {
                throw new IllegalArgumentException("Local MySQL credentials are required.");
            }
            phase = "JDBC target and installed history";
            verifyTarget(user, password, selected);
            phase = "Flyway migration resolution";
            var flyway = configuration(selected, previous).dataSource(URL, user, password).load();
            verifyResolved(flyway.info().all(), selected);
            phase = "Flyway migration execution";
            var result = flyway.migrate();
            phase = "Flyway execution result";
            if (!result.success || result.migrationsExecuted != 1
                    || !Integer.toString(selected.target()).equals(result.migrations.getFirst().version)) {
                throw new IllegalStateException("Expected exactly the requested migration.");
            }
            System.out.println("Staff V" + selected.target() + " applied. Run the live read-back before proceeding.");
        } catch (Exception failure) {
            // Do not include JDBC or Flyway messages. They can contain connection information.
            System.err.println("Staff rollout stopped during " + phase
                    + ". Inspect the target and history before any retry or repair.");
            System.exit(1);
        }
    }
}
