package com.computerstore.computer_store_backend.catalog;

import com.computerstore.computer_store_backend.catalog.entity.Category;
import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.catalog.repository.CategoryRepository;
import jakarta.persistence.LockModeType;
import java.sql.Connection;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import static org.junit.jupiter.api.Assertions.*;

class CategoryRepositoryTest {
    @Test void queriesMatchRealCategoryAndProductMetadataWithoutJdbc() {
        var noConnections = new NoConnections();
        var registry = new StandardServiceRegistryBuilder().addService(ConnectionProvider.class,noConnections)
                .applySetting("hibernate.boot.allow_jdbc_metadata_access",false)
                .applySetting("hibernate.dialect","org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.hbm2ddl.auto","none").build();
        try {
            try (var factory = (SessionFactoryImplementor) new MetadataSources(registry)
                    .addAnnotatedClass(Category.class).addAnnotatedClass(Product.class).buildMetadata().buildSessionFactory()) {
                var translator = factory.getQueryEngine().getHqlTranslator();
                assertNotNull(translator.translate(CategoryRepository.SEARCH_QUERY,Category.class));
                assertNotNull(translator.translate(CategoryRepository.COUNT_QUERY,Long.class));
                assertNotNull(translator.translate(CategoryRepository.LOCK_QUERY,Category.class));
                assertNotNull(translator.translate(CategoryRepository.REFERENCES_QUERY,Product.class));
            }
            assertEquals(0,noConnections.attempts);
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }

    @Test void mutationAndReferenceReadsDeclareLocksInsteadOfUsingAStaleScalarCount() throws Exception {
        assertEquals(LockModeType.PESSIMISTIC_WRITE,
                CategoryRepository.class.getMethod("findForUpdate",Long.class).getAnnotation(Lock.class).value());
        assertEquals(LockModeType.PESSIMISTIC_READ,
                CategoryRepository.class.getMethod("findProductReferencesForDeactivation",Long.class,Pageable.class)
                        .getAnnotation(Lock.class).value());
    }

    static class NoConnections implements ConnectionProvider {
        int attempts;
        @Override public Connection getConnection() { attempts++; throw new AssertionError("JDBC forbidden"); }
        @Override public void closeConnection(Connection connection) { throw new AssertionError("No connection expected"); }
        @Override public boolean supportsAggressiveRelease() { return false; }
        @Override public boolean isUnwrappableAs(Class<?> type) { return type.isInstance(this); }
        @Override public <T> T unwrap(Class<T> type) { return type.cast(this); }
    }
}
