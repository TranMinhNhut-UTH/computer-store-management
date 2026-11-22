package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.partners.entity.Customer;
import com.computerstore.computer_store_backend.partners.repository.CustomerRepository;
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

class CustomerRepositoryTest {
    @Test void validatesQueriesAgainstCustomerMappingsWithoutJdbc() throws Exception {
        var registry = new StandardServiceRegistryBuilder().addService(ConnectionProvider.class, new NoConnections())
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "none").build();
        var search = CustomerRepository.class.getMethod("search", String.class, Boolean.class, Pageable.class);
        var customer = CustomerRepository.class.getMethod("findForUpdate", Long.class);
        try (var sessionFactory = (SessionFactoryImplementor) new MetadataSources(registry)
                .addAnnotatedClass(Customer.class)
                .buildMetadata().buildSessionFactory()) {
            var translator = sessionFactory.getQueryEngine().getHqlTranslator();
            assertNotNull(translator.translate(search.getAnnotation(Query.class).value(), Customer.class));
            assertNotNull(translator.translate(search.getAnnotation(Query.class).countQuery(), Long.class));
            assertNotNull(translator.translate(customer.getAnnotation(Query.class).value(), Customer.class));
            assertEquals(LockModeType.PESSIMISTIC_WRITE, customer.getAnnotation(Lock.class).value());
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
