package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import com.computerstore.computer_store_backend.inventory.repository.InventoryTransactionRepository;
import com.computerstore.computer_store_backend.partners.StaffManagementIT.Session;
import com.computerstore.computer_store_backend.partners.SupplierPermissionRolloutIT.DisposableMysql;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import static com.computerstore.computer_store_backend.partners.StaffManagementIT.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryMovementReportIT {
    private static final String MOVEMENTS = "/api/v1/reports/inventory/movements";
    @TempDir Path directory;

    @Test void reportsOnlyRecordedMovementsWithVietnamDatesAndStablePages() throws Exception {
        String home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to the Windows MySQL 8 installation");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var db = ReportsOverviewIT.initializeWithoutFlyway(mysql);
            db.execute("ALTER TABLE app_user ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0");
            seedActors(db);
            try (var app = runApp(mysql, 0)) {
                int port = ((WebServerApplicationContext) app).getWebServer().getPort();
                expect(401, new Session(port).read(MOVEMENTS));
                var admin = new Session(port);
                admin.login("history.admin");
                expect(403, admin.read(MOVEMENTS));
                var manager = new Session(port);
                var id = manager.login("history.manager").get("userId").asLong();
                seedProducts(db);
                var empty = expect(200, manager.read(MOVEMENTS));
                assertTrue(empty.get("items").isEmpty());
                assertEquals(0, empty.get("totalElements").asLong());
                seedMovements(db, id);
                var before = db.queryForList("SELECT * FROM inventory_transaction ORDER BY id");
                verifyMovements(manager);
                var inventory = app.getBean(InventoryTransactionRepository.class);
                var sales = inventory.search(1L, InventoryTransactionType.SALE_OUT,
                        LocalDateTime.parse("2026-11-26T17:00:00"), LocalDateTime.parse("2026-11-27T17:00:00"),
                        PageRequest.of(0, 1));
                assertEquals(1, sales.getTotalElements());
                assertEquals(-9007199254740993L, sales.getContent().getFirst().getQuantityDelta());
                assertEquals("history.manager", sales.getContent().getFirst().getRecordedBy().getUsername());
                expect(200, manager.read("/api/v1/reports/overview"));
                assertEquals(before, db.queryForList("SELECT * FROM inventory_transaction ORDER BY id"));
            }
        }
    }

    private static void seedActors(JdbcTemplate db) {
        fixture(db, "history.manager", "MANAGER");
        fixture(db, "history.admin", "ADMIN");
        for (var code : List.of("PRODUCT_READ", "CATEGORY_READ", "SUPPLIER_READ", "CUSTOMER_READ")) {
            db.update("INSERT INTO permission (code,name,created_at,updated_at) VALUES (?,?,NOW(6),NOW(6))", code, code);
            db.update("""
                    INSERT INTO role_permission (role_id,permission_id,created_at)
                    SELECT r.id,p.id,NOW(6) FROM app_role r,permission p
                    WHERE r.code IN ('MANAGER','ADMIN') AND p.code=?
                    """, code);
        }
    }

    private static void seedProducts(JdbcTemplate db) {
        db.update("INSERT INTO category (id,code,name,created_at,updated_at) VALUES (1,'PARTS','Parts',NOW(6),NOW(6))");
        db.update("""
                INSERT INTO product (id,sku,name,category_id,selling_price,is_active,created_at,updated_at) VALUES
                    (1,'P_1','Linh kiện',1,1,TRUE,NOW(6),NOW(6)),
                    (2,'PX1','Phụ kiện',1,1,FALSE,NOW(6),NOW(6))
                """);
        db.update("""
                INSERT INTO inventory (product_id,quantity_on_hand,created_at,updated_at)
                VALUES (1,9007199254740998,NOW(6),NOW(6)), (2,1,NOW(6),NOW(6))
                """);
    }

    private static void seedMovements(JdbcTemplate db, long actor) {
        db.update("INSERT INTO supplier (id,code,name,created_at,updated_at) VALUES (1,'S1','Supplier',NOW(6),NOW(6))");
        db.update("""
                INSERT INTO purchase (id,document_no,supplier_id,status,created_by,confirmed_by,confirmed_at,created_at,updated_at)
                VALUES (1,'P-1',1,'CONFIRMED',?,?,'2026-11-26 16:59:59.999999',NOW(6),NOW(6))
                """, actor, actor);
        db.update("""
                INSERT INTO purchase_item (id,purchase_id,product_id,product_name_snapshot,sku_snapshot,quantity,unit_cost,line_total,created_at,updated_at)
                VALUES (1,1,1,'Linh kiện','P_1',9007199254740995,0,0,NOW(6),NOW(6))
                """);
        db.update("""
                INSERT INTO sales_order (id,order_no,status,created_by,confirmed_by,confirmed_at,cancelled_at,cancelled_by,
                    cancel_reason,created_at,updated_at)
                VALUES (1,'S-1','CANCELLED',?,?,'2026-11-26 17:00:00','2026-11-27 17:00:00',?,'fixture',NOW(6),NOW(6))
                """, actor, actor, actor);
        db.update("""
                INSERT INTO sales_order_item (id,order_id,product_id,product_name_snapshot,sku_snapshot,quantity,unit_price,line_total,created_at,updated_at)
                VALUES (1,1,1,'Linh kiện','P_1',9007199254740993,0,0,NOW(6),NOW(6))
                """);
        db.update("""
                INSERT INTO inventory_adjustment (id,adjustment_no,status,reason,proposed_by,approved_by,proposed_at,approved_at,created_at,updated_at)
                SELECT 1,'A-1','APPROVED','fixture',id,?,'2026-11-26 16:00:00','2026-11-26 17:00:00',NOW(6),NOW(6)
                FROM app_user WHERE username='history.admin'
                """, actor);
        db.update("""
                INSERT INTO inventory_adjustment_item (id,adjustment_id,product_id,quantity_delta,created_at,updated_at)
                VALUES (1,1,1,3,NOW(6),NOW(6)), (2,1,2,1,NOW(6),NOW(6))
                """);
        db.update("""
                INSERT INTO inventory_transaction (id,product_id,transaction_type,quantity_delta,quantity_after,occurred_at,
                    recorded_by,purchase_item_id,sales_order_item_id,adjustment_item_id,reversal_of_transaction_id,reversal_reason,note,created_at)
                VALUES
                    (1,1,'PURCHASE_IN',9007199254740995,9007199254740995,'2026-11-26 16:59:59.999999',?,1,NULL,NULL,NULL,NULL,'private-note',NOW(6)),
                    (2,1,'SALE_OUT',-9007199254740993,2,'2026-11-26 17:00:00',?,NULL,1,NULL,NULL,NULL,NULL,NOW(6)),
                    (3,1,'ADJUSTMENT_IN',3,5,'2026-11-26 17:00:00',?,NULL,NULL,1,NULL,NULL,NULL,NOW(6)),
                    (4,2,'ADJUSTMENT_IN',1,1,'2026-11-27 16:59:59.999999',?,NULL,NULL,2,NULL,NULL,NULL,NOW(6)),
                    (5,1,'SALE_CANCEL_IN',9007199254740993,9007199254740998,'2026-11-27 17:00:00',?,NULL,NULL,NULL,2,'private-reason',NULL,NOW(6))
                """, actor, actor, actor, actor, actor);
    }

    private static void verifyMovements(Session manager) throws Exception {
        var day = MOVEMENTS + "?fromDate=2026-11-27&toDate=2026-11-27&size=1";
        var first = expect(200, manager.read(day));
        assertEquals(3, first.get("totalElements").asLong());
        assertEquals(3, first.get("totalPages").asInt());
        var endOfDay = first.get("items").get(0);
        assertEquals("PX1", endOfDay.get("sku").asString());
        assertEquals("2026-11-27T16:59:59.999999Z", endOfDay.get("occurredAt").asString());
        var tied = item(manager, day + "&page=1");
        var outbound = item(manager, day + "&page=2");
        assertEquals("ADJUSTMENT_IN", tied.get("movementType").asString());
        assertEquals("-9007199254740993", outbound.get("quantityDelta").asString());
        assertEquals("SALE_OUT", outbound.get("movementType").asString());
        assertEquals("2026-11-26T17:00:00Z", outbound.get("occurredAt").asString());
        assertEquals(outbound.get("occurredAt"), tied.get("occurredAt"));
        assertEquals("Linh kiện", outbound.get("productName").asString());

        var inbound = item(manager, MOVEMENTS + "?q=p_1&type=PURCHASE_IN&toDate=2026-11-26");
        assertEquals("9007199254740995", inbound.get("quantityDelta").asString());
        assertEquals(5, inbound.size());
        for (var field : List.of("id", "recordedBy", "note", "purchaseItemId", "sourceId", "quantityAfter")) {
            assertFalse(inbound.has(field));
        }
        assertEquals(4, expect(200, manager.read(MOVEMENTS + "?q=p_1")).get("totalElements").asLong());
        assertEquals(1, expect(200, manager.read(MOVEMENTS + "?q=Ph%E1%BB%A5%20ki%E1%BB%87n")).get("totalElements").asLong());
        assertEquals("SALE_CANCEL_IN", item(manager, MOVEMENTS + "?fromDate=2026-11-28").get("movementType").asString());
        for (var invalid : List.of("fromDate=2026-02-29", "toDate=2026-1-1", "fromDate=2026-11-28&toDate=2026-11-27",
                "type=STOCKTAKE", "page=-1", "size=101")) {
            assertEquals("VALIDATION_ERROR", expect(400, manager.read(MOVEMENTS + "?" + invalid)).get("code").asString());
        }
    }

    private static JsonNode item(Session manager, String path) throws Exception {
        return expect(200, manager.read(path)).get("items").get(0);
    }
}
