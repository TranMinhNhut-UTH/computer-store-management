package com.computerstore.computer_store_backend.inventory.service;

import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.inventory.entity.Inventory;
import com.computerstore.computer_store_backend.inventory.repository.InventoryRepository;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(InventoryLifecycleServiceTest.Fixture.class)
class InventoryLifecycleServiceTest {
    private static final LocalDateTime EXPECTED_TIME = LocalDateTime.of(2026, 11, 25, 16, 0);

    @Autowired private InventoryLifecycleService service;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private Transactions transactions;

    @BeforeEach
    void setUp() {
        reset(inventoryRepository);
        transactions.begins = 0;
        transactions.commits = 0;
        transactions.rollbacks = 0;
    }

    @Test
    void initializesExactlyOneZeroBalanceRowWithUtcTimestamps() {
        Product product = BeanUtils.instantiateClass(Product.class);
        ReflectionTestUtils.setField(product, "id", 42L);
        when(inventoryRepository.existsByProductId(42L)).thenReturn(false);
        when(inventoryRepository.saveAndFlush(any(Inventory.class))).thenAnswer(invocation -> {
            assertWriteTransaction();
            return invocation.getArgument(0);
        });

        Inventory result = service.initializeForNewProduct(product);

        assertSame(product, result.getProduct());
        assertEquals(0L, result.getQuantityOnHand());
        assertEquals(EXPECTED_TIME, result.getCreatedAt());
        assertEquals(EXPECTED_TIME, result.getUpdatedAt());
        verify(inventoryRepository).existsByProductId(42L);
        verify(inventoryRepository).saveAndFlush(any(Inventory.class));
        assertEquals(1, transactions.begins);
        assertEquals(1, transactions.commits);
        assertEquals(0, transactions.rollbacks);
    }

    @Test
    void joinsProductCreationTransactionAndPropagatesInitializationFailureForRollback() {
        Product product = BeanUtils.instantiateClass(Product.class);
        ReflectionTestUtils.setField(product, "id", 42L);
        when(inventoryRepository.existsByProductId(42L)).thenReturn(false);
        when(inventoryRepository.saveAndFlush(any(Inventory.class))).thenAnswer(invocation -> {
            assertWriteTransaction();
            throw new IllegalStateException("Inventory initialization failed.");
        });

        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions)
                .execute(status -> {
                    assertWriteTransaction();
                    service.initializeForNewProduct(product);
                    return null;
                }));

        assertEquals(1, transactions.begins);
        assertEquals(0, transactions.commits);
        assertEquals(1, transactions.rollbacks);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void rejectsASecondInventoryRowBeforeWriting() {
        Product product = BeanUtils.instantiateClass(Product.class);
        ReflectionTestUtils.setField(product, "id", 42L);
        when(inventoryRepository.existsByProductId(42L)).thenReturn(true);

        var failure = assertThrows(com.computerstore.computer_store_backend.shared.error.BusinessException.class,
                () -> service.initializeForNewProduct(product));

        assertEquals(com.computerstore.computer_store_backend.shared.error.ErrorCode.BUSINESS_CONFLICT,
                failure.code());
        org.mockito.Mockito.verify(inventoryRepository, org.mockito.Mockito.never())
                .saveAndFlush(any(Inventory.class));
    }

    @Test
    void rejectsMissingPersistedProductIdBeforeWriting() {
        Product product = BeanUtils.instantiateClass(Product.class);

        var failure = assertThrows(IllegalStateException.class,
                () -> service.initializeForNewProduct(product));

        assertEquals("Inventory requires an already-persisted Product.", failure.getMessage());
        org.mockito.Mockito.verifyNoInteractions(inventoryRepository);
    }

    @Test
    void mapsUniqueConstraintRaceToBusinessConflict() {
        Product product = BeanUtils.instantiateClass(Product.class);
        ReflectionTestUtils.setField(product, "id", 42L);
        when(inventoryRepository.existsByProductId(42L)).thenReturn(false);
        when(inventoryRepository.saveAndFlush(any(Inventory.class)))
                .thenThrow(new DataIntegrityViolationException("uq_inventory_product"));

        var failure = assertThrows(com.computerstore.computer_store_backend.shared.error.BusinessException.class,
                () -> service.initializeForNewProduct(product));

        assertEquals(com.computerstore.computer_store_backend.shared.error.ErrorCode.BUSINESS_CONFLICT,
                failure.code());
    }

    private static void assertWriteTransaction() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(InventoryLifecycleService.class)
    static class Fixture {
        @Bean
        InventoryRepository inventoryRepository() {
            return org.mockito.Mockito.mock(InventoryRepository.class);
        }

        @Bean
        UtcTimeProvider utcTimeProvider() {
            return new UtcTimeProvider(Clock.fixed(
                    Instant.parse("2026-11-25T16:00:00Z"), ZoneOffset.UTC));
        }

        @Bean
        Transactions transactionManager() {
            return new Transactions();
        }
    }

    static class Transactions extends AbstractPlatformTransactionManager {
        private final ThreadLocal<Marker> current = new ThreadLocal<>();
        int begins;
        int commits;
        int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return current.get() == null ? new Marker() : current.get();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((Marker) transaction).active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            ((Marker) transaction).active = true;
            current.set((Marker) transaction);
            begins++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            ((Marker) status.getTransaction()).rollbackOnly = true;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            ((Marker) transaction).active = false;
            current.remove();
        }

        private static class Marker {
            boolean active;
            boolean rollbackOnly;
        }
    }
}
