package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.ComputerStoreBackendApplication;
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
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import tools.jackson.databind.json.JsonMapper;

import static com.computerstore.computer_store_backend.partners.StaffManagementIT.expect;
import static com.computerstore.computer_store_backend.partners.StaffManagementIT.fixture;
import static org.junit.jupiter.api.Assertions.*;

class ProductInventoryCreationIT {
    private static final String SCHEMA = ReportsOverviewIT.SCHEMA;
    private static final String PRODUCTS = "/api/v1/products";
    private static final String CATEGORIES = "/api/v1/categories";
    private static final String PRODUCT_STOCK = "/api/v1/reports/inventory/products/";
    private static final String PRICE = "9999999999999999999";
    @TempDir Path directory;

    @Test void productCreateOwnsTheInventoryTransactionAndSerializesCategoryAndSkuRaces() throws Exception {
        String home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to the Windows MySQL 8 installation");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var server = new JdbcTemplate(mysql.dataSource(""));
            mysql.verifyTarget(server);
            server.execute("CREATE DATABASE " + SCHEMA);
            var source = mysql.dataSource(SCHEMA);
            String ddl = new ClassPathResource("db/migration/V1__create_initial_schema.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            String anchor = "  last_login_at DATETIME(6) NULL,";
            assertTrue(ddl.contains(anchor));
            ddl = ddl.replace(anchor, "  session_version BIGINT NOT NULL DEFAULT 0,\n" + anchor);
            try (var connection = source.getConnection()) {
                assertEquals(SCHEMA, connection.getCatalog());
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)));
            }
            var db = new JdbcTemplate(source);
            seed(db);
            try (var app = SpringApplication.run(ComputerStoreBackendApplication.class,
                    "--spring.datasource.url=" + source.getURL(),
                    "--spring.datasource.username=root", "--spring.datasource.password=",
                    "--spring.flyway.enabled=false", "--spring.jpa.hibernate.ddl-auto=validate",
                    "--server.address=127.0.0.1", "--server.port=0",
                    "--logging.level.root=WARN")) {
                int port = ((WebServerApplicationContext) app).getWebServer().getPort();
                var manager = new Session(port);
                manager.login("manager.fixture");
                assertSecurityAndCreation(db, port, manager);
                assertRollback(db, manager);
                var competing = new Session(port);
                competing.login("manager.fixture");
                assertConcurrentRequests(db, manager, competing);
            }
        }
    }

    private static void seed(JdbcTemplate db) {
        fixture(db, "manager.fixture", "MANAGER");
        fixture(db, "sales.fixture", "SALES_STAFF");
        for (var code : List.of("PRODUCT_READ", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_PRICE_UPDATE",
                "PRODUCT_DEACTIVATE", "CATEGORY_DEACTIVATE", "CATEGORY_READ", "SUPPLIER_READ", "CUSTOMER_READ")) {
            db.update("INSERT INTO permission (code,name,created_at,updated_at) VALUES (?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                    code, code);
            db.update("""
                    INSERT INTO role_permission (role_id,permission_id,created_at)
                    SELECT r.id,p.id,UTC_TIMESTAMP(6) FROM app_role r,permission p
                    WHERE p.code=? AND (r.code='MANAGER' OR (p.code='PRODUCT_READ' AND r.code='SALES_STAFF'))
                    """, code);
        }
        for (int id = 1; id <= 5; id++) {
            db.update("""
                    INSERT INTO category (id,code,name,is_active,created_at,updated_at)
                    VALUES (?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                    """, id, "CAT-" + id, "Fixture category " + id, id != 5);
        }
    }

    private static void assertSecurityAndCreation(JdbcTemplate db, int port, Session manager) throws Exception {
        var input = payload("new-sku", 1);
        var anonymous = new Session(port);
        expect(401, anonymous.write("POST", PRODUCTS, input));
        var sales = new Session(port);
        sales.login("sales.fixture");
        expect(403, sales.write("POST", PRODUCTS, input));
        var salesActions = expect(200, sales.read(PRODUCTS)).get("allowedActions");
        assertTrue(salesActions.isEmpty());
        expect(403, manager.send("POST", PRODUCTS, input, null));
        var invalidCsrf = JsonMapper.builder().build().readTree("{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"invalid\"}");
        expect(403, manager.send("POST", PRODUCTS, input, invalidCsrf));
        expect(400, manager.write("POST", PRODUCTS, Map.of("information", Map.of(), "sellingPrice", PRICE)));
        expect(400, manager.write("POST", PRODUCTS, Map.of("information", information("NUMBER", 1), "sellingPrice", 123)));
        expect(409, manager.write("POST", PRODUCTS, payload("INACTIVE", 5)));
        assertEquals(0, count(db, "SELECT COUNT(*) FROM product"));
        assertEquals(0, count(db, "SELECT COUNT(*) FROM inventory"));

        var actions = expect(200, manager.read(PRODUCTS)).get("allowedActions");
        assertTrue(actions.toString().contains("\"PRODUCT_CREATE\""));
        var response = manager.write("POST", PRODUCTS, input);
        var created = expect(201, response);
        String id = created.get("id").asString();
        assertEquals(PRODUCTS + "/" + id, response.headers().firstValue("Location").orElseThrow());
        assertEquals(PRICE, created.get("sellingPrice").asString());
        assertEquals("NEW-SKU", created.get("sku").asString());
        assertEquals(1, count(db, "SELECT COUNT(*) FROM product"));
        assertZeroBalance(db, "NEW-SKU");
        assertEquals(0, count(db, "SELECT COUNT(*) FROM inventory_transaction"));
        assertEquals(id, expect(200, manager.read(PRODUCTS + "?q=NEW-SKU")).get("items").get(0).get("id").asString());
        expect(409, manager.write("POST", PRODUCTS, input));
        expect(401, anonymous.read(PRODUCT_STOCK + id));
        expect(403, sales.read(PRODUCT_STOCK + id));
        assertProductStock(db, manager, id);

        expect(200, manager.write("PUT", PRODUCTS + "/" + id, information("NEW-SKU", 1)));
        assertEquals("9007199254740993", expect(200, manager.write("PUT", PRODUCTS + "/" + id + "/price",
                Map.of("sellingPrice", "9007199254740993"))).get("sellingPrice").asString());
        assertFalse(expect(200, manager.write("POST", PRODUCTS + "/" + id + "/deactivate", null))
                .get("active").asBoolean());
        expect(404, manager.read(PRODUCT_STOCK + id));
        assertZeroBalance(db, "NEW-SKU");
    }

    private static void assertProductStock(JdbcTemplate db, Session manager, String id) throws Exception {
        expect(204, manager.read("/api/v1/reports/access"));
        var path = PRODUCT_STOCK + id;
        var empty = expect(200, manager.read(path));
        assertEquals(6, empty.size());
        assertEquals(id, empty.get("productId").asString());
        assertEquals("NEW-SKU", empty.get("sku").asString());
        assertEquals("Fixture new-sku", empty.get("productName").asString());
        assertEquals("0", empty.get("quantityOnHand").asString());
        assertEquals("4", empty.get("minStockLevel").asString());
        assertEquals("OUT_OF_STOCK", empty.get("stockStatus").asString());

        for (long quantity : List.of(3L, 4L, 9007199254740993L)) {
            db.update("UPDATE inventory SET quantity_on_hand=? WHERE product_id=?", quantity, id);
            var stock = expect(200, manager.read(path));
            assertEquals(Long.toString(quantity), stock.get("quantityOnHand").asString());
            assertEquals(quantity <= 4 ? "LOW_STOCK" : "NORMAL", stock.get("stockStatus").asString());
        }
        db.update("DELETE FROM inventory WHERE product_id=?", id);
        assertEquals("BUSINESS_CONFLICT", expect(409, manager.read(path)).get("code").asString());
        db.update("INSERT INTO inventory (product_id,quantity_on_hand,created_at,updated_at) VALUES (?,0,NOW(6),NOW(6))", id);
        expect(404, manager.read(PRODUCT_STOCK + "999999"));
        expect(400, manager.read(PRODUCT_STOCK + "9223372036854775808"));
        expect(403, manager.read("/api/v1/inventory/balances"));
    }

    private static void assertRollback(JdbcTemplate db, Session manager) throws Exception {
        long before = count(db, "SELECT COUNT(*) FROM inventory");
        for (String table : List.of("product", "inventory")) {
            db.execute("CREATE TRIGGER fixture_reject_insert BEFORE INSERT ON " + table
                    + " FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='disposable rollback fixture'");
            try {
                expect(500, manager.write("POST", PRODUCTS, payload("ROLLBACK", 1)));
            } finally {
                db.execute("DROP TRIGGER fixture_reject_insert");
            }
            assertEquals(0, count(db, "SELECT COUNT(*) FROM product WHERE sku='ROLLBACK'"));
            assertEquals(before, count(db, "SELECT COUNT(*) FROM inventory"));
        }
    }

    private static void assertConcurrentRequests(JdbcTemplate db, Session first, Session second) throws Exception {
        // Different categories use independent locks, so uniqueness must also hold at the SKU constraint.
        var one = first.request("POST", PRODUCTS, payload("RACE-SKU", 2), first.csrf());
        var two = second.request("POST", PRODUCTS, payload("race-sku", 3), second.csrf());
        var created = first.sendAsync(one);
        var duplicate = second.sendAsync(two);
        CompletableFuture.allOf(created, duplicate).get(15, TimeUnit.SECONDS);
        assertEquals(List.of(201, 409), List.of(created.join().statusCode(), duplicate.join().statusCode())
                .stream().sorted().toList());
        assertZeroBalance(db, "RACE-SKU");

        var create = first.request("POST", PRODUCTS, payload("CATEGORY-RACE", 4), first.csrf());
        var deactivate = second.request("POST", CATEGORIES + "/4/deactivate", null, second.csrf());
        var product = first.sendAsync(create);
        var category = second.sendAsync(deactivate);
        CompletableFuture.allOf(product, category).get(15, TimeUnit.SECONDS);
        if (product.join().statusCode() == 201) {
            expect(409, category.join());
            assertZeroBalance(db, "CATEGORY-RACE");
            assertEquals(1, count(db, "SELECT COUNT(*) FROM category WHERE id=4 AND is_active=TRUE"));
        } else {
            expect(409, product.join());
            expect(200, category.join());
            assertEquals(0, count(db, "SELECT COUNT(*) FROM category WHERE id=4 AND is_active=TRUE"));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM product WHERE sku='CATEGORY-RACE'"));
        }
        assertEquals(0, count(db, "SELECT COUNT(*) FROM inventory_transaction"));
        assertEquals(count(db, "SELECT COUNT(*) FROM product"), count(db, "SELECT COUNT(*) FROM inventory"));
    }

    private static void assertZeroBalance(JdbcTemplate db, String sku) {
        assertEquals(List.of(0L), db.queryForList("""
                SELECT i.quantity_on_hand FROM inventory i JOIN product p ON p.id=i.product_id WHERE p.sku=?
                """, Long.class, sku));
    }

    private static long count(JdbcTemplate db, String sql) {
        return db.queryForObject(sql, Long.class);
    }

    private static Map<String, Object> payload(String sku, int category) {
        return Map.of("information", information(sku, category), "sellingPrice", PRICE);
    }

    private static Map<String, String> information(String sku, int category) {
        return Map.of("sku", sku, "name", "Fixture " + sku, "categoryId", Integer.toString(category), "minStockLevel", "4");
    }
}
