package com.computerstore.computer_store_backend.inventory.service;

import com.computerstore.computer_store_backend.catalog.entity.Category;
import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.identity.authorization.PermissionPolicy;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.inventory.entity.Inventory;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransaction;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import com.computerstore.computer_store_backend.inventory.repository.InventoryRepository;
import com.computerstore.computer_store_backend.inventory.repository.InventoryTransactionRepository;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReadServiceTest {
    private final PermissionPolicy permissions = mock(PermissionPolicy.class);
    private final InventoryRepository inventories = mock(InventoryRepository.class);
    private final InventoryTransactionRepository movements = mock(InventoryTransactionRepository.class);
    private InventoryReadService service;

    @BeforeEach
    void setUp() {
        service = new InventoryReadService(permissions, inventories, movements);
    }

    @Test
    void balancesMapExactQuantitiesAndStockStatuses() {
        var zero = inventory(1L, "CPU-1", "CPU One", 0L, 2L);
        var low = inventory(2L, "RAM-1", "RAM One", 2L, 2L);
        var normal = inventory(3L, "SSD-1", "SSD One", 3L, 2L);
        var page = PageRequest.of(0, 20);
        when(inventories.findActiveBalances(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(zero, low, normal), page, 3));

        var result = service.balances(0, 20, InventoryReadService.BalanceFilter.ALL);

        assertEquals(3, result.items().size());
        assertEquals("0", result.items().get(0).quantityOnHand());
        assertEquals("OUT_OF_STOCK", result.items().get(0).stockStatus().name());
        assertEquals("LOW_STOCK", result.items().get(1).stockStatus().name());
        assertEquals("NORMAL", result.items().get(2).stockStatus().name());
        verify(inventories).findActiveBalances(any());
    }

    @Test
    void usesLowAndOutBalanceQueries() {
        var page = PageRequest.of(0, 20);
        when(inventories.findActiveLowStockBalances(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), page, 0));
        when(inventories.findActiveOutOfStockBalances(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), page, 0));

        service.balances(0, 20, InventoryReadService.BalanceFilter.LOW);
        service.balances(0, 20, InventoryReadService.BalanceFilter.OUT);

        verify(inventories).findActiveLowStockBalances(any());
        verify(inventories).findActiveOutOfStockBalances(any());
    }

    @Test
    void movementFiltersAndMapsSourceAndAuditFields() {
        var product = BeanUtils.instantiateClass(Product.class);
        ReflectionTestUtils.setField(product, "id", 42L);
        product.setSku("CPU-42");
        product.setName("CPU 42");

        var user = BeanUtils.instantiateClass(AppUser.class);
        user.setUsername("warehouse");

        var transaction = BeanUtils.instantiateClass(InventoryTransaction.class);
        ReflectionTestUtils.setField(transaction, "id", 77L);
        transaction.setProduct(product);
        transaction.setTransactionType(InventoryTransactionType.PURCHASE_IN);
        transaction.setQuantityDelta(10L);
        transaction.setQuantityAfter(15L);
        transaction.setOccurredAt(LocalDateTime.of(2026, 11, 27, 1, 0));
        transaction.setRecordedBy(user);
        transaction.setCreatedAt(LocalDateTime.of(2026, 11, 27, 1, 0));

        var page = PageRequest.of(0, 20);
        var from = LocalDateTime.of(2026, 11, 1, 0, 0);
        var to = LocalDateTime.of(2026, 12, 1, 0, 0);
        when(movements.search(eq(42L), eq(InventoryTransactionType.PURCHASE_IN), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transaction), page, 1));

        var result = service.movements(0, 20, "42", InventoryTransactionType.PURCHASE_IN, from, to);

        assertEquals(1, result.items().size());
        assertEquals("77", result.items().get(0).id());
        assertEquals("42", result.items().get(0).productId());
        assertEquals("10", result.items().get(0).quantityDelta());
        assertEquals("15", result.items().get(0).quantityAfter());
        assertEquals("warehouse", result.items().get(0).recordedBy());
        verify(movements).search(eq(42L), eq(InventoryTransactionType.PURCHASE_IN), eq(from), eq(to), any());
    }

    @Test
    void rejectsInvalidProductIdAndTimeRange() {
        var failure = assertThrows(BusinessException.class,
                () -> service.movements(0, 20, "0", null, null, null));
        assertEquals(ErrorCode.VALIDATION_ERROR, failure.code());

        var time = LocalDateTime.of(2026, 11, 27, 1, 0);
        var rangeFailure = assertThrows(BusinessException.class,
                () -> service.movements(0, 20, null, null, time, time));
        assertEquals(ErrorCode.VALIDATION_ERROR, rangeFailure.code());
    }

    private static Inventory inventory(long id, String sku, String name, long quantity, long threshold) {
        var product = BeanUtils.instantiateClass(Product.class);
        ReflectionTestUtils.setField(product, "id", id);
        product.setSku(sku);
        product.setName(name);
        product.setMinStockLevel(threshold);
        var inventory = BeanUtils.instantiateClass(Inventory.class);
        inventory.setProduct(product);
        inventory.setQuantityOnHand(quantity);
        return inventory;
    }
}
