package com.computerstore.computer_store_backend.partners;

import com.mysql.cj.jdbc.MysqlDataSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** Opt in with -Dtest=SupplierPermissionRolloutIT and -Dcsm.mysql.home=<Windows MySQL 8 installation>. */
class SupplierPermissionRolloutIT {
    private static final String SCHEMA = "csm_supplier_rollout_it";
    private static final String LOCATION = "operations/supplier/v1";
    private static final String V6 = "V6__register_supplier_permissions.sql";
    private static final String V7 = "V7__grant_supplier_permissions.sql";
    private static final int V6_CHECKSUM = -1416705504;
    private static final int V7_CHECKSUM = 982398228;

    @TempDir Path directory;

    @Test void realMysqlRejectsConflictsAndRecordsExactSupplierRollout() throws Exception {
        String home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to the installed Windows MySQL server");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var server = new JdbcTemplate(mysql.dataSource(""));
            mysql.verifyTarget(server);
            var version = server.queryForObject("SELECT VERSION()", String.class);
            assertTrue(version.startsWith("8.0."), version);
            server.execute("CREATE DATABASE " + SCHEMA);
            var source = mysql.dataSource(SCHEMA);
            var jdbc = new JdbcTemplate(source);
            assertEquals(SCHEMA, jdbc.queryForObject("SELECT DATABASE()", String.class));

            // V1 runs verbatim. V2–V5 are test-only history markers with the released signatures.
            assertEquals(5, flyway(source, "5").migrate().migrationsExecuted);
            var baseline = history(jdbc);
            assertEquals(List.of("1:-1142478933:1", "2:947719693:1", "3:253774843:1",
                    "4:-1732158983:1", "5:155246154:1"), baseline);
            assertEquals(0, count(jdbc, "permission"));
            assertEquals(0, count(jdbc, "role_permission"));

            // Negative fixtures use the production SQL text, without recording test failures in Flyway history.
            var register = script(V6);
            var grant = script(V7);
            assertRejected(jdbc, grant);
            assertEquals(0, count(jdbc, "role_permission"));

            jdbc.update("INSERT INTO permission (code,name,created_at,updated_at) VALUES ('supplier_read','conflict',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
            var existing = jdbc.queryForList("SELECT * FROM permission");
            assertRejected(jdbc, register);
            assertEquals(existing, jdbc.queryForList("SELECT * FROM permission"));
            jdbc.update("DELETE FROM permission WHERE code = 'supplier_read'");

            jdbc.update("UPDATE flyway_schema_history SET checksum = 0 WHERE version = '5'");
            assertRejected(jdbc, register);
            assertEquals(0, count(jdbc, "permission"));
            jdbc.update("UPDATE flyway_schema_history SET checksum = 155246154 WHERE version = '5'");

            assertEquals(1, flyway(source, "6").migrate().migrationsExecuted);
            assertEquals(append(baseline, "6:" + V6_CHECKSUM + ":1"), history(jdbc));
            assertEquals(List.of("SUPPLIER_CREATE", "SUPPLIER_DEACTIVATE", "SUPPLIER_READ", "SUPPLIER_UPDATE"),
                    jdbc.queryForList("SELECT code FROM permission WHERE CAST(name AS BINARY) = CAST(code AS BINARY) AND description IS NULL ORDER BY code", String.class));
            assertEquals(4, count(jdbc, "permission"));
            assertEquals(0, count(jdbc, "role_permission"));
            assertRejected(jdbc, register);

            jdbc.update("UPDATE app_role SET code = 'WAREHOUSE_FIXTURE' WHERE code = 'WAREHOUSE_STAFF'");
            assertRejected(jdbc, grant);
            assertEquals(0, count(jdbc, "role_permission"));
            jdbc.update("UPDATE app_role SET code = 'WAREHOUSE_STAFF' WHERE code = 'WAREHOUSE_FIXTURE'");

            jdbc.update("UPDATE permission SET name = 'wrong' WHERE code = 'SUPPLIER_READ'");
            assertRejected(jdbc, grant);
            assertEquals(0, count(jdbc, "role_permission"));
            jdbc.update("UPDATE permission SET name = code WHERE code = 'SUPPLIER_READ'");

            jdbc.update("""
                    INSERT INTO role_permission (role_id, permission_id, created_at)
                    SELECT r.id, p.id, UTC_TIMESTAMP(6) FROM app_role r CROSS JOIN permission p
                    WHERE r.code = 'ADMIN' AND p.code = 'SUPPLIER_READ'
                    """);
            var conflict = jdbc.queryForList("SELECT * FROM role_permission");
            assertRejected(jdbc, grant);
            assertEquals(conflict, jdbc.queryForList("SELECT * FROM role_permission"));
            jdbc.update("DELETE FROM role_permission");

            assertEquals(1, flyway(source, "7").migrate().migrationsExecuted);
            assertEquals(append(baseline, "6:" + V6_CHECKSUM + ":1", "7:" + V7_CHECKSUM + ":1"), history(jdbc));
            assertEquals(List.of("MANAGER:SUPPLIER_CREATE", "MANAGER:SUPPLIER_DEACTIVATE", "MANAGER:SUPPLIER_READ",
                    "MANAGER:SUPPLIER_UPDATE", "WAREHOUSE_STAFF:SUPPLIER_READ"), jdbc.queryForList("""
                    SELECT CONCAT(r.code, ':', p.code) FROM role_permission rp
                    JOIN app_role r ON r.id = rp.role_id JOIN permission p ON p.id = rp.permission_id
                    ORDER BY r.code, p.code
                    """, String.class));
            assertEquals(0, count(jdbc, "user_role"));
            var grants = jdbc.queryForList("SELECT * FROM role_permission");
            assertRejected(jdbc, grant);
            assertEquals(0, flyway(source, "7").migrate().migrationsExecuted);
            assertEquals(grants, jdbc.queryForList("SELECT * FROM role_permission"));
            System.out.printf("Supplier V6/V7 verified on MySQL %s, checksums %d/%d%n", version, V6_CHECKSUM, V7_CHECKSUM);
        }
    }

