package com.computerstore.computer_store_backend.operations.category.v1;

import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.JavaMigration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class CategoryMigrationRunnerTest {
    @Test void noDefaultStageOrAmbiguousOptInCanReachConfiguration() {
        for (var args : new String[][] { {}, {"--category-permission-rollout"},
                {"--category-permission-rollout", "--stage=A", "--confirm-target=computer_store"},
                {"--category-permission-rollout", "--stage=A", "--stage=B"},
                {"--category-permission-rollout", "--stage=A", "--confirm-target=computer_store_dev", "--extra"} }) {
            assertThrows(IllegalArgumentException.class, () -> CategoryPermissionMigrationRunner.stage(args));
        }
        assertThrows(IllegalArgumentException.class, () -> CategoryPermissionMigrationRunner.stage(null));
        assertEquals(CategoryPermissionMigrationRunner.Stage.A, CategoryPermissionMigrationRunner.stage(
                new String[]{"--category-permission-rollout", "--stage=A", "--confirm-target=computer_store_dev"}));
        assertEquals(CategoryPermissionMigrationRunner.Stage.B, CategoryPermissionMigrationRunner.stage(
                new String[]{"--stage=B", "--confirm-target=computer_store_dev", "--category-permission-rollout"}));
    }

    @Test void explicitStageRegistersOnlyItsVersionedMigrationsWithoutConnectingOrExecutingFlyway() {
        var a = CategoryPermissionMigrationRunner.configuration(CategoryPermissionMigrationRunner.Stage.A);
        var b = CategoryPermissionMigrationRunner.configuration(CategoryPermissionMigrationRunner.Stage.B);
        assertEquals("2", a.getTarget().toString()); assertEquals("3", b.getTarget().toString());
        assertEquals(1, a.getJavaMigrations().length); assertEquals(2, b.getJavaMigrations().length);
        assertInstanceOf(V2__register_category_permissions.class, a.getJavaMigrations()[0]);
        assertInstanceOf(V3__grant_category_permissions.class, b.getJavaMigrations()[1]);
        for (var config : new org.flywaydb.core.api.configuration.FluentConfiguration[]{a, b}) {
            assertFalse(config.isBaselineOnMigrate()); assertFalse(config.isOutOfOrder()); assertFalse(config.isCreateSchemas());
            assertFalse(config.isGroup()); assertTrue(config.isCleanDisabled()); assertTrue(config.isValidateOnMigrate());
            assertTrue(config.isExecuteInTransaction());
            assertEquals("computer_store_dev", config.getDefaultSchema());
            assertArrayEquals(new String[]{"computer_store_dev"}, config.getSchemas());
            assertEquals("classpath:db/migration", config.getLocations()[0].toString());
            assertNull(config.getDataSource());
        }
    }

    @Test void normalApplicationLocationAndComponentScanCannotAutomaticallyRegisterTheseMigrations() throws Exception {
        var properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/application.properties")) { properties.load(stream); }
        assertEquals("classpath:db/migration", properties.getProperty("spring.flyway.locations"));
        assertEquals("false", properties.getProperty("spring.flyway.enabled"));
        assertNotNull(getClass().getResource("/db/migration/V1__create_initial_schema.sql"));
        assertFalse(V2__register_category_permissions.class.getPackageName().startsWith("db.migration"));
        assertEquals(0, Flyway.configure().getJavaMigrations().length);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.scan(CategoryPermissionMigrationRunner.class.getPackageName()); context.refresh();
            assertTrue(context.getBeansOfType(JavaMigration.class).isEmpty());
            assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
            assertTrue(context.getBeansOfType(org.springframework.boot.CommandLineRunner.class).isEmpty());
        }
    }

    @Test void environmentValidationRejectsMissingCredentialsAndUnsafeUrlWithoutEchoingValues() throws Exception {
        String url = "jdbc:mysql://127.0.0.1:3306/computer_store_dev?serverTimezone=UTC";
        CategoryPermissionMigrationRunner.validateEnvironment(Map.of("DB_URL", url, "DB_USERNAME", "fixture", "DB_PASSWORD", "fixture-only"));
        assertThrows(IllegalArgumentException.class, () -> CategoryPermissionMigrationRunner.validateEnvironment(Map.of("DB_URL", url)));
        for (var unsafe : new String[]{url + "&serverTimezone=UTC", url + "&sessionVariables=unsafe", "jdbc:mysql://fixture:SECRET@127.0.0.1/computer_store_dev"}) {
            var error = assertThrows(SQLException.class, () -> CategoryPermissionMigrationRunner.validateEnvironment(
                    Map.of("DB_URL", unsafe, "DB_USERNAME", "fixture", "DB_PASSWORD", "SECRET")));
            assertFalse(error.getMessage().contains("SECRET")); assertNull(error.getCause());
        }
    }
}
