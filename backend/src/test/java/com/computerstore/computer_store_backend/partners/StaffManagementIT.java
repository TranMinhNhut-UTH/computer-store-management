package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.ComputerStoreBackendApplication;
import com.computerstore.computer_store_backend.operations.staff.v1.StaffPermissionMigrationRunner;
import com.computerstore.computer_store_backend.operations.session.v1.SessionVersionMigrationRunner;
import com.computerstore.computer_store_backend.partners.SupplierPermissionRolloutIT.DisposableMysql;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class StaffManagementIT {
    static final String PASSWORD = "disposable-staff-fixture";
    private static final String STAFF = "/api/v1/employees";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> CODES = List.of("EMPLOYEE_CREATE", "EMPLOYEE_LOCK", "EMPLOYEE_READ",
            "EMPLOYEE_ROLE_ASSIGN", "EMPLOYEE_UNLOCK", "EMPLOYEE_UPDATE");

    @TempDir Path directory;

    @Test void rolloutAndStaffWorkflowsUseIsolatedMysqlWithRealSessions() throws Exception {
        var home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to the Windows MySQL 8 installation");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var db = prepare(mysql);
            try (var app = runApp(mysql, 0)) {
                int port = ((WebServerApplicationContext) app).getWebServer().getPort();
                assertWorkflow(db, port);
                assertConcurrentAdminProtection(db, port);
            }
        }
    }

    static JdbcTemplate prepare(DisposableMysql mysql) throws Exception {
        var db = ReportsOverviewIT.initialize(mysql);
        var names = List.of("register category permissions", "grant category permissions", "register product permissions",
                "grant product permissions", "register supplier permissions", "grant supplier permissions",
                "register customer permissions", "grant customer permissions");
        var checksums = List.of(947719693, 253774843, -1732158983, 155246154, -1416705504, 982398228, 151122022, 985142827);
        for (int i = 0; i < names.size(); i++) {
            int version = i + 2;
            db.update("""
                    INSERT INTO flyway_schema_history
                    (installed_rank,version,description,type,script,checksum,installed_by,execution_time,success)
                    VALUES (?,?,?,?,?,?, 'fixture', 0, TRUE)
                    """, version, Integer.toString(version), names.get(i), version <= 5 ? "JDBC" : "SQL",
                    "fixture-" + version, checksums.get(i));
        }
        fixture(db, "admin.one", "ADMIN");
        fixture(db, "admin.two", "ADMIN");
        fixture(db, "manager.fixture", "MANAGER");
        fixture(db, "sales.fixture", "SALES_STAFF");
        fixture(db, "warehouse.fixture", "WAREHOUSE_STAFF");

        assertThrows(DataAccessException.class, () -> db.execute(script("V11__grant_staff_permissions.sql")));
        assertEquals(0, count(db, "role_permission"));
        assertStage(mysql, "A", "10");
        assertEquals(CODES, db.queryForList("SELECT code FROM permission ORDER BY code", String.class));
        assertEquals(0, count(db, "role_permission"));
        db.update("UPDATE app_user SET is_active = FALSE WHERE username LIKE 'admin.%'");
        assertThrows(DataAccessException.class, () -> db.execute(script("V11__grant_staff_permissions.sql")));
        assertEquals(0, count(db, "role_permission"));
        db.update("UPDATE app_user SET is_active = TRUE WHERE username LIKE 'admin.%'");
        assertStage(mysql, "B", "11");
        assertEquals(CODES, db.queryForList("""
                SELECT p.code FROM role_permission rp JOIN permission p ON p.id=rp.permission_id
                JOIN app_role r ON r.id=rp.role_id WHERE r.code='ADMIN' ORDER BY p.code
                """, String.class));
        assertEquals(6, count(db, "role_permission"));
        assertEquals(List.of("10:1376292394:1", "11:792526816:1"), db.queryForList("""
                SELECT CONCAT(version, ':', checksum, ':', success) FROM flyway_schema_history
                WHERE version IN ('10','11') ORDER BY installed_rank
                """, String.class));
        var source = mysql.dataSource(ReportsOverviewIT.SCHEMA);
        // An unexpected predecessor history must fail before touching the account schema.
        db.update("UPDATE flyway_schema_history SET checksum = 0 WHERE version = '11'");
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(
                SessionVersionMigrationRunner.class, "apply", source, ReportsOverviewIT.SCHEMA));
        assertEquals(0, db.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'session_version'
                """, Integer.class));
        db.update("UPDATE flyway_schema_history SET checksum = 792526816 WHERE version = '11'");
        ReflectionTestUtils.invokeMethod(SessionVersionMigrationRunner.class, "apply", source, ReportsOverviewIT.SCHEMA);
        assertEquals(-710087236, db.queryForObject(
                "SELECT checksum FROM flyway_schema_history WHERE version = '12' AND success = TRUE", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM app_user WHERE session_version <> 0", Integer.class));
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(
                SessionVersionMigrationRunner.class, "apply", source, ReportsOverviewIT.SCHEMA));
        return db;
    }

    private static void assertStage(DisposableMysql mysql, String name, String version) {
        var stage = ReflectionTestUtils.invokeMethod(StaffPermissionMigrationRunner.class, "stage", (Object)
                new String[]{"--staff-permission-rollout", "--approve-iam-005", "--confirm-target=computer_store_dev", "--stage=" + name});
        JavaMigration[] previous = ReflectionTestUtils.invokeMethod(StaffPermissionMigrationRunner.class, "previousMigrations");
        FluentConfiguration configuration = ReflectionTestUtils.invokeMethod(
                StaffPermissionMigrationRunner.class, "configuration", stage, previous);
        var flyway = configuration.dataSource(mysql.dataSource(ReportsOverviewIT.SCHEMA))
                .defaultSchema(ReportsOverviewIT.SCHEMA).schemas(ReportsOverviewIT.SCHEMA).load();
        ReflectionTestUtils.invokeMethod(StaffPermissionMigrationRunner.class, "verifyResolved", flyway.info().all(), stage);
        var applied = flyway.migrate();
        assertEquals(1, applied.migrationsExecuted);
        assertEquals(version, applied.migrations.getFirst().version);
    }

    static ConfigurableApplicationContext runApp(DisposableMysql mysql, int port) {
        return SpringApplication.run(ComputerStoreBackendApplication.class,
                "--spring.datasource.url=" + mysql.dataSource(ReportsOverviewIT.SCHEMA).getURL(),
                "--spring.datasource.username=root", "--spring.datasource.password=",
                "--spring.flyway.enabled=false", "--spring.jpa.hibernate.ddl-auto=validate",
                "--csm.staff-management.enabled=true", "--server.address=127.0.0.1", "--server.port=" + port,
                "--logging.level.root=WARN");
    }

    private static void assertWorkflow(JdbcTemplate db, int port) throws Exception {
        var anonymous = new Session(port);
        expect(401, anonymous.read(STAFF));
        for (var username : List.of("manager.fixture", "sales.fixture", "warehouse.fixture")) {
            var other = new Session(port);
            other.login(username);
            expect(403, other.read(STAFF));
            expect(403, other.write("POST", STAFF, create("forbidden", null, List.of("MANAGER"))));
        }

        var admin = new Session(port);
        var adminId = admin.login("admin.one").get("userId").asString();
        var profile = expect(200, admin.read("/api/v1/auth/ui-capabilities"));
        assertEquals("EMPLOYEE_READ", profile.get("permissions").get(0).asString());
        var empty = expect(200, admin.read(STAFF + "?q=missing"));
        assertEquals(0, empty.get("totalElements").asInt());
        assertEquals(0, empty.get("items").size());
        var secondPage = expect(200, admin.read(STAFF + "?page=1&size=1"));
        assertEquals(1, secondPage.get("page").asInt());
        assertEquals(1, secondPage.get("size").asInt());
        assertEquals(1, secondPage.get("items").size());
        assertEquals("admin.two", secondPage.get("items").get(0).get("username").asString());
        var beyondLastPage = expect(200, admin.read(STAFF + "?page=5&size=1"));
        assertEquals(5, beyondLastPage.get("page").asInt());
        assertEquals(5, beyondLastPage.get("totalPages").asInt());
        assertEquals(0, beyondLastPage.get("items").size());
        assertEquals("VALIDATION_ERROR", expect(400, admin.read(STAFF + "?size=101")).get("code").asString());
        assertEquals("VALIDATION_ERROR", expect(400, admin.read(STAFF + "?page=2147483647&size=2")).get("code").asString());
        expect(403, admin.send("POST", STAFF, create("csrf-check", null, List.of("MANAGER")), null));

        db.update("DELETE rp FROM role_permission rp JOIN permission p ON p.id=rp.permission_id WHERE p.code='EMPLOYEE_ROLE_ASSIGN'");
        expect(403, admin.write("POST", STAFF, create("no-assignment", null, List.of("MANAGER"))));
        db.update("""
                INSERT INTO role_permission (role_id,permission_id,created_at)
                SELECT r.id,p.id,UTC_TIMESTAMP(6) FROM app_role r,permission p
                WHERE r.code='ADMIN' AND p.code='EMPLOYEE_ROLE_ASSIGN'
                """);
        expect(400, admin.write("POST", STAFF, create("unknown-role", null, List.of("CUSTOMER"))));
        expect(400, admin.write("POST", STAFF, create("duplicated-role", null, List.of("MANAGER", "MANAGER"))));
        expect(400, admin.write("POST", STAFF, Map.of("username", "long-password", "fullName", "Fixture",
                "password", "é".repeat(37), "roles", List.of("MANAGER"))));

        long before = count(db, "app_user");
        db.execute("CREATE TRIGGER fixture_fail_assign BEFORE INSERT ON user_role FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture rollback'");
        try { expect(500, admin.write("POST", STAFF, create("rollback.fixture", null, List.of("MANAGER")))); }
        finally { db.execute("DROP TRIGGER fixture_fail_assign"); }
        assertEquals(before, count(db, "app_user"));

        var createdResponse = admin.write("POST", STAFF, create(" New.Staff ", "Fixture@Example.Test", List.of("SALES_STAFF")));
        var created = expect(201, createdResponse);
        var id = created.get("id").asString();
        assertEquals(STAFF + "/" + id, createdResponse.headers().firstValue("location").orElseThrow());
        assertEquals("new.staff", created.get("username").asString());
        assertEquals("fixture@example.test", created.get("email").asString());
        assertTrue(created.get("active").asBoolean());
        assertFalse(createdResponse.body().contains("password"));
        assertFalse(createdResponse.body().contains(PASSWORD));
        expect(409, admin.write("POST", STAFF, create("NEW.STAFF", null, List.of("MANAGER"))));
        expect(409, admin.write("POST", STAFF, create("other.staff", "fixture@example.test", List.of("MANAGER"))));

        var staff = new Session(port);
        staff.login("new.staff");
        var secondStaffSession = new Session(port);
        secondStaffSession.login("new.staff");
        String me = "/api/v1/auth/me";
        expect(200, staff.read(me));
        expect(403, staff.read(STAFF));
        expect(400, admin.write("PUT", STAFF + "/" + id, Map.of("fullName", "Fixture", "active", false)));
        var updated = expect(200, admin.write("PUT", STAFF + "/" + id, Map.of("fullName", "Updated fixture")));
        assertEquals("Updated fixture", updated.get("fullName").asString());
        assertTrue(updated.get("active").asBoolean());
        expect(200, staff.read(me));
        var employeePath = STAFF + "/" + id;
        var changeRoles = Map.of("roles", List.of("ADMIN", "SALES_STAFF"));
        db.execute("""
                CREATE TRIGGER fixture_fail_revision BEFORE UPDATE ON app_user FOR EACH ROW
                BEGIN
                    IF NEW.session_version <> OLD.session_version THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture rollback';
                    END IF;
                END
                """);
        try {
            expect(500, admin.write("PUT", employeePath + "/roles", changeRoles));
            expect(500, admin.write("POST", employeePath + "/lock", null));
            expect(200, staff.read(me));
            expect(403, staff.read(STAFF));
            assertEquals(1, expect(200, admin.read(employeePath)).get("roles").size());
        } finally {
            db.execute("DROP TRIGGER fixture_fail_revision");
        }
        expect(403, admin.write("POST", STAFF + "/" + adminId + "/lock", null));
        expect(403, admin.write("PUT", STAFF + "/" + adminId + "/roles", Map.of("roles", List.of("MANAGER"))));
        assertFalse(expect(200, admin.write("POST", STAFF + "/" + id + "/lock", null)).get("active").asBoolean());
        expect(401, staff.read("/api/v1/auth/me"));
        expect(401, staff.read("/api/v1/auth/ui-capabilities"));
        expect(401, new Session(port).write("POST", "/api/v1/auth/login", Map.of("username", "new.staff", "password", PASSWORD)));
        assertTrue(expect(200, admin.write("POST", STAFF + "/" + id + "/unlock", null)).get("active").asBoolean());
        expect(401, staff.read(me));
        expect(401, secondStaffSession.read(me));
        expect(401, staff.send("PUT", employeePath, Map.of("fullName", "rejected"), null));
        expect(200, admin.read(me));
        var fresh = new Session(port);
        fresh.login("new.staff");
        expect(200, fresh.read(me));
        expect(403, fresh.read(STAFF));
        var assigned = expect(200, admin.write("PUT", employeePath + "/roles", changeRoles));
        assertEquals(2, assigned.get("roles").size());
        expect(401, fresh.read(me));
        expect(401, fresh.read(STAFF));
        expect(401, staff.read(STAFF));
        var authorized = new Session(port);
        authorized.login("new.staff");
        expect(200, authorized.read(STAFF));
        expect(200, admin.write("PUT", employeePath + "/roles", changeRoles));
        expect(200, authorized.read(me)); // Assigning the identical role set does not revoke.
        expect(403, authorized.send("POST", "/api/v1/auth/logout", null, null));
        expect(204, authorized.write("POST", "/api/v1/auth/logout", null));
        expect(401, authorized.read(me));
        // Restore one non-admin fixture so the final two-ADMIN race checks the last-active invariant.
        expect(200, admin.write("PUT", employeePath + "/roles", Map.of("roles", List.of("SALES_STAFF"))));
        expect(403, admin.write("DELETE", STAFF + "/" + id, null));
        assertEquals("new.staff", expect(200, admin.read(STAFF + "/" + id)).get("username").asString());

        var secondAdmin = expect(201, admin.write("POST", STAFF, create("another.admin", null, List.of("ADMIN"))));
        assertEquals("ADMIN", secondAdmin.get("roles").get(0).asString());
        expect(200, admin.write("POST", STAFF + "/" + secondAdmin.get("id").asString() + "/lock", null));
    }

    private static void assertConcurrentAdminProtection(JdbcTemplate db, int port) throws Exception {
        var first = new Session(port);
        var second = new Session(port);
        var firstId = first.login("admin.one").get("userId").asString();
        var secondId = second.login("admin.two").get("userId").asString();
        var one = first.sendAsync(first.request("POST", STAFF + "/" + secondId + "/lock", null, first.csrf()));
        var two = second.sendAsync(second.request("PUT", STAFF + "/" + firstId + "/roles",
                Map.of("roles", List.of("MANAGER")), second.csrf()));
        CompletableFuture.allOf(one, two).get(15, TimeUnit.SECONDS);
        var statuses = List.of(one.join().statusCode(), two.join().statusCode()).stream().sorted().toList();
        assertTrue(statuses.equals(List.of(200, 401)) || statuses.equals(List.of(200, 403)), statuses.toString());
        var winner = one.join().statusCode() == 200 ? first : second;
        var revoked = one.join().statusCode() == 200 ? second : first;
        expect(200, winner.read("/api/v1/auth/me"));
        expect(401, revoked.read("/api/v1/auth/me"));
        assertEquals(1, db.queryForObject("""
                SELECT COUNT(*) FROM app_user u JOIN user_role ur ON ur.user_id=u.id
                JOIN app_role r ON r.id=ur.role_id WHERE r.code='ADMIN' AND u.is_active=TRUE
                """, Integer.class));
    }

    static void fixture(JdbcTemplate db, String username, String role) {
        db.update("INSERT INTO app_user (username,password_hash,full_name,created_at,updated_at) VALUES (?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                username, new BCryptPasswordEncoder().encode(PASSWORD), username);
        db.update("INSERT INTO user_role (user_id,role_id,assigned_at) SELECT u.id,r.id,UTC_TIMESTAMP(6) FROM app_user u,app_role r WHERE u.username=? AND r.code=?",
                username, role);
    }

    private static java.util.Map<String, Object> create(String username, String email, List<String> roles) {
        var values = new java.util.HashMap<String, Object>();
        values.put("username", username); values.put("email", email); values.put("fullName", "Staff fixture");
        values.put("password", PASSWORD); values.put("roles", roles);
        return values;
    }

    private static String script(String name) throws Exception {
        return new ClassPathResource("operations/staff/v1/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    private static long count(JdbcTemplate db, String table) {
        return db.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    static JsonNode expect(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), "Unexpected HTTP response");
        return response.body().isEmpty() ? null : JSON.readTree(response.body());
    }

    static final class Session {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).connectTimeout(Duration.ofSeconds(5)).build();
        final String base;
        Session(int port) { base = "http://127.0.0.1:" + port; }

        JsonNode login(String username) throws Exception {
            return login(username, PASSWORD);
        }

        JsonNode login(String username, String password) throws Exception {
            return expect(200, write("POST", "/api/v1/auth/login", Map.of("username", username, "password", password)));
        }

        JsonNode csrf() throws Exception { return expect(200, read("/api/v1/auth/csrf")); }
        HttpResponse<String> read(String path) throws Exception { return send("GET", path, null, null); }
        HttpResponse<String> write(String method, String path, Object body) throws Exception {
            return send(method, path, body, csrf());
        }

        HttpRequest request(String method, String path, Object body, JsonNode csrf) {
            var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
            if (csrf != null) builder.header(csrf.get("headerName").asString(), csrf.get("token").asString());
            if (body != null) builder.header("Content-Type", "application/json");
            return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
        }

        HttpResponse<String> send(String method, String path, Object body, JsonNode csrf) throws Exception {
            return client.send(request(method, path, body, csrf), HttpResponse.BodyHandlers.ofString());
        }

        CompletableFuture<HttpResponse<String>> sendAsync(HttpRequest request) {
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
