package com.computerstore.computer_store_backend.inventory.service;

import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.identity.authorization.CurrentActor;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.inventory.entity.Inventory;
import com.computerstore.computer_store_backend.inventory.entity.InventoryAdjustmentStatus;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransaction;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import com.computerstore.computer_store_backend.inventory.entity.StocktakeStatus;
import com.computerstore.computer_store_backend.inventory.repository.InventoryAdjustmentItemRepository;
import com.computerstore.computer_store_backend.inventory.repository.InventoryRepository;
import com.computerstore.computer_store_backend.inventory.repository.InventoryTransactionRepository;
import com.computerstore.computer_store_backend.inventory.repository.StocktakeItemRepository;
import com.computerstore.computer_store_backend.purchasing.entity.PurchaseItem;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Isolated test cho InventoryOperationsServiceImpl.applyPurchaseIn() — mock toàn bộ
 * repository/EntityManager, không dùng Spring context hay DB thật. Khớp 2 test case
 * trong FND-011 §2: #1 (confirm tạo đúng transaction) và #7 (SKU bị khóa stocktake).
 */
@ExtendWith(MockitoExtension.class)
class InventoryOperationsServiceImplTest {

    private static final Long PRODUCT_ID = 42L;
    private static final Long PURCHASE_ITEM_ID = 7L;
    private static final long INITIAL_QUANTITY = 5L;
    private static final long PURCHASE_QUANTITY = 10L;

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock
    private StocktakeItemRepository stocktakeItemRepository;
    @Mock
    private InventoryAdjustmentItemRepository inventoryAdjustmentItemRepository;
    @Mock
    private EntityManager entityManager;

    @Mock
    private Product product;
    @Mock
    private PurchaseItem purchaseItem;
    @Mock
    private AppUser appUserReference;

    private Inventory inventory;
    private CurrentActor actor;
    private LocalDateTime occurredAt;

    private InventoryOperationsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryOperationsServiceImpl(
                inventoryRepository, inventoryTransactionRepository,
                stocktakeItemRepository, inventoryAdjustmentItemRepository, entityManager);

        // Entity thật có protected constructor — khởi tạo đúng cách bootstrap đã thống nhất
        // (xem CategoryService.create()), không mock Inventory để setQuantityOnHand thao tác
        // trên object thật, kiểm tra được trạng thái sau khi service chạy xong.
        inventory = BeanUtils.instantiateClass(Inventory.class);
        inventory.setProduct(product);
        inventory.setQuantityOnHand(INITIAL_QUANTITY);

        actor = new CurrentActor(99L, "huy");
        occurredAt = LocalDateTime.of(2026, 11, 23, 10, 0, 0);
    }

    @Test
    void applyPurchaseIn_createsTransactionAndIncreasesQuantity() {
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(purchaseItem.getId()).thenReturn(PURCHASE_ITEM_ID);
        when(purchaseItem.getProduct()).thenReturn(product);
        when(purchaseItem.getQuantity()).thenReturn(PURCHASE_QUANTITY);

        when(inventoryTransactionRepository.findByPurchaseItemId(PURCHASE_ITEM_ID))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
        when(stocktakeItemRepository.existsByProductIdAndCountedAtIsNullAndStocktake_Status(
                eq(PRODUCT_ID), eq(StocktakeStatus.IN_PROGRESS))).thenReturn(false);
        when(inventoryAdjustmentItemRepository.existsByProductIdAndAdjustment_Status(
                eq(PRODUCT_ID), eq(InventoryAdjustmentStatus.PENDING))).thenReturn(false);
        when(entityManager.getReference(eq(AppUser.class), anyLong())).thenReturn(appUserReference);
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryTransaction result = service.applyPurchaseIn(purchaseItem, actor, occurredAt);

        // Tồn kho phải tăng đúng theo delta, dưới cùng object đã bị khóa.
        assertEquals(INITIAL_QUANTITY + PURCHASE_QUANTITY, inventory.getQuantityOnHand());

        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryTransactionRepository).save(captor.capture());
        InventoryTransaction saved = captor.getValue();

        assertEquals(InventoryTransactionType.PURCHASE_IN, saved.getTransactionType());
        assertEquals(PURCHASE_QUANTITY, saved.getQuantityDelta());
        assertEquals(INITIAL_QUANTITY + PURCHASE_QUANTITY, saved.getQuantityAfter());
        assertEquals(occurredAt, saved.getOccurredAt());
        assertSame(product, saved.getProduct());
        assertSame(purchaseItem, saved.getPurchaseItem());
        assertSame(appUserReference, saved.getRecordedBy());
        assertSame(saved, result);
    }

    @Test
    void applyPurchaseIn_isIdempotent_returnsExistingTransactionWithoutTouchingInventory() {
        InventoryTransaction existing = BeanUtils.instantiateClass(InventoryTransaction.class);
        when(purchaseItem.getId()).thenReturn(PURCHASE_ITEM_ID);
        when(inventoryTransactionRepository.findByPurchaseItemId(PURCHASE_ITEM_ID))
                .thenReturn(Optional.of(existing));

        InventoryTransaction result = service.applyPurchaseIn(purchaseItem, actor, occurredAt);

        assertSame(existing, result);
        verify(inventoryRepository, never()).findByProductIdForUpdate(any());
        verify(inventoryTransactionRepository, never()).save(any());
    }

    @Test
    void applyPurchaseIn_throwsBusinessConflict_whenSkuLockedByStocktake() {
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(purchaseItem.getId()).thenReturn(PURCHASE_ITEM_ID);
        when(purchaseItem.getProduct()).thenReturn(product);

        when(inventoryTransactionRepository.findByPurchaseItemId(PURCHASE_ITEM_ID))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
        when(stocktakeItemRepository.existsByProductIdAndCountedAtIsNullAndStocktake_Status(
                eq(PRODUCT_ID), eq(StocktakeStatus.IN_PROGRESS))).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.applyPurchaseIn(purchaseItem, actor, occurredAt));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, exception.code());
        verify(inventoryTransactionRepository, never()).save(any());
        // Tồn kho không được đổi khi bị chặn.
        assertEquals(INITIAL_QUANTITY, inventory.getQuantityOnHand());
    }

    @Test
    void applyPurchaseIn_throwsResourceNotFound_whenInventoryRowMissing() {
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(purchaseItem.getId()).thenReturn(PURCHASE_ITEM_ID);
        when(purchaseItem.getProduct()).thenReturn(product);

        when(inventoryTransactionRepository.findByPurchaseItemId(PURCHASE_ITEM_ID))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.applyPurchaseIn(purchaseItem, actor, occurredAt));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.code());
        verify(inventoryTransactionRepository, never()).save(any());
    }
}