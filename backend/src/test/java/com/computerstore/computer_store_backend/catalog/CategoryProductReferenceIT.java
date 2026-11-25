package com.computerstore.computer_store_backend.catalog;

import com.computerstore.computer_store_backend.catalog.controller.CategoryController;
import com.computerstore.computer_store_backend.catalog.dto.request.ProductPriceRequest;
import com.computerstore.computer_store_backend.catalog.dto.request.ProductWriteRequest;
import com.computerstore.computer_store_backend.catalog.entity.Category;
import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.catalog.repository.CategoryRepository;
import com.computerstore.computer_store_backend.catalog.repository.ProductRepository;
import com.computerstore.computer_store_backend.catalog.service.CategoryService;
import com.computerstore.computer_store_backend.catalog.service.ProductService;
import com.computerstore.computer_store_backend.identity.authorization.AuthorizationService;
import com.computerstore.computer_store_backend.identity.authorization.CurrentActor;
import com.computerstore.computer_store_backend.identity.authorization.EmployeeRole;
import com.computerstore.computer_store_backend.identity.authorization.PermissionPolicy;
import com.computerstore.computer_store_backend.identity.authorization.ResolvedAccess;
import com.computerstore.computer_store_backend.identity.authorization.RoleResolutionService;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.security.ApiAccessDeniedHandler;
import com.computerstore.computer_store_backend.identity.security.ApiAuthenticationEntryPoint;
import com.computerstore.computer_store_backend.identity.security.EmployeeUserDetailsService;
import com.computerstore.computer_store_backend.identity.security.SecurityConfig;
import com.computerstore.computer_store_backend.shared.error.GlobalExceptionHandler;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import com.mysql.cj.jdbc.MysqlDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.mock.web.MockServletContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Opt in with -Dtest=CategoryProductReferenceIT and -Dcsm.mysql.home=<MySQL installation>.
 * Starts an owned Windows MySQL process on a unique named pipe with TCP disabled.
 */
class CategoryProductReferenceIT {
    private static final String SCHEMA = "csm_category_conflict_it";
    private static final String MANAGER = "category-fixture-manager";

    @TempDir Path directory;

