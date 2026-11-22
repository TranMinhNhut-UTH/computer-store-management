package com.computerstore.computer_store_backend.operations.product.v1;

import com.computerstore.computer_store_backend.operations.category.v1.V2__register_category_permissions;
import com.computerstore.computer_store_backend.operations.category.v1.V3__grant_category_permissions;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.migration.JavaMigration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static com.computerstore.computer_store_backend.operations.product.v1.ProductPermissionMigrationRunner.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductPermissionMigrationRunnerTest {
    @Test void exactOptInNeverSelectsADefaultStage() {
        assertThrows(IllegalArgumentException.class, () -> stage(null));
        for (String[] args : List.of(new String[]{}, new String[]{"--product-permission-rollout", "--stage=A"},
                new String[]{"--product-permission-rollout", "--stage=B", "--confirm-target=computer_store"},
                new String[]{"--category-permission-rollout", "--stage=A", "--confirm-target=computer_store_dev"},
                new String[]{"--product-permission-rollout", "--stage=A", "--stage=B"},
                new String[]{"--product-permission-rollout", "--stage=A", "--confirm-target=computer_store_dev", "--extra"})) {
            assertThrows(IllegalArgumentException.class, () -> stage(args));
        }
        assertEquals(Stage.A, stage(new String[]{"--product-permission-rollout", "--stage=A", "--confirm-target=computer_store_dev"}));
        assertEquals(Stage.B, stage(new String[]{"--confirm-target=computer_store_dev", "--stage=B", "--product-permission-rollout"}));
        assertThrows(IllegalArgumentException.class, () -> configuration(null));
    }

    @Test void targetAndEnvironmentAreCheckedWithoutConnecting() throws Exception {
        validateEnvironment(Map.of("DB_URL", FrozenProductRolloutV1.URL, "DB_USERNAME", "fixture", "DB_PASSWORD", "fixture-only"));
        for (String url : List.of("jdbc:mysql://localhost:3306/computer_store_dev",
                "jdbc:mysql://127.0.0.1:3307/computer_store_dev",
                "jdbc:mysql://127.0.0.1:3306/computer_store",
                "jdbc:mysql://127.0.0.1/computer_store_dev",
                FrozenProductRolloutV1.URL + "?password=test-sentinel")) {
            var failure = assertThrows(SQLException.class, () -> validateEnvironment(Map.of("DB_URL", url)));
            assertFalse(failure.getMessage().contains("test-sentinel"));
        }
        assertThrows(SQLException.class, () -> validateEnvironment(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> validateEnvironment(Map.of("DB_URL", FrozenProductRolloutV1.URL)));
        assertThrows(IllegalArgumentException.class, () -> validateEnvironment(Map.of(
                "DB_URL", FrozenProductRolloutV1.URL, "DB_USERNAME", "fixture", "DB_PASSWORD", " ")));
    }

    @Test void stageRegistrationAllowsOnlyTheSelectedMigrationToExecute() {
        var a = configuration(Stage.A);
        var b = configuration(Stage.B);
        assertEquals(List.of("2", "3", "4"), Arrays.stream(a.getJavaMigrations()).map(m -> m.getVersion().toString()).toList());
        assertEquals(List.of("2", "3", "4", "5"), Arrays.stream(b.getJavaMigrations()).map(m -> m.getVersion().toString()).toList());
        assertInstanceOf(V2__register_category_permissions.class, a.getJavaMigrations()[0]);
        assertInstanceOf(V3__grant_category_permissions.class, a.getJavaMigrations()[1]);
        assertEquals("4", a.getTarget().toString());
        assertEquals("5", b.getTarget().toString());
        for (var config : List.of(a, b)) {
            assertTrue(config.isValidateOnMigrate() && config.isExecuteInTransaction() && config.isCleanDisabled());
            assertFalse(config.isCreateSchemas() || config.isBaselineOnMigrate() || config.isOutOfOrder() || config.isGroup());
            assertNull(config.getDataSource());
            var callback = config.getCallbacks()[0];
            var context = mock(Context.class);
            var migration = mock(MigrationInfo.class);
            when(context.getMigrationInfo()).thenReturn(migration);
            assertTrue(callback.supports(Event.BEFORE_EACH_MIGRATE, context));
            assertFalse(callback.supports(Event.AFTER_MIGRATE, context));
            for (String version : List.of("1", "2", "3", "4", "5", "6")) {
                when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
                if (version.equals(config.getTarget().toString())) callback.handle(Event.BEFORE_EACH_MIGRATE, context);
                else assertThrows(FlywayException.class, () -> callback.handle(Event.BEFORE_EACH_MIGRATE, context));
            }
        }
    }

    @Test void versionsChecksumsAndDefaultDiscoveryAreStable() throws Exception {
        var a = new V4__register_product_permissions();
        var b = new V5__grant_product_permissions();
        assertEquals("4", a.getVersion().toString());
        assertEquals("5", b.getVersion().toString());
        assertEquals("register product permissions", a.getDescription());
        assertEquals("grant product permissions", b.getDescription());
        assertEquals(-1732158983, a.getChecksum());
        assertEquals(155246154, b.getChecksum());
        assertTrue(a.canExecuteInTransaction() && b.canExecuteInTransaction());
        assertEquals(947719693, new V2__register_category_permissions().getChecksum());
        assertEquals(253774843, new V3__grant_category_permissions().getChecksum());
        var v1 = new org.flywaydb.core.internal.resource.classpath.ClassPathResource(new Location("classpath:db/migration"),
                "db/migration/V1__create_initial_schema.sql", getClass().getClassLoader(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(-1142478933, org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(v1));

        var properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/application.properties")) { properties.load(stream); }
        assertEquals("classpath:db/migration", properties.getProperty("spring.flyway.locations"));
        assertFalse(a.getClass().getPackageName().startsWith("db.migration"));
        assertEquals(0, Flyway.configure().getJavaMigrations().length);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.scan(ProductPermissionMigrationRunner.class.getPackageName());
            context.refresh();
            assertTrue(context.getBeansOfType(JavaMigration.class).isEmpty());
            assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
            assertTrue(context.getBeansOfType(org.springframework.boot.CommandLineRunner.class).isEmpty());
        }
    }
}
