package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.partners.entity.Supplier;
import com.computerstore.computer_store_backend.partners.repository.SupplierRepository;
import com.computerstore.computer_store_backend.purchasing.entity.Purchase;
import com.computerstore.computer_store_backend.purchasing.entity.PurchaseStatus;
import jakarta.persistence.LockModeType;
import java.sql.Connection;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import static org.junit.jupiter.api.Assertions.*;

class SupplierRepositoryTest {
    @Test void validatesQueriesAgainstSupplierAndPurchaseMappingsWithoutJdbc() throws Exception {
        var registry = new StandardServiceRegistryBuilder().addService(ConnectionProvider.class, new NoConnections())
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "none").build();
        var search = SupplierRepository.class.getMethod("search", String.class, Boolean.class, Pageable.class);
        var supplier = SupplierRepository.class.getMethod("findForUpdate", Long.class);
        var purchases = SupplierRepository.class.getMethod("findPurchases", Long.class, PurchaseStatus.class, Pageable.class);
        try (var sessionFactory = (SessionFactoryImplementor) new MetadataSources(registry)
                .addAnnotatedClass(Supplier.class).addAnnotatedClass(Purchase.class).addAnnotatedClass(AppUser.class)
                .buildMetadata().buildSessionFactory()) {
            var translator = sessionFactory.getQueryEngine().getHqlTranslator();
            assertNotNull(translator.translate(search.getAnnotation(Query.class).value(), Supplier.class));
            assertNotNull(translator.translate(search.getAnnotation(Query.class).countQuery(), Long.class));
            assertNotNull(translator.translate(supplier.getAnnotation(Query.class).value(), Supplier.class));
            assertNotNull(translator.translate(purchases.getAnnotation(Query.class).value(), Purchase.class));
            assertEquals(LockModeType.PESSIMISTIC_WRITE, supplier.getAnnotation(Lock.class).value());
            assertEquals(LockModeType.PESSIMISTIC_READ, purchases.getAnnotation(Lock.class).value());
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static final class NoConnections implements ConnectionProvider {
        @Override public Connection getConnection() { throw new AssertionError("JDBC is forbidden in this test"); }
        @Override public void closeConnection(Connection connection) { throw new AssertionError("No connection expected"); }
        @Override public boolean supportsAggressiveRelease() { return false; }
        @Override public boolean isUnwrappableAs(Class<?> type) { return type.isInstance(this); }
        @Override public <T> T unwrap(Class<T> type) { return type.cast(this); }
    }
}
