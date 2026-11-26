package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.partners.StaffManagementIT.Session;
import com.computerstore.computer_store_backend.partners.SupplierPermissionRolloutIT.DisposableMysql;
import com.computerstore.computer_store_backend.partners.service.CustomerService;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.computerstore.computer_store_backend.partners.StaffManagementIT.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerContactIT {
    private static final String CUSTOMERS = "/api/v1/customers";
    private static final String PHONE = "+81 (0)3 1234 ext. 8";
    private static final String EMAIL = "fixture@example.test";
    private static final String ADDRESS = "Phường Bến Thành, Thành phố Hồ Chí Minh";
    private static final String NOTES = "Giao buổi sáng\nGọi trước khi đến";
    @TempDir Path directory;

    @Test void contactsRoundTripPreserveOmittedValuesAndRollBackOnFailure() throws Exception {
        String home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to the Windows MySQL 8 installation");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var db = initializeFixture(mysql);
            fixture(db, "sales.fixture", "SALES_STAFF");
            for (var code : List.of("CUSTOMER_READ", "CUSTOMER_CREATE", "CUSTOMER_UPDATE")) {
                db.update("INSERT INTO permission (code,name,created_at,updated_at) VALUES (?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                        code, code);
                db.update("""
                        INSERT INTO role_permission (role_id,permission_id,created_at)
                        SELECT r.id,p.id,UTC_TIMESTAMP(6) FROM app_role r,permission p
                        WHERE p.code=? AND r.code='SALES_STAFF'
                        """, code);
            }
            try (var app = runApp(mysql, 0)) {
                var sales = new Session(((WebServerApplicationContext) app).getWebServer().getPort());
                sales.login("sales.fixture");
                long id = assertContactWorkflow(db, sales);
                var guard = app.getBean(CustomerService.class);
                var write = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
                write.executeWithoutResult(status -> guard.requireActiveForSale(id));
                db.update("UPDATE customer SET is_active=FALSE WHERE id=?", id);
                assertEquals(ErrorCode.BUSINESS_CONFLICT, assertThrows(BusinessException.class,
                        () -> write.executeWithoutResult(status -> guard.requireActiveForSale(id))).code());
                expect(200, sales.write("PUT", CUSTOMERS + "/" + id, Map.of("code", "C-ONE", "fullName", "Khách cũ")));
                assertFalse(expect(200, sales.read(CUSTOMERS + "/" + id)).get("active").asBoolean());
            }
        }
    }

    private static JdbcTemplate initializeFixture(DisposableMysql mysql) throws Exception {
        var schema = ReportsOverviewIT.SCHEMA;
        var server = new JdbcTemplate(mysql.dataSource(""));
        mysql.verifyTarget(server);
        server.execute("CREATE DATABASE " + schema);
        var source = mysql.dataSource(schema);
        var ddl = new ClassPathResource("db/migration/V1__create_initial_schema.sql").getContentAsString(StandardCharsets.UTF_8);
        String anchor = "  last_login_at DATETIME(6) NULL,";
        assertTrue(ddl.contains(anchor));
        ddl = ddl.replace(anchor, "  session_version BIGINT NOT NULL DEFAULT 0,\n" + anchor);
        try (var connection = source.getConnection()) {
            assertEquals(schema, connection.getCatalog());
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)));
        }
        return new JdbcTemplate(source);
    }

    private static long assertContactWorkflow(JdbcTemplate db, Session sales) throws Exception {
        var first = expect(201, sales.write("POST", CUSTOMERS, Map.of("code", " c-one ", "fullName", "Nguyễn Ánh",
                "phone", PHONE, "email", EMAIL, "address", ADDRESS, "notes", NOTES)));
        assertFalse(first.has("contact"));
        long id = Long.parseLong(first.get("id").asString());
        var path = CUSTOMERS + "/" + id;
        var original = expect(200, sales.read(path)).get("contact");
        assertEquals(PHONE, original.get("phone").asString());
        assertEquals(EMAIL, original.get("email").asString());
        assertEquals(ADDRESS, original.get("address").asString());
        assertEquals(NOTES, original.get("notes").asString());
        assertEquals("Nguyễn Ánh", first.get("fullName").asString());

        expect(201, sales.write("POST", CUSTOMERS, Map.of("code", "C-TWO", "fullName", "Khách thứ hai",
                "phone", PHONE, "email", EMAIL)));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM customer WHERE phone=? AND email=?", Integer.class, PHONE, EMAIL));
        var empty = expect(201, sales.write("POST", CUSTOMERS, Map.of("code", "C-THREE", "fullName", "Khách thứ ba")));
        var emptyContact = expect(200, sales.read(CUSTOMERS + "/" + empty.get("id").asString())).get("contact");
        for (var field : List.of("phone", "email", "address", "notes")) assertTrue(emptyContact.get(field).isNull());

        var edit = new HashMap<String, Object>();
        edit.put("code", "C-ONE");
        edit.put("fullName", "Nguyễn Ánh");
        edit.put("phone", null);
        edit.put("address", "Đà Nẵng");
        expect(200, sales.write("PUT", path, edit));
        var contact = expect(200, sales.read(path)).get("contact");
        assertTrue(contact.get("phone").isNull());
        assertEquals("Đà Nẵng", contact.get("address").asString());
        assertEquals(EMAIL, contact.get("email").asString());
        assertEquals(NOTES, contact.get("notes").asString());

        expect(200, sales.write("PUT", path, Map.of("code", "C-ONE", "fullName", "Nguyễn Ánh mới")));
        assertEquals(contact, expect(200, sales.read(path)).get("contact"));
        var before = db.queryForMap("SELECT * FROM customer WHERE id=?", id);
        db.execute("CREATE TRIGGER fixture_reject_contact AFTER UPDATE ON customer FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='disposable rollback fixture'");
        try {
            expect(500, sales.write("PUT", path, Map.of("code", "C-ONE", "fullName", "Thử rollback", "email", "other@example.test")));
        } finally {
            db.execute("DROP TRIGGER fixture_reject_contact");
        }
        assertEquals(before, db.queryForMap("SELECT * FROM customer WHERE id=?", id));
        return id;
    }
}
