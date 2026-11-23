package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.operations.category.v1.V2__register_category_permissions;
import com.computerstore.computer_store_backend.operations.category.v1.V3__grant_category_permissions;
import com.computerstore.computer_store_backend.operations.product.v1.V4__register_product_permissions;
import com.computerstore.computer_store_backend.operations.product.v1.V5__grant_product_permissions;
import com.computerstore.computer_store_backend.partners.SupplierPermissionRolloutIT.DisposableMysql;
import com.computerstore.computer_store_backend.operations.customer.v1.CustomerPermissionMigrationRunner;
import com.mysql.cj.jdbc.MysqlDataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/** Uses a fresh named-pipe MySQL test server with networking disabled. */
class CustomerRolloutIT {
    private static final String SCHEMA = "csm_customer_rollout_it";
    private static final String CUSTOMER_MIGRATIONS = "operations/customer/v1";

    @TempDir Path directory;

    @Test void permissionRolloutAndCustomerReferencesWorkOnDisposableMysql() throws Exception {
        String home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to the Windows MySQL 8 installation");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var server = new JdbcTemplate(mysql.dataSource(""));
            mysql.verifyTarget(server);
            server.execute("CREATE DATABASE " + SCHEMA);
            var source = mysql.dataSource(SCHEMA);
            var db = new JdbcTemplate(source);
            assertEquals(SCHEMA, db.queryForObject("SELECT DATABASE()", String.class));
            assertEquals(1, flyway(source, "1").migrate().migrationsExecuted);
            recordFixtureHistory(db);
            assertThrows(DataAccessException.class, () -> db.execute(script("V9__grant_customer_permissions.sql")));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM permission", Integer.class));

            assertEquals(1, runControlledStage(source, "A").migrate().migrationsExecuted);
            assertEquals(List.of("CUSTOMER_CREATE", "CUSTOMER_READ", "CUSTOMER_UPDATE"), db.queryForList("""
                    SELECT code FROM permission WHERE CAST(name AS BINARY)=CAST(code AS BINARY)
                      AND description IS NULL ORDER BY code
                    """, String.class));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM role_permission", Integer.class));
            assertThrows(DataAccessException.class, () -> db.execute(script("V8__register_customer_permissions.sql")));

            assertEquals(1, runControlledStage(source, "B").migrate().migrationsExecuted);
            assertEquals(List.of("MANAGER:CUSTOMER_READ", "SALES_STAFF:CUSTOMER_CREATE", "SALES_STAFF:CUSTOMER_READ",
                    "SALES_STAFF:CUSTOMER_UPDATE"), db.queryForList("""
                    SELECT CONCAT(r.code, ':', p.code) FROM role_permission rp
                    JOIN app_role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id ORDER BY r.code,p.code
                    """, String.class));
            assertEquals(List.of("8:151122022:1", "9:985142827:1"), db.queryForList("""
                    SELECT CONCAT(version, ':', checksum, ':', success) FROM flyway_schema_history
                    WHERE version IN ('8','9') ORDER BY installed_rank
                    """, String.class));
            assertSalesReferences(db);
        }
    }

    private static void assertSalesReferences(JdbcTemplate db) {
        db.update("""
                INSERT INTO app_user (id,username,password_hash,full_name,created_at,updated_at)
                VALUES (1,'fixture','unusable','Fixture',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        db.update("""
                INSERT INTO customer (id,code,full_name,phone,email,is_active,created_at,updated_at) VALUES
                   (7,'FIXTURE','Customer fixture','0000','fixture@example.test',FALSE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                   (8,'OTHER','Another fixture','0000','fixture@example.test',TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        db.update("""
                INSERT INTO sales_order (id,order_no,customer_id,status,created_by,created_at,updated_at) VALUES
                   (10,'REFERENCE',7,'DRAFT',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                   (11,'WALK-IN',NULL,'DRAFT',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        var before = db.queryForList("SELECT * FROM sales_order ORDER BY id");
        db.update("UPDATE customer SET full_name='Edited', code='EDITED' WHERE id=7");
        assertEquals(before, db.queryForList("SELECT * FROM sales_order ORDER BY id"));
        assertThrows(DataIntegrityViolationException.class, () -> db.update("DELETE FROM customer WHERE id=7"));
        assertThrows(DataIntegrityViolationException.class, () -> db.update("UPDATE customer SET code='EDITED' WHERE id=8"));
    }

    private static Flyway runControlledStage(MysqlDataSource source, String name) {
        var stage = ReflectionTestUtils.invokeMethod(CustomerPermissionMigrationRunner.class, "stage",
                (Object) new String[]{"--customer-permission-rollout", "--confirm-target=computer_store_dev", "--stage=" + name});
        org.flywaydb.core.api.migration.JavaMigration[] previous =
                ReflectionTestUtils.invokeMethod(CustomerPermissionMigrationRunner.class, "previousMigrations");
        FluentConfiguration configuration = ReflectionTestUtils.invokeMethod(
                CustomerPermissionMigrationRunner.class, "configuration", stage, previous);
        var flyway = configuration.dataSource(source).defaultSchema(SCHEMA).schemas(SCHEMA).load();
        ReflectionTestUtils.invokeMethod(CustomerPermissionMigrationRunner.class, "verifyResolved", flyway.info().all(), stage);
        return flyway;
    }

    private static Flyway flyway(MysqlDataSource source, String target) {
        return Flyway.configure().dataSource(source).defaultSchema(SCHEMA).schemas(SCHEMA).createSchemas(false)
                .locations("classpath:db/migration", "classpath:operations/supplier/v1", "classpath:" + CUSTOMER_MIGRATIONS)
                .javaMigrations(new V2__register_category_permissions(), new V3__grant_category_permissions(),
                        new V4__register_product_permissions(), new V5__grant_product_permissions())
                .target(target).validateOnMigrate(true).cleanDisabled(true).load();
    }

    private static void recordFixtureHistory(JdbcTemplate db) {
        // Prior stages are represented by fixture rows checked against their original migration identities.
        var descriptions = List.of("register category permissions", "grant category permissions",
                "register product permissions", "grant product permissions", "register supplier permissions", "grant supplier permissions");
        var checksums = List.of(947719693, 253774843, -1732158983, 155246154, -1416705504, 982398228);
        for (int index = 0; index < checksums.size(); index++) {
            int version = index + 2;
            db.update("""
                    INSERT INTO flyway_schema_history
                    (installed_rank,version,description,type,script,checksum,installed_by,execution_time,success)
                    VALUES (?,?,?,?,?,?, 'fixture', 0, TRUE)
                    """, version, Integer.toString(version), descriptions.get(index), version <= 5 ? "JDBC" : "SQL",
                    "fixture-v" + version, checksums.get(index));
        }
    }

    private static String script(String file) throws Exception {
        return new ClassPathResource(CUSTOMER_MIGRATIONS + "/" + file).getContentAsString(StandardCharsets.UTF_8);
    }
}
