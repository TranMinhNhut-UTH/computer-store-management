package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.partners.StaffManagementIT.Session;
import com.computerstore.computer_store_backend.partners.SupplierPermissionRolloutIT.DisposableMysql;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

import static com.computerstore.computer_store_backend.partners.StaffManagementIT.*;
import static org.junit.jupiter.api.Assertions.*;

class PasswordChangeIT {
    private static final String STAFF = "/api/v1/employees";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @TempDir Path directory;

    @Test void selfServiceChangeIsAtomicAndRequiresFreshAuthentication() throws Exception {
        String home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to the Windows MySQL 8 installation");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var server = new JdbcTemplate(mysql.dataSource(""));
            mysql.verifyTarget(server);
            server.execute("CREATE DATABASE " + ReportsOverviewIT.SCHEMA);
            var source = mysql.dataSource(ReportsOverviewIT.SCHEMA);
            var db = new JdbcTemplate(source);
            // Build a fresh test schema with the current column shape without invoking Flyway or V12.
            String ddl = new ClassPathResource("db/migration/V1__create_initial_schema.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            String anchor = "  last_login_at DATETIME(6) NULL,";
            assertTrue(ddl.contains(anchor));
            ddl = ddl.replace(anchor, "  session_version BIGINT NOT NULL DEFAULT 0,\n" + anchor);
            try (var connection = source.getConnection()) {
                assertEquals(ReportsOverviewIT.SCHEMA, connection.getCatalog());
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)));
            }
            fixture(db, "manager.fixture", "MANAGER");
            fixture(db, "sales.fixture", "SALES_STAFF");
            fixture(db, "admin.fixture", "ADMIN");
            for (String code : List.of("EMPLOYEE_LOCK", "EMPLOYEE_UNLOCK", "EMPLOYEE_ROLE_ASSIGN")) {
                db.update("INSERT INTO permission (code,name,created_at,updated_at) VALUES (?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                        code, code);
                db.update("""
                        INSERT INTO role_permission (role_id,permission_id,created_at)
                        SELECT r.id,p.id,UTC_TIMESTAMP(6) FROM app_role r,permission p
                        WHERE r.code='ADMIN' AND p.code=?
                        """, code);
            }
            try (var app = runApp(mysql, 0)) {
                int port = ((WebServerApplicationContext) app).getWebServer().getPort();
                assertPasswordChanges(db, port);
                assertStaffRevocation(port);
            }
        }
    }

    private static void assertStaffRevocation(int port) throws Exception {
        String me = "/api/v1/auth/me";
        var admin = new Session(port);
        admin.login("admin.fixture");
        var first = new Session(port);
        var second = new Session(port);
        String id = first.login("sales.fixture").get("userId").asString();
        second.login("sales.fixture");

        expect(200, admin.write("POST", STAFF + "/" + id + "/lock", null));
        expect(401, first.read(me));
        expect(200, admin.write("POST", STAFF + "/" + id + "/unlock", null));
        expect(401, second.read(me));

        var fresh = new Session(port);
        fresh.login("sales.fixture");
        expect(200, fresh.read(me));
        expect(200, admin.write("PUT", STAFF + "/" + id + "/roles", Map.of("roles", List.of("WAREHOUSE_STAFF"))));
        expect(401, fresh.read(me));
        fresh.login("sales.fixture");
        expect(200, fresh.read(me));
        expect(200, admin.read(me));
    }

    private static void assertPasswordChanges(JdbcTemplate db, int port) throws Exception {
        String path = "/api/v1/auth/change-password";
        String me = "/api/v1/auth/me";
        var original = new Session(port);
        var second = new Session(port);
        var other = new Session(port);
        String id = original.login("manager.fixture").get("userId").asString();
        second.login("manager.fixture");
        String otherId = other.login("sales.fixture").get("userId").asString();
        String replacement = " " + "\u00e9".repeat(35) + " ";
        assertEquals(72, replacement.getBytes(StandardCharsets.UTF_8).length);
        var valid = Map.of("currentPassword", PASSWORD, "newPassword", replacement);
        var originalProfile = db.queryForMap("""
                SELECT username, full_name, email, phone, is_active, created_at, last_login_at
                FROM app_user WHERE id=?
                """, id);
        String oldHash = db.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, id);
        String otherHash = db.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, otherId);

        expect(401, new Session(port).write("POST", path, valid));
        expect(403, original.send("POST", path, valid, null));
        var invalidCsrf = JSON.readTree("{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"invalid\"}");
        expect(403, original.send("POST", path, valid, invalidCsrf));
        expect(400, original.write("POST", path, Map.of("currentPassword", PASSWORD,
                "newPassword", replacement, "userId", otherId)));
        expect(400, original.write("POST", path, Map.of("currentPassword", PASSWORD, "newPassword", List.of("invalid"))));
        for (String invalid : List.of("", "   ", replacement + "x", "x".repeat(73))) {
            expect(400, original.write("POST", path, Map.of("currentPassword", PASSWORD, "newPassword", invalid)));
        }
        var wrong = expect(400, original.write("POST", path, Map.of("currentPassword", "wrong", "newPassword", replacement)));
        assertEquals("VALIDATION_ERROR", wrong.get("code").asString());
        expect(200, original.read(me));

        db.execute("""
                CREATE TRIGGER fixture_fail_password BEFORE UPDATE ON app_user FOR EACH ROW
                BEGIN
                    IF NEW.password_hash <> OLD.password_hash THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture rollback';
                    END IF;
                END
                """);
        try {
            expect(500, original.write("POST", path, valid));
            expect(200, original.read(me));
            expect(200, second.read(me));
            assertTrue(oldHash.equals(db.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, id)));
            assertEquals(0L, db.queryForObject("SELECT session_version FROM app_user WHERE id=?", Long.class, id));
        } finally {
            db.execute("DROP TRIGGER fixture_fail_password");
        }

        var firstRequest = original.request("POST", path, valid, original.csrf());
        var secondRequest = second.request("POST", path, valid, second.csrf());
        var firstResult = original.sendAsync(firstRequest);
        var secondResult = second.sendAsync(secondRequest);
        CompletableFuture.allOf(firstResult, secondResult).get(15, TimeUnit.SECONDS);
        assertEquals(List.of(204, 401), List.of(firstResult.join().statusCode(), secondResult.join().statusCode())
                .stream().sorted().toList());
        assertEquals("", (firstResult.join().statusCode() == 204 ? firstResult : secondResult).join().body());
        expect(401, original.read(me));
        expect(401, second.read(me));
        expect(200, other.read(me));
        assertTrue(otherHash.equals(db.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, otherId)));
        assertEquals(1L, db.queryForObject("SELECT session_version FROM app_user WHERE id=?", Long.class, id));
        assertEquals(originalProfile, db.queryForMap("""
                SELECT username, full_name, email, phone, is_active, created_at, last_login_at
                FROM app_user WHERE id=?
                """, id));
        assertEquals(List.of("MANAGER"), db.queryForList("""
                SELECT r.code FROM app_role r JOIN user_role ur ON ur.role_id=r.id WHERE ur.user_id=?
                """, String.class, id));
        String updatedHash = db.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, id);
        assertTrue(new BCryptPasswordEncoder().matches(replacement, updatedHash));
        assertFalse(oldHash.equals(updatedHash));

        var fresh = new Session(port);
        for (String previous : List.of(PASSWORD, replacement.strip())) {
            expect(401, fresh.write("POST", "/api/v1/auth/login", Map.of("username", "manager.fixture", "password", previous)));
        }
        assertEquals(id, fresh.login("manager.fixture", replacement).get("userId").asString());
        expect(200, fresh.read(me));
        expect(403, fresh.read(STAFF));
        expect(401, original.read(me));
    }

}
