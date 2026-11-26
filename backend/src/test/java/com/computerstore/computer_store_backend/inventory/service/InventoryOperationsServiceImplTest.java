package com.computerstore.computer_store_backend.inventory.service;

import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.identity.authorization.CurrentActor;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.inventory.entity.Inventory;
import com.computerstore.computer_store_backend.inventory.entity.InventoryAdjustmentStatus;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransaction;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import com.computerstore.computer_store_backend.inventory.entity.StocktakeStatus;
import com.computerstore.computer_store_backend.inventory.repository.InventoryAdjustmentItemRepository;
import com.computerstore.computer_store_backend.inventory.repository.InventoryRepository;
import com.computerstore.computer_store_backend.inventory.repository.InventoryTransactionRepository;
import com.computerstore.computer_store_backend.inventory.repository.StocktakeItemRepository;
import com.computerstore.computer_store_backend.inventory.entity.InventoryAdjustmentItem;
import com.computerstore.computer_store_backend.purchasing.entity.PurchaseItem;
import com.computerstore.computer_store_backend.sales.entity.SalesOrderItem;
import com.computerstore.computer_store_backend.sales.entity.SalesReturnItem;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
    private AppUserRepository appUserRepository;

    @Mock
    private Product product;
    @Mock
    private PurchaseItem purchaseItem;
    @Mock
    private SalesReturnItem salesReturnItem;
    @Mock
    private InventoryAdjustmentItem adjustmentItem;
    @Mock
    private SalesOrderItem salesOrderItem1;
    @Mock
    private SalesOrderItem salesOrderItem2;
    @Mock
    private Product product2;
    @Mock
    private com.computerstore.computer_store_backend.sales.entity.SalesOrderItem salesOrderItem;
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
                stocktakeItemRepository, inventoryAdjustmentItemRepository, appUserRepository);

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
        when(appUserRepository.getReferenceById(anyLong())).thenReturn(appUserReference);
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

    // ----- applyReturnIn -----

    private static final Long SALES_RETURN_ITEM_ID = 15L;
    private static final long RESTOCK_QUANTITY = 3L;

    @Test
    void applyReturnIn_createsTransactionAndIncreasesQuantity() {
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(salesReturnItem.getId()).thenReturn(SALES_RETURN_ITEM_ID);
        when(salesReturnItem.getRestockQuantity()).thenReturn(RESTOCK_QUANTITY);
        when(salesReturnItem.getSalesOrderItem()).thenReturn(salesOrderItem);
        when(salesOrderItem.getProduct()).thenReturn(product);

        when(inventoryTransactionRepository.findBySalesReturnItemId(SALES_RETURN_ITEM_ID))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
        when(stocktakeItemRepository.existsByProductIdAndCountedAtIsNullAndStocktake_Status(
                eq(PRODUCT_ID), eq(StocktakeStatus.IN_PROGRESS))).thenReturn(false);
        when(inventoryAdjustmentItemRepository.existsByProductIdAndAdjustment_Status(
                eq(PRODUCT_ID), eq(InventoryAdjustmentStatus.PENDING))).thenReturn(false);
        when(appUserRepository.getReferenceById(anyLong())).thenReturn(appUserReference);
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryTransaction result = service.applyReturnIn(salesReturnItem, actor, occurredAt);

        assertEquals(INITIAL_QUANTITY + RESTOCK_QUANTITY, inventory.getQuantityOnHand());

        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryTransactionRepository).save(captor.capture());
        InventoryTransaction saved = captor.getValue();

        assertEquals(InventoryTransactionType.RETURN_IN, saved.getTransactionType());
        assertEquals(RESTOCK_QUANTITY, saved.getQuantityDelta());
        assertEquals(INITIAL_QUANTITY + RESTOCK_QUANTITY, saved.getQuantityAfter());
        assertSame(salesReturnItem, saved.getSalesReturnItem());
        assertSame(saved, result);
    }

    @Test
    void applyReturnIn_rejectsZeroOrNegativeRestockQuantity() {
        when(salesReturnItem.getRestockQuantity()).thenReturn(0L);

        assertThrows(IllegalArgumentException.class,
                () -> service.applyReturnIn(salesReturnItem, actor, occurredAt));
        verify(inventoryTransactionRepository, never()).findBySalesReturnItemId(any());
    }

    @Test
    void applyReturnIn_isIdempotent_returnsExistingTransaction() {
        InventoryTransaction existing = BeanUtils.instantiateClass(InventoryTransaction.class);
        when(salesReturnItem.getRestockQuantity()).thenReturn(RESTOCK_QUANTITY);
        when(salesReturnItem.getId()).thenReturn(SALES_RETURN_ITEM_ID);
        when(inventoryTransactionRepository.findBySalesReturnItemId(SALES_RETURN_ITEM_ID))
                .thenReturn(Optional.of(existing));

        InventoryTransaction result = service.applyReturnIn(salesReturnItem, actor, occurredAt);

        assertSame(existing, result);
        verify(inventoryRepository, never()).findByProductIdForUpdate(any());
    }

    // ----- applyAdjustment -----

    private static final Long ADJUSTMENT_ITEM_ID = 21L;

    @Test
    void applyAdjustment_positiveDelta_createsAdjustmentInAndIncreasesQuantity() {
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(adjustmentItem.getId()).thenReturn(ADJUSTMENT_ITEM_ID);
        when(adjustmentItem.getProduct()).thenReturn(product);
        when(adjustmentItem.getQuantityDelta()).thenReturn(4L);

        when(inventoryTransactionRepository.findByAdjustmentItemId(ADJUSTMENT_ITEM_ID))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
        when(stocktakeItemRepository.existsByProductIdAndCountedAtIsNullAndStocktake_Status(
                eq(PRODUCT_ID), eq(StocktakeStatus.IN_PROGRESS))).thenReturn(false);
        when(inventoryAdjustmentItemRepository.existsByProductIdAndAdjustment_Status(
                eq(PRODUCT_ID), eq(InventoryAdjustmentStatus.PENDING))).thenReturn(false);
        when(appUserRepository.getReferenceById(anyLong())).thenReturn(appUserReference);
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryTransaction result = service.applyAdjustment(adjustmentItem, actor, occurredAt);

        assertEquals(INITIAL_QUANTITY + 4L, inventory.getQuantityOnHand());
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryTransactionRepository).save(captor.capture());
        assertEquals(InventoryTransactionType.ADJUSTMENT_IN, captor.getValue().getTransactionType());
        assertEquals(4L, captor.getValue().getQuantityDelta());
        assertSame(result, captor.getValue());
    }

    @Test
    void applyAdjustment_negativeDelta_createsAdjustmentOutWhenStillNonNegative() {
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(adjustmentItem.getId()).thenReturn(ADJUSTMENT_ITEM_ID);
        when(adjustmentItem.getProduct()).thenReturn(product);
        when(adjustmentItem.getQuantityDelta()).thenReturn(-2L); // INITIAL_QUANTITY(5) - 2 = 3 >= 0

        when(inventoryTransactionRepository.findByAdjustmentItemId(ADJUSTMENT_ITEM_ID))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
        when(stocktakeItemRepository.existsByProductIdAndCountedAtIsNullAndStocktake_Status(
                eq(PRODUCT_ID), eq(StocktakeStatus.IN_PROGRESS))).thenReturn(false);
        when(inventoryAdjustmentItemRepository.existsByProductIdAndAdjustment_Status(
                eq(PRODUCT_ID), eq(InventoryAdjustmentStatus.PENDING))).thenReturn(false);
        when(appUserRepository.getReferenceById(anyLong())).thenReturn(appUserReference);
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.applyAdjustment(adjustmentItem, actor, occurredAt);

        assertEquals(INITIAL_QUANTITY - 2L, inventory.getQuantityOnHand());
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryTransactionRepository).save(captor.capture());
        assertEquals(InventoryTransactionType.ADJUSTMENT_OUT, captor.getValue().getTransactionType());
        assertEquals(-2L, captor.getValue().getQuantityDelta());
    }

    @Test
    void applyAdjustment_negativeDelta_rejectsWhenWouldGoNegative() {
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(adjustmentItem.getId()).thenReturn(ADJUSTMENT_ITEM_ID);
        when(adjustmentItem.getProduct()).thenReturn(product);
        when(adjustmentItem.getQuantityDelta()).thenReturn(-999L); // INITIAL_QUANTITY(5) - 999 < 0

        when(inventoryTransactionRepository.findByAdjustmentItemId(ADJUSTMENT_ITEM_ID))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
        when(stocktakeItemRepository.existsByProductIdAndCountedAtIsNullAndStocktake_Status(
                eq(PRODUCT_ID), eq(StocktakeStatus.IN_PROGRESS))).thenReturn(false);
        when(inventoryAdjustmentItemRepository.existsByProductIdAndAdjustment_Status(
                eq(PRODUCT_ID), eq(InventoryAdjustmentStatus.PENDING))).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.applyAdjustment(adjustmentItem, actor, occurredAt));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, exception.code());
        verify(inventoryTransactionRepository, never()).save(any());
        assertEquals(INITIAL_QUANTITY, inventory.getQuantityOnHand()); // không đổi khi bị chặn
    }

    @Test
    void applyAdjustment_isIdempotent_returnsExistingTransaction() {
        InventoryTransaction existing = BeanUtils.instantiateClass(InventoryTransaction.class);
        when(adjustmentItem.getId()).thenReturn(ADJUSTMENT_ITEM_ID);
        when(inventoryTransactionRepository.findByAdjustmentItemId(ADJUSTMENT_ITEM_ID))
                .thenReturn(Optional.of(existing));

        InventoryTransaction result = service.applyAdjustment(adjustmentItem, actor, occurredAt);

        assertSame(existing, result);
        verify(inventoryRepository, never()).findByProductIdForUpdate(any());
    }

    // ----- applySaleCancel -----

    private static final Long ORIGINAL_SALE_OUT_ID = 31L;

    @Mock
    private InventoryTransaction originalSaleOut;

    @Test
    void applySaleCancel_restoresQuantityAndCreatesReversal() {
        when(originalSaleOut.getTransactionType()).thenReturn(InventoryTransactionType.SALE_OUT);
        when(originalSaleOut.getId()).thenReturn(ORIGINAL_SALE_OUT_ID);
        when(originalSaleOut.getProduct()).thenReturn(product);
        when(originalSaleOut.getQuantityDelta()).thenReturn(-6L);
        when(product.getId()).thenReturn(PRODUCT_ID);

        when(inventoryTransactionRepository.findByReversalOfTransactionId(ORIGINAL_SALE_OUT_ID))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
        when(stocktakeItemRepository.existsByProductIdAndCountedAtIsNullAndStocktake_Status(
                eq(PRODUCT_ID), eq(StocktakeStatus.IN_PROGRESS))).thenReturn(false);
        when(inventoryAdjustmentItemRepository.existsByProductIdAndAdjustment_Status(
                eq(PRODUCT_ID), eq(InventoryAdjustmentStatus.PENDING))).thenReturn(false);
        when(appUserRepository.getReferenceById(anyLong())).thenReturn(appUserReference);
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryTransaction result = service.applySaleCancel(originalSaleOut, "customer changed mind", actor, occurredAt);

        assertEquals(INITIAL_QUANTITY + 6L, inventory.getQuantityOnHand());
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryTransactionRepository).save(captor.capture());
        assertEquals(InventoryTransactionType.SALE_CANCEL_IN, captor.getValue().getTransactionType());
        assertEquals(6L, captor.getValue().getQuantityDelta());
        assertSame(originalSaleOut, captor.getValue().getReversalOfTransaction());
        assertEquals("customer changed mind", captor.getValue().getReversalReason());
        assertSame(result, captor.getValue());
    }

    @Test
    void applySaleCancel_rejectsNonSaleOutSource() {
        when(originalSaleOut.getTransactionType()).thenReturn(InventoryTransactionType.PURCHASE_IN);

        assertThrows(IllegalArgumentException.class,
                () -> service.applySaleCancel(originalSaleOut, "reason", actor, occurredAt));
        verify(inventoryTransactionRepository, never()).save(any());
    }

    @Test
    void applySaleCancel_rejectsBlankReason() {
        when(originalSaleOut.getTransactionType()).thenReturn(InventoryTransactionType.SALE_OUT);

        assertThrows(IllegalArgumentException.class,
                () -> service.applySaleCancel(originalSaleOut, "  ", actor, occurredAt));
        verify(inventoryTransactionRepository, never()).save(any());
    }

    @Test
    void applySaleCancel_isIdempotent_returnsExistingTransaction() {
        InventoryTransaction existing = BeanUtils.instantiateClass(InventoryTransaction.class);
        when(originalSaleOut.getTransactionType()).thenReturn(InventoryTransactionType.SALE_OUT);
        when(originalSaleOut.getId()).thenReturn(ORIGINAL_SALE_OUT_ID);
        when(inventoryTransactionRepository.findByReversalOfTransactionId(ORIGINAL_SALE_OUT_ID))
                .thenReturn(Optional.of(existing));

        InventoryTransaction result = service.applySaleCancel(originalSaleOut, "reason", actor, occurredAt);

        assertSame(existing, result);
        verify(inventoryRepository, never()).findByProductIdForUpdate(any());
    }

    // ----- applySaleOut -----

    @Test
    void applySaleOut_checkAllFirst_rollsBackEntirelyWhenOneLineIsShort() {
        Inventory inventory2 = BeanUtils.instantiateClass(Inventory.class);
        inventory2.setProduct(product2);
        inventory2.setQuantityOnHand(2L); // không đủ cho dòng 2 cần 5

        when(salesOrderItem1.getId()).thenReturn(101L);
        when(salesOrderItem1.getProduct()).thenReturn(product);
        when(salesOrderItem1.getQuantity()).thenReturn(1L);
        // Lưu ý: salesOrderItem2.getId() KHÔNG được stub ở đây — applySaleOut chỉ gọi
        // getId() trên sourceItems.get(0) (dòng đầu) để check idempotency, không gọi cho
        // từng dòng trong vòng lặp chính (vòng lặp chỉ dùng getProduct()/getQuantity()).
        // Stub thừa cho item2.getId() sẽ bị Mockito strict-stubbing báo "unnecessary".
        when(salesOrderItem2.getProduct()).thenReturn(product2);
        when(salesOrderItem2.getQuantity()).thenReturn(5L);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(product2.getId()).thenReturn(43L);

        when(inventoryTransactionRepository.findBySalesOrderItemId(101L)).thenReturn(Optional.empty());
        when(inventoryRepository.findAllByProductIdInForUpdate(List.of(PRODUCT_ID, 43L)))
                .thenReturn(List.of(inventory, inventory2));
        when(stocktakeItemRepository.existsByProductIdAndCountedAtIsNullAndStocktake_Status(any(), any()))
                .thenReturn(false);
        when(inventoryAdjustmentItemRepository.existsByProductIdAndAdjustment_Status(any(), any()))
                .thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.applySaleOut(List.of(salesOrderItem1, salesOrderItem2), actor, occurredAt));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, exception.code());
        verify(inventoryTransactionRepository, never()).save(any());
        // Không dòng nào bị trừ tồn, kể cả dòng 1 vốn đủ hàng.
        assertEquals(INITIAL_QUANTITY, inventory.getQuantityOnHand());
        assertEquals(2L, inventory2.getQuantityOnHand());
    }

    @Test
    void applySaleOut_createsOneTransactionPerLine_whenAllSufficient() {
        when(salesOrderItem1.getId()).thenReturn(101L);
        when(salesOrderItem1.getProduct()).thenReturn(product);
        when(salesOrderItem1.getQuantity()).thenReturn(2L);
        when(product.getId()).thenReturn(PRODUCT_ID);

        when(inventoryTransactionRepository.findBySalesOrderItemId(101L)).thenReturn(Optional.empty());
        when(inventoryRepository.findAllByProductIdInForUpdate(List.of(PRODUCT_ID)))
                .thenReturn(List.of(inventory));
        when(stocktakeItemRepository.existsByProductIdAndCountedAtIsNullAndStocktake_Status(any(), any()))
                .thenReturn(false);
        when(inventoryAdjustmentItemRepository.existsByProductIdAndAdjustment_Status(any(), any()))
                .thenReturn(false);
        when(appUserRepository.getReferenceById(anyLong())).thenReturn(appUserReference);
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<InventoryTransaction> result = service.applySaleOut(List.of(salesOrderItem1), actor, occurredAt);

        assertEquals(1, result.size());
        assertEquals(InventoryTransactionType.SALE_OUT, result.get(0).getTransactionType());
        assertEquals(-2L, result.get(0).getQuantityDelta());
        assertEquals(INITIAL_QUANTITY - 2L, inventory.getQuantityOnHand());
    }

    @Test
    void applySaleOut_isIdempotent_whenFirstLineAlreadyHasTransaction() {
        InventoryTransaction existing1 = BeanUtils.instantiateClass(InventoryTransaction.class);
        InventoryTransaction existing2 = BeanUtils.instantiateClass(InventoryTransaction.class);
        when(salesOrderItem1.getId()).thenReturn(101L);
        when(salesOrderItem2.getId()).thenReturn(102L);
        when(inventoryTransactionRepository.findBySalesOrderItemId(101L)).thenReturn(Optional.of(existing1));
        when(inventoryTransactionRepository.findBySalesOrderItemId(102L)).thenReturn(Optional.of(existing2));

        List<InventoryTransaction> result = service.applySaleOut(List.of(salesOrderItem1, salesOrderItem2), actor, occurredAt);

        assertEquals(List.of(existing1, existing2), result);
        verify(inventoryRepository, never()).findAllByProductIdInForUpdate(any());
    }
}