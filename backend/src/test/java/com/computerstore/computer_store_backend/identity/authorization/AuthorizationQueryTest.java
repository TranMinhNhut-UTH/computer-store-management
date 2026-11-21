package com.computerstore.computer_store_backend.identity.authorization;

import com.computerstore.computer_store_backend.identity.entity.AppRole;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.entity.Permission;
import com.computerstore.computer_store_backend.identity.entity.RolePermission;
import com.computerstore.computer_store_backend.identity.entity.UserRole;
import java.sql.Connection;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Parses the actual JPQL against real entity metadata. JDBC access and schema actions are prohibited. */
class AuthorizationQueryTest {
    @Test void scalarQueriesMatchActualEntityFieldsWithoutOpeningAnyDatabaseConnection() {
        var noConnections = new NoConnections();
        var registry = new StandardServiceRegistryBuilder()
                .addService(ConnectionProvider.class, noConnections)
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.show_sql", false).build();
        try {
            try (var factory = (SessionFactoryImplementor) new MetadataSources(registry)
                    .addAnnotatedClass(AppUser.class).addAnnotatedClass(AppRole.class)
                    .addAnnotatedClass(UserRole.class).addAnnotatedClass(RolePermission.class)
                    .addAnnotatedClass(Permission.class).buildMetadata().buildSessionFactory()) {
                var translator = factory.getQueryEngine().getHqlTranslator();
                assertNotNull(translator.translate(RoleResolutionService.ROLES_QUERY, String.class));
                assertNotNull(translator.translate(RoleResolutionService.PERMISSIONS_QUERY, String.class));
            }
            assertEquals(0, noConnections.attempts);
        } finally {
            org.hibernate.boot.registry.StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    static class NoConnections implements ConnectionProvider {
        int attempts;
        @Override public Connection getConnection() { attempts++; throw new AssertionError("JDBC is forbidden in this isolated test"); }
        @Override public void closeConnection(Connection connection) { throw new AssertionError("No JDBC connection should exist"); }
        @Override public boolean supportsAggressiveRelease() { return false; }
        @Override public boolean isUnwrappableAs(Class<?> type) { return type.isInstance(this); }
        @Override public <T> T unwrap(Class<T> type) { return type.cast(this); }
    }
}
