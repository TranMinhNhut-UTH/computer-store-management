package com.computerstore.computer_store_backend.catalog;

import com.computerstore.computer_store_backend.catalog.entity.Category;
import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.catalog.repository.ProductRepository;
import jakarta.persistence.LockModeType;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import static org.junit.jupiter.api.Assertions.*;

class ProductRepositoryTest {
    @Test void productQueriesCompileAgainstActualMappingsWithoutJdbc() throws Exception {
        var guard = new CategoryRepositoryTest.NoConnections();
        var registry = new StandardServiceRegistryBuilder().addService(ConnectionProvider.class, guard)
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "none").build();
        try {
            try (var factory = (SessionFactoryImplementor) new MetadataSources(registry)
                    .addAnnotatedClass(Category.class).addAnnotatedClass(Product.class).buildMetadata().buildSessionFactory()) {
                var translator = factory.getQueryEngine().getHqlTranslator();
                var search = ProductRepository.class.getMethod("search", String.class, Boolean.class, Long.class, Pageable.class)
                        .getAnnotation(Query.class);
                var detail = ProductRepository.class.getMethod("findDetail", long.class).getAnnotation(Query.class);
                var lockingRead = ProductRepository.class.getMethod("findForUpdate", long.class);
                assertNotNull(translator.translate(search.value(), Product.class));
                assertNotNull(translator.translate(search.countQuery(), Long.class));
                assertNotNull(translator.translate(detail.value(), Product.class));
                assertNotNull(translator.translate(lockingRead.getAnnotation(Query.class).value(), Product.class));
                assertEquals(LockModeType.PESSIMISTIC_WRITE, lockingRead.getAnnotation(Lock.class).value());
            }
            assertEquals(0, guard.attempts);
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