    @Test void linkedProductBlocksDeactivationAndBothCommittedRowsRemainUnchanged() throws Exception {
        String home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to an installed Windows MySQL server");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var datasource = mysql.createSchema();
            var jdbc = new JdbcTemplate(datasource);
            var transactions = new TransactionTemplate(new JdbcTransactionManager(datasource));
            transactions.executeWithoutResult(status -> {
                jdbc.update("""
                        INSERT INTO category (id, code, name, is_active, created_at, updated_at)
                        VALUES (7, 'IT-CATEGORY', 'Integration category', TRUE, '2026-01-01', '2026-01-02')
                        """);
                jdbc.update("""
                        INSERT INTO product (id, sku, name, category_id, selling_price, min_stock_level,
                                             is_active, created_at, updated_at)
                        VALUES (11, 'IT-PRODUCT', 'Integration product', 7, 1000, 3, TRUE,
                                '2026-01-01', '2026-01-02')
                        """);
            });
            transactions.setReadOnly(true);
            Snapshot before = transactions.execute(status -> snapshot(jdbc));

            try (var context = application(datasource)) {
                var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
                mvc.perform(post("/api/v1/categories/7/deactivate").with(user(MANAGER).roles("MANAGER")).with(csrf()))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"))
                        .andExpect(jsonPath("$.detail").value("Category deactivation is unavailable while products reference it."));
                verify(context.getBean(RoleResolutionService.class)).resolveCurrent();
            }

            // New JDBC transaction after the request, outside its JPA persistence context.
            Snapshot after = transactions.execute(status -> snapshot(jdbc));
            assertNotNull(after);
            assertEquals(Boolean.TRUE, after.categories().getFirst().get("is_active"));
            assertEquals(7L, ((Number) after.products().getFirst().get("category_id")).longValue());
            assertEquals(before, after, "Both tables must retain exactly their committed fixture rows and column values");
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class));
        }
    }

    @Test void productCommandsPreserveChangesCommittedAfterTheirTransactionLoadedTheRow() throws Exception {
        String home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to an installed Windows MySQL server");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var source = mysql.createSchema();
            var external = new JdbcTemplate(mysql.dataSource(SCHEMA));
            external.update("""
                    INSERT INTO category (id, code, name, created_at, updated_at) VALUES
                    (7, 'START', 'Original category', '2026-01-01', '2026-01-02'),
                    (8, 'OTHER', 'Unlinked category', '2026-01-01', '2026-01-02')
                    """);
            external.update("""
                    INSERT INTO product (id, sku, name, category_id, selling_price, created_at, updated_at)
                    VALUES (11, 'IT', 'Original Product', 7, 1000, '2026-01-01', '2026-01-02')
                    """);
            try (var context = application(source)) {
                var tx = new TransactionTemplate(context.getBean(JpaTransactionManager.class));
                var products = context.getBean(ProductRepository.class);
                var categories = context.getBean(CategoryRepository.class);
                var service = context.getBean(ProductService.class);
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        MANAGER, null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
                try {
                    tx.executeWithoutResult(status -> {
                        assertEquals("1000", products.findById(11L).orElseThrow().getSellingPrice().toPlainString());
                        external.update("UPDATE product SET selling_price=9999999999999999999, is_active=FALSE WHERE id=11");
                        var result = service.update("11", new ProductWriteRequest("IT", "Edited", "7", null, null, null, "0"));
                        assertEquals("9999999999999999999", result.sellingPrice());
                        assertFalse(result.active());
                    });
                    tx.executeWithoutResult(status -> {
                        assertEquals("Edited", products.findById(11L).orElseThrow().getName());
                        external.update("UPDATE product SET name='Concurrent rename' WHERE id=11");
                        var result = service.updatePrice("11", new ProductPriceRequest("9007199254740993"));
                        assertEquals("Concurrent rename", result.name());
                        assertEquals("9007199254740993", result.sellingPrice());
                    });
                    var failure = assertThrows(BusinessException.class, () -> tx.executeWithoutResult(status -> {
                        assertTrue(categories.findById(8L).orElseThrow().getActive());
                        external.update("UPDATE category SET is_active=FALSE WHERE id=8");
                        service.update("11", new ProductWriteRequest("IT", "Rejected", "8", null, null, null, "0"));
                    }));
                    assertEquals(ErrorCode.BUSINESS_CONFLICT, failure.code());
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }
            assertEquals("Concurrent rename", external.queryForObject("SELECT name FROM product WHERE id=11", String.class));
            assertEquals("9007199254740993", external.queryForObject(
                    "SELECT selling_price FROM product WHERE id=11", java.math.BigDecimal.class).toPlainString());
            assertEquals(7L, external.queryForObject("SELECT category_id FROM product WHERE id=11", Long.class));
        }
    }

    private static GenericWebApplicationContext application(DataSource source) {
        var context = new GenericWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.registerBean(DataSource.class, () -> source);
        new AnnotatedBeanDefinitionReader(context).register(Fixture.class);
        context.refresh();
        return context;
    }

    private static Snapshot snapshot(JdbcTemplate jdbc) {
        return new Snapshot(jdbc.queryForList("SELECT * FROM category ORDER BY id"),
                jdbc.queryForList("SELECT * FROM product ORDER BY id"));
    }

    private record Snapshot(List<Map<String, Object>> categories, List<Map<String, Object>> products) { }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc @EnableWebSecurity @EnableTransactionManagement(proxyTargetClass = true)
    @Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
            EmployeeUserDetailsService.class, CategoryController.class, CategoryService.class, ProductService.class,
            GlobalExceptionHandler.class, AuthorizationService.class, PermissionPolicy.class})
    static class Fixture {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource datasource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(datasource);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(
                    Category.class.getName(), Product.class.getName(), AppUser.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.jdbc.time_zone", "UTC"));
            return factory;
        }

        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean EntityManager entities(EntityManagerFactory factory) {
            return SharedEntityManagerCreator.createSharedEntityManager(factory);
        }

        @Bean JpaRepositoryFactory repositories(EntityManager entities) {
            return new JpaRepositoryFactory(entities);
        }

        @Bean ProductRepository products(JpaRepositoryFactory factory) {
            return factory.getRepository(ProductRepository.class);
        }

        @Bean CategoryRepository categories(JpaRepositoryFactory factory) {
            return factory.getRepository(CategoryRepository.class);
        }

        @Bean AppUserRepository users(JpaRepositoryFactory factory) {
            return factory.getRepository(AppUserRepository.class);
        }

        @Bean RoleResolutionService roles() {
            // Supply the role snapshot at the identity boundary. No account or grant rows are needed.
            var resolver = mock(RoleResolutionService.class);
            when(resolver.resolveCurrent()).thenAnswer(call -> {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                assertNotNull(auth);
                assertTrue(auth.isAuthenticated());
                assertEquals(MANAGER, auth.getName());
                assertTrue(auth.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_MANAGER")));
                return new ResolvedAccess(new CurrentActor(1L, MANAGER), Set.of(EmployeeRole.MANAGER),
                        Set.of("CATEGORY_DEACTIVATE", "PRODUCT_UPDATE", "PRODUCT_PRICE_UPDATE"));
            });
            return resolver;
        }

        @Bean UtcTimeProvider time() {
            return new UtcTimeProvider(Clock.fixed(Instant.parse("2026-01-03T00:00:00Z"), ZoneOffset.UTC));
        }
    }

    private record DisposableMysql(Process process, Path data, String pipe, Path log) implements AutoCloseable {
        static DisposableMysql start(Path home, Path directory) throws Exception {
            Path executable = home.resolve("bin/mysqld.exe");
            assertTrue(Files.isRegularFile(executable), "Installed mysqld.exe required");
            Path data = Files.createDirectory(directory.resolve("data"));
            Path initLog = directory.resolve("initialize.log");
            Process init = launch(executable, home, data, initLog, "--initialize-insecure");
            try {
                assertTrue(init.waitFor(60, TimeUnit.SECONDS), "Disposable MySQL initialization timed out");
                assertEquals(0, init.exitValue(), () -> readLog(initLog));
            } finally {
                if (init.isAlive()) init.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            }

            String pipe = "csm-category-it-" + UUID.randomUUID();
            Path log = directory.resolve("server.log");
            var mysql = new DisposableMysql(launch(executable, home, data, log, "--skip-networking",
                    "--named-pipe", "--socket=" + pipe, "--mysqlx=OFF", "--pid-file=" + directory.resolve("server.pid")),
                    data, pipe, log);
            try {
                mysql.awaitReady();
                return mysql;
            } catch (Exception | AssertionError failure) {
                mysql.stopProcess();
                throw failure;
            }
        }

        private static Process launch(Path executable, Path home, Path data, Path log, String... options)
                throws java.io.IOException {
            var command = new java.util.ArrayList<>(List.of(executable.toString(), "--no-defaults",
                    "--basedir=" + home, "--datadir=" + data, "--console"));
            command.addAll(List.of(options));
            return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        }

        private void awaitReady() throws Exception {
            var jdbc = new JdbcTemplate(dataSource(null));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (process.isAlive() && System.nanoTime() < deadline) {
                try {
                    verifyTarget(jdbc);
                    return;
                } catch (org.springframework.jdbc.CannotGetJdbcConnectionException notReady) {
                    Thread.sleep(200);
                }
            }
            fail("Disposable MySQL did not start: " + readLog(log));
        }

        DataSource createSchema() throws Exception {
            var server = new JdbcTemplate(dataSource(null));
            verifyTarget(server);
            server.execute("CREATE DATABASE " + SCHEMA);
            var datasource = dataSource(SCHEMA);
            var jdbc = new JdbcTemplate(datasource);
            assertEquals(SCHEMA, jdbc.queryForObject("SELECT DATABASE()", String.class));
            var v1 = new ClassPathResource("db/migration/V1__create_initial_schema.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            // Reuse only the relevant V1 table definitions, without invoking Flyway or copying its DDL.
            for (String table : List.of("app_user", "category", "product")) {
                var ddl = Pattern.compile("(?ms)^CREATE TABLE " + table + " \\(.*?;").matcher(v1);
                assertTrue(ddl.find(), "Missing V1 definition for " + table);
                jdbc.execute(ddl.group());
            }
            return datasource;
        }

        private void verifyTarget(JdbcTemplate jdbc) throws java.io.IOException {
            var state = jdbc.queryForMap("SELECT @@datadir AS datadir, @@socket AS pipe, @@skip_networking AS offline");
            assertEquals(data.toRealPath(), Path.of(state.get("datadir").toString()).toRealPath());
            assertEquals(pipe, state.get("pipe"));
            assertEquals("1", state.get("offline").toString());
        }

        private MysqlDataSource dataSource(String schema) {
            var datasource = new MysqlDataSource();
            String pipePath = URLEncoder.encode("\\\\.\\pipe\\" + pipe, StandardCharsets.UTF_8);
            datasource.setURL("jdbc:mysql://127.0.0.1/" + (schema == null ? "" : schema)
                    + "?socketFactory=com.mysql.cj.protocol.NamedPipeSocketFactory&path=" + pipePath
                    + "&sslMode=DISABLED&connectTimeout=1000");
            datasource.setUser("root");
            datasource.setPassword("");
            return datasource;
        }

        @Override public void close() throws Exception {
            try {
                if (process.isAlive()) {
                    var jdbc = new JdbcTemplate(dataSource(null));
                    verifyTarget(jdbc);
                    try { jdbc.execute("SHUTDOWN"); }
                    catch (org.springframework.dao.DataAccessException disconnected) {
                        // The server may close the connection as part of shutdown.
                        if (!process.waitFor(5, TimeUnit.SECONDS)) throw disconnected;
                    }
                }
            } finally {
                stopProcess();
            }
        }

        private void stopProcess() throws InterruptedException {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                assertTrue(process.destroyForcibly().waitFor(10, TimeUnit.SECONDS), "Disposable MySQL did not stop");
            }
        }

        private static String readLog(Path path) {
            try { return Files.readString(path); }
            catch (java.io.IOException failure) { return "Disposable MySQL log unavailable"; }
        }
    }
}