    @Test void supplierRowLockSerializesDeactivationAndPurchaseDraftSelection() throws Exception {
        String home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to the installed Windows MySQL server");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var server = new JdbcTemplate(mysql.dataSource(""));
            mysql.verifyTarget(server);
            server.execute("CREATE DATABASE " + SCHEMA);
            var source = mysql.dataSource(SCHEMA);
            assertEquals(1, flyway(source, "1").migrate().migrationsExecuted);
            var jdbc = new JdbcTemplate(source);
            jdbc.update("""
                    INSERT INTO supplier (id, code, name, created_at, updated_at) VALUES
                    (1, 'DEACTIVATION-FIRST', 'Fixture one', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (2, 'DRAFT-FIRST', 'Fixture two', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    """);
            jdbc.update("""
                    INSERT INTO app_user (id, username, password_hash, full_name, created_at, updated_at)
                    VALUES (1, 'fixture', 'disabled-fixture', 'Fixture', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    """);

            try (var workers = Executors.newSingleThreadExecutor();
                    var deactivation = source.getConnection()) {
                deactivation.setAutoCommit(false);
                assertTrue(lockSupplier(deactivation, 1));
                var purchasing = workers.submit(() -> {
                    try (var draft = source.getConnection(); var statement = draft.createStatement()) {
                        statement.execute("SET SESSION innodb_lock_wait_timeout=5");
                        draft.setAutoCommit(false);
                        boolean active = lockSupplier(draft, 1);
                        draft.rollback();
                        return active;
                    }
                });
                awaitLockWait(jdbc);
                try (var update = deactivation.createStatement()) {
                    assertEquals(1, update.executeUpdate("UPDATE supplier SET is_active=FALSE WHERE id=1"));
                }
                deactivation.commit();
                assertFalse(purchasing.get(6, TimeUnit.SECONDS), "Purchasing must reject the supplier after acquiring the lock");
            }

            try (var workers = Executors.newSingleThreadExecutor(); var purchasing = source.getConnection()) {
                purchasing.setAutoCommit(false);
                assertTrue(lockSupplier(purchasing, 2));
                try (var insert = purchasing.createStatement()) {
                    insert.executeUpdate("""
                            INSERT INTO purchase (document_no, supplier_id, status, created_by, created_at, updated_at)
                            VALUES ('DRAFT-FIXTURE', 2, 'DRAFT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                            """);
                }
                var deactivation = workers.submit(() -> {
                    try (var attempt = source.getConnection(); var statement = attempt.createStatement()) {
                        statement.execute("SET SESSION innodb_lock_wait_timeout=5");
                        attempt.setAutoCommit(false);
                        assertTrue(lockSupplier(attempt, 2));
                        try (var drafts = statement.executeQuery("""
                                SELECT id FROM purchase WHERE supplier_id=2 AND status='DRAFT' LIMIT 1 FOR SHARE
                                """)) {
                            return drafts.next();
                        }
                    }
                });
                awaitLockWait(jdbc);
                purchasing.commit();
                assertTrue(deactivation.get(6, TimeUnit.SECONDS), "Deactivation must see and reject the committed draft");
            }
            assertEquals(1, jdbc.queryForObject("SELECT is_active FROM supplier WHERE id=2", Integer.class));
        }
    }

    private static boolean lockSupplier(Connection connection, long id) throws SQLException {
        try (var query = connection.prepareStatement("SELECT is_active FROM supplier WHERE id=? FOR UPDATE")) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                assertTrue(row.next());
                return row.getBoolean(1);
            }
        }
    }

    private static void awaitLockWait(JdbcTemplate jdbc) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class) > 0) return;
            Thread.sleep(30);
        }
        fail("Expected the concurrent Supplier operation to wait for the row lock");
    }

    private static void assertRejected(JdbcTemplate jdbc, String sql) {
        var failure = assertThrows(DataAccessException.class, () -> jdbc.execute(sql));
        var cause = assertInstanceOf(SQLException.class, failure.getMostSpecificCause());
        assertEquals(1242, cause.getErrorCode(), "Expected the migration's fail-fast scalar guard");
    }

    private static List<String> append(List<String> baseline, String... rows) {
        var result = new ArrayList<>(baseline);
        result.addAll(List.of(rows));
        return result;
    }

    private static List<String> history(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT CONCAT(version, ':', checksum, ':', success) FROM flyway_schema_history ORDER BY installed_rank", String.class);
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static String script(String file) throws Exception {
        return new ClassPathResource(LOCATION + "/" + file).getContentAsString(StandardCharsets.UTF_8);
    }

    private static Flyway flyway(MysqlDataSource source, String target) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration", "classpath:" + LOCATION)
                .javaMigrations(new V2__register_category_permissions(), new V3__grant_category_permissions(),
                        new V4__register_product_permissions(), new V5__grant_product_permissions())
                .defaultSchema(SCHEMA).schemas(SCHEMA).target(target).createSchemas(false)
                .baselineOnMigrate(false).outOfOrder(false).cleanDisabled(true).validateOnMigrate(true).load();
    }

    private abstract static class HistoryMarker extends BaseJavaMigration {
        private final int checksum;
        HistoryMarker(int checksum) { this.checksum = checksum; }
        @Override public Integer getChecksum() { return checksum; }
        @Override public void migrate(Context context) { }
    }

    private static final class V2__register_category_permissions extends HistoryMarker {
        V2__register_category_permissions() { super(947719693); }
    }
    private static final class V3__grant_category_permissions extends HistoryMarker {
        V3__grant_category_permissions() { super(253774843); }
    }
    private static final class V4__register_product_permissions extends HistoryMarker {
        V4__register_product_permissions() { super(-1732158983); }
    }
    private static final class V5__grant_product_permissions extends HistoryMarker {
        V5__grant_product_permissions() { super(155246154); }
    }

    private record DisposableMysql(Process process, Path data, String pipe) implements AutoCloseable {
        static DisposableMysql start(Path home, Path directory) throws Exception {
            Path data = Files.createDirectory(directory.resolve("data"));
            Path initLog = directory.resolve("initialize.log");
            Process init = launch(home, data, initLog, "--initialize-insecure");
            try {
                assertTrue(init.waitFor(60, TimeUnit.SECONDS), "Disposable MySQL initialization timed out");
                assertEquals(0, init.exitValue(), () -> readLog(initLog));
            } finally {
                if (init.isAlive()) init.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            }
            String pipe = "csm-supplier-it-" + UUID.randomUUID();
            Path log = directory.resolve("server.log");
            var mysql = new DisposableMysql(launch(home, data, log, "--skip-networking", "--named-pipe",
                    "--socket=" + pipe, "--mysqlx=OFF", "--pid-file=" + directory.resolve("server.pid")), data, pipe);
            boolean ready = false;
            try {
                var jdbc = new JdbcTemplate(mysql.dataSource(""));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                while (mysql.process.isAlive() && System.nanoTime() < deadline) {
                    try {
                        mysql.verifyTarget(jdbc);
                        ready = true;
                        return mysql;
                    } catch (org.springframework.jdbc.CannotGetJdbcConnectionException unavailable) { Thread.sleep(200); }
                }
                return fail("Disposable MySQL failed to start: " + readLog(log));
            } finally {
                if (!ready) mysql.stop();
            }
        }

        private static Process launch(Path home, Path data, Path log, String... options) throws Exception {
            Path executable = home.resolve("bin/mysqld.exe");
            assertTrue(Files.isRegularFile(executable));
            var command = new ArrayList<>(List.of(executable.toString(), "--no-defaults", "--basedir=" + home,
                    "--datadir=" + data, "--console"));
            command.addAll(List.of(options));
            return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        }

        MysqlDataSource dataSource(String schema) {
            assertTrue(schema.isEmpty() || SCHEMA.equals(schema));
            var source = new MysqlDataSource();
            String pipePath = URLEncoder.encode("\\\\.\\pipe\\" + pipe, StandardCharsets.UTF_8);
            source.setURL("jdbc:mysql://127.0.0.1/" + schema
                    + "?socketFactory=com.mysql.cj.protocol.NamedPipeSocketFactory&path=" + pipePath
                    + "&sslMode=DISABLED&connectTimeout=1000");
            source.setUser("root");
            source.setPassword(""); // Fresh disposable server, named pipe only.
            return source;
        }

        void verifyTarget(JdbcTemplate jdbc) throws Exception {
            var environment = jdbc.queryForMap("SELECT @@datadir AS datadir, @@socket AS pipe, @@skip_networking AS offline");
            assertEquals(data.toRealPath(), Path.of(environment.get("datadir").toString()).toRealPath());
            assertEquals(pipe, environment.get("pipe"));
            assertEquals("1", environment.get("offline").toString());
        }

        private void stop() throws InterruptedException {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                assertTrue(process.destroyForcibly().waitFor(10, TimeUnit.SECONDS), "Disposable MySQL did not stop");
            }
        }

        @Override public void close() throws Exception {
            try {
                if (process.isAlive()) {
                    var jdbc = new JdbcTemplate(dataSource(""));
                    verifyTarget(jdbc);
                    try { jdbc.execute("SHUTDOWN"); }
                    catch (DataAccessException disconnected) {
                        if (!process.waitFor(5, TimeUnit.SECONDS)) throw disconnected;
                    }
                }
            } finally {
                stop();
            }
        }

        private static String readLog(Path path) {
            try { return Files.readString(path); }
            catch (java.io.IOException unavailable) { return "Disposable MySQL log unavailable"; }
        }
    }
}
