package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.identity.authorization.CurrentActor;
import com.computerstore.computer_store_backend.identity.authorization.EmployeeRole;
import com.computerstore.computer_store_backend.identity.authorization.ResolvedAccess;
import com.computerstore.computer_store_backend.identity.authorization.RoleResolutionService;
import com.computerstore.computer_store_backend.partners.SupplierPermissionRolloutIT.DisposableMysql;
import com.computerstore.computer_store_backend.reports.ReportsOverview.ProductCategoryCount;
import com.computerstore.computer_store_backend.reports.ReportsOverview.StatusCounts;
import com.computerstore.computer_store_backend.reports.ReportsRepository;
import com.computerstore.computer_store_backend.reports.ReportsService;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportsOverviewIT {
    // The shared test harness allowlists this schema on its disposable named-pipe server.
    static final String SCHEMA = "csm_customer_rollout_it";
    @TempDir Path directory;

    @Test void aggregatesEmptyAndMixedStatusesWithoutChangingSourceRows() throws Exception {
        String home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to the Windows MySQL 8 installation");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var db = initialize(mysql);
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
        }
    }

    static JdbcTemplate initialize(DisposableMysql mysql) throws Exception {
        var server = new JdbcTemplate(mysql.dataSource(""));
        mysql.verifyTarget(server);
        server.execute("CREATE DATABASE " + SCHEMA);
        var source = mysql.dataSource(SCHEMA);
        var db = new JdbcTemplate(source);
        assertEquals(SCHEMA, db.queryForObject("SELECT DATABASE()", String.class));
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("1").load().migrate();
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
