package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.identity.authorization.CurrentActor;
import com.computerstore.computer_store_backend.identity.authorization.EmployeeRole;
import com.computerstore.computer_store_backend.identity.authorization.ResolvedAccess;
import com.computerstore.computer_store_backend.identity.authorization.RoleResolutionService;
import com.computerstore.computer_store_backend.partners.SupplierPermissionRolloutIT.DisposableMysql;
import com.computerstore.computer_store_backend.reports.ReportsOverview.ProductCategoryCount;
import com.computerstore.computer_store_backend.reports.ReportsOverview.StatusCounts;
import com.computerstore.computer_store_backend.reports.ReportsOverview;
import com.computerstore.computer_store_backend.reports.ReportsRepository;
import com.computerstore.computer_store_backend.reports.ReportsService;
import com.computerstore.computer_store_backend.reports.InventoryReport;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.computerstore.computer_store_backend.reports.InventoryReport.StockFilter.*;

class ReportsOverviewIT {
    // The shared test harness allowlists this schema on its disposable named-pipe server.
    static final String SCHEMA = "csm_customer_rollout_it";
    @TempDir Path directory;

    @Test void aggregatesEmptyAndMixedStatusesWithoutChangingSourceRows() throws Exception {
        String home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to the Windows MySQL 8 installation");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var db = initializeWithoutFlyway(mysql);
            var roles = mock(RoleResolutionService.class);
            when(roles.resolveCurrent()).thenReturn(new ResolvedAccess(new CurrentActor(1L, "fixture"),
                    Set.of(EmployeeRole.MANAGER), Set.of("PRODUCT_READ", "CATEGORY_READ", "SUPPLIER_READ", "CUSTOMER_READ")));
            var reports = new ReportsService(roles, new ReportsRepository(db));

            var empty = reports.overview();
            var zero = new StatusCounts(0, 0, 0);
            assertEquals(List.of(zero, zero, zero, zero), List.of(empty.products(), empty.categories(), empty.suppliers(), empty.customers()));
            assertTrue(empty.productsByCategory().isEmpty());

            seed(db);
            var before = snapshot(db);
            var overview = reports.overview();
            assertEquals(new StatusCounts(3, 2, 1), overview.products());
            assertEquals(new StatusCounts(3, 2, 1), overview.categories());
            assertEquals(new StatusCounts(2, 1, 1), overview.suppliers());
            assertEquals(new StatusCounts(1, 0, 1), overview.customers());
            assertEquals(List.of(
                    new ProductCategoryCount("A", "First", new StatusCounts(2, 1, 1)),
                    new ProductCategoryCount("B", "Inactive category", new StatusCounts(1, 1, 0)),
                    new ProductCategoryCount("Z", "Empty category", zero)), overview.productsByCategory());
            assertEquals(before, snapshot(db));

            // A concurrent writer commits immediately after the first response query.
            // With a second SQL read under READ COMMITTED, its breakdown would see the change.
            var interleaved = spy(db);
            doAnswer(call -> {
                Object response = call.callRealMethod();
                db.update("UPDATE product SET is_active=FALSE WHERE sku='P1'");
                return response;
            }).when(interleaved).query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<ReportsOverview>>any());
            var consistent = new ReportsService(roles, new ReportsRepository(interleaved)).overview();
            assertEquals(overview, consistent);
            var refreshed = reports.overview();
            assertEquals(new StatusCounts(3, 1, 2), refreshed.products());
            assertEquals(new StatusCounts(2, 0, 2), refreshed.productsByCategory().getFirst().products());
        }
    }

    @Test void inventoryUsesOnlyActiveBalancesAndPaginatesAnExactSnapshot() throws Exception {
        String home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to the Windows MySQL 8 installation");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var db = initializeWithoutFlyway(mysql);
            var roles = mock(RoleResolutionService.class);
            when(roles.resolveCurrent()).thenReturn(new ResolvedAccess(new CurrentActor(1L, "fixture"),
                    Set.of(EmployeeRole.MANAGER), Set.of("PRODUCT_READ", "CATEGORY_READ", "SUPPLIER_READ", "CUSTOMER_READ")));
            var reports = new ReportsService(roles, new ReportsRepository(db));
            for (var filter : InventoryReport.StockFilter.values()) {
                var empty = reports.inventory(0, 2, filter);
                assertEquals(new InventoryReport.Summary(0, "0", 0, 0), empty.summary());
                assertTrue(empty.items().isEmpty());
                assertEquals(0, empty.totalElements());
                assertEquals(0, empty.totalPages());
            }

            db.update("""
                    INSERT INTO category (id,code,name,is_active,created_at,updated_at) VALUES
                    (1,'A','First',TRUE,UTC_TIMESTAMP(),UTC_TIMESTAMP()),
                    (2,'B','Second',FALSE,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                    """);
            long large = 9_007_199_254_740_993L;
            stockProduct(db, "C-ZERO", 1, true, 0, 0L);
            stockProduct(db, "A-BELOW", 2, true, 4, 2L);
            stockProduct(db, "B-EQUAL", 1, true, 3, 3L);
            stockProduct(db, "D-ABOVE", 1, true, 5, 6L);
            stockProduct(db, "E-LARGE", 2, true, large, large);
            stockProduct(db, "F-MAX", 1, true, 0, Long.MAX_VALUE);
            stockProduct(db, "G-INACTIVE", 1, false, 4, 0L);
            stockProduct(db, "H-INACTIVE-MISSING", 1, false, 0, null);

            var before = db.queryForList("SELECT * FROM inventory ORDER BY id");
            var first = reports.inventory(0, 2, LOW);
            var second = reports.inventory(1, 2, LOW);
            var beyond = reports.inventory(2, 2, LOW);
            var total = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(large)).add(BigInteger.valueOf(11));
            assertEquals(new InventoryReport.Summary(6, total.toString(), 1, 4), first.summary());
            assertEquals(first.summary(), second.summary());
            assertEquals(first.summary(), beyond.summary());
            assertEquals(4, first.totalElements());
            assertEquals(2, first.totalPages());
            assertTrue(beyond.items().isEmpty());
            assertEquals(List.of("A-BELOW", "B-EQUAL"), first.items().stream().map(InventoryReport.StockProduct::sku).toList());
            assertEquals("Second", first.items().getFirst().categoryName());
            assertEquals(new InventoryReport.StockProduct("C-ZERO", "C-ZERO", "First", "0", "0",
                    InventoryReport.StockStatus.OUT_OF_STOCK), second.items().getFirst());
            assertEquals(new InventoryReport.StockProduct("E-LARGE", "E-LARGE", "Second",
                    Long.toString(large), Long.toString(large), InventoryReport.StockStatus.LOW_STOCK), second.items().getLast());

            var all = reports.inventory(0, 6, ALL);
            assertEquals(first.summary(), all.summary());
            assertEquals(6, all.totalElements());
            assertEquals(List.of("A-BELOW", "B-EQUAL", "C-ZERO", "D-ABOVE", "E-LARGE", "F-MAX"),
                    all.items().stream().map(InventoryReport.StockProduct::sku).toList());
            assertEquals(List.of(InventoryReport.StockStatus.LOW_STOCK, InventoryReport.StockStatus.LOW_STOCK,
                    InventoryReport.StockStatus.OUT_OF_STOCK, InventoryReport.StockStatus.NORMAL,
                    InventoryReport.StockStatus.LOW_STOCK, InventoryReport.StockStatus.NORMAL),
                    all.items().stream().map(InventoryReport.StockProduct::stockStatus).toList());
            assertEquals(Long.toString(Long.MAX_VALUE), all.items().getLast().quantityOnHand());
            var lastPage = reports.inventory(2, 2, ALL);
            assertEquals(3, lastPage.totalPages());
            assertEquals(all.items().subList(4, 6), lastPage.items());

            var out = reports.inventory(0, 2, OUT);
            assertEquals(first.summary(), out.summary());
            assertEquals(1, out.totalElements());
            assertEquals(1, out.totalPages());
            assertEquals(List.of(second.items().getFirst()), out.items());
            assertEquals(before, db.queryForList("SELECT * FROM inventory ORDER BY id"));

            stockProduct(db, "I-MISSING", 1, true, 0, null);
            for (var filter : InventoryReport.StockFilter.values()) {
                var failure = assertThrows(BusinessException.class, () -> reports.inventory(0, 2, filter));
                assertEquals(ErrorCode.BUSINESS_CONFLICT, failure.code());
            }
            assertEquals(7, reports.overview().products().active());
        }
    }

    private static void stockProduct(JdbcTemplate db, String sku, int category, boolean active, long min, Long balance) {
        db.update("""
                INSERT INTO product (sku,name,category_id,selling_price,is_active,min_stock_level,created_at,updated_at)
                VALUES (?,?,?,100,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """, sku, sku, category, active, min);
        if (balance != null) {
            db.update("""
                    INSERT INTO inventory (product_id,quantity_on_hand,created_at,updated_at)
                    SELECT id,?,UTC_TIMESTAMP(),UTC_TIMESTAMP() FROM product WHERE sku=?
                    """, balance, sku);
        }
    }

    static JdbcTemplate initializeWithoutFlyway(DisposableMysql mysql) throws Exception {
        var db = createEmptySchema(mysql);
        try (var connection = db.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__create_initial_schema.sql"));
        }
        return db;
    }

    static JdbcTemplate initialize(DisposableMysql mysql) throws Exception {
        var db = createEmptySchema(mysql);
        Flyway.configure().dataSource(db.getDataSource()).locations("classpath:db/migration").target("1").load().migrate();
        return db;
    }

    private static JdbcTemplate createEmptySchema(DisposableMysql mysql) throws Exception {
        var server = new JdbcTemplate(mysql.dataSource(""));
        mysql.verifyTarget(server);
        server.execute("CREATE DATABASE " + SCHEMA);
        var source = mysql.dataSource(SCHEMA);
        var db = new JdbcTemplate(source);
        assertEquals(SCHEMA, db.queryForObject("SELECT DATABASE()", String.class));
        return db;
    }

    static void seed(JdbcTemplate db) {
        db.update("""
                INSERT INTO category (id, code, name, is_active, created_at, updated_at) VALUES
                (1,'Z','Empty category',TRUE,UTC_TIMESTAMP(),UTC_TIMESTAMP()),
                (2,'B','Inactive category',FALSE,UTC_TIMESTAMP(),UTC_TIMESTAMP()),
                (3,'A','First',TRUE,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """);
        db.update("""
                INSERT INTO product (sku, name, category_id, selling_price, is_active, created_at, updated_at) VALUES
                ('P1','One',3,100,TRUE,UTC_TIMESTAMP(),UTC_TIMESTAMP()),
                ('P2','Two',3,200,FALSE,UTC_TIMESTAMP(),UTC_TIMESTAMP()),
                ('P3','Three',2,300,TRUE,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """);
        db.update("""
                INSERT INTO supplier (code, name, is_active, created_at, updated_at) VALUES
                ('S1','First supplier',TRUE,UTC_TIMESTAMP(),UTC_TIMESTAMP()),
                ('S2','Second supplier',FALSE,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """);
        db.update("""
                INSERT INTO customer (code, full_name, phone, is_active, created_at, updated_at)
                VALUES ('C1','Fixture','123',FALSE,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """);
    }

    private static List<?> snapshot(JdbcTemplate db) {
        return List.of(db.queryForList("SELECT * FROM category ORDER BY id"),
                db.queryForList("SELECT * FROM product ORDER BY id"),
                db.queryForList("SELECT * FROM supplier ORDER BY id"),
                db.queryForList("SELECT * FROM customer ORDER BY id"));
    }
}
