package com.computerstore.computer_store_backend.inventory.service;

import com.computerstore.computer_store_backend.identity.authorization.CurrentActor;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.inventory.entity.Inventory;
import com.computerstore.computer_store_backend.inventory.entity.InventoryAdjustmentItem;
import com.computerstore.computer_store_backend.inventory.entity.InventoryAdjustmentStatus;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransaction;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import com.computerstore.computer_store_backend.inventory.exception.InventoryErrors;
import com.computerstore.computer_store_backend.inventory.repository.InventoryAdjustmentItemRepository;
import com.computerstore.computer_store_backend.inventory.repository.InventoryRepository;
import com.computerstore.computer_store_backend.inventory.repository.InventoryTransactionRepository;
import com.computerstore.computer_store_backend.inventory.repository.StocktakeItemRepository;
import com.computerstore.computer_store_backend.inventory.entity.StocktakeStatus;
import com.computerstore.computer_store_backend.purchasing.entity.PurchaseItem;
import com.computerstore.computer_store_backend.sales.entity.SalesOrderItem;
import com.computerstore.computer_store_backend.sales.entity.SalesReturnItem;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FND-008/009/010/011 — implementation thật của InventoryOperationsService.
 *
 * Chỉ applyPurchaseIn() đã implement đầy đủ ở lần này. 4 method còn lại ném
 * UnsupportedOperationException có chủ đích, để class compile/merge được theo từng
 * phần thay vì chờ viết xong cả 5 operation cùng lúc:
 *   - applySaleOut: chờ nhóm chốt phương án A/B (FND-009 §4) trước khi dùng
 *     findAllByProductIdInForUpdate.
 *   - applyReturnIn, applyAdjustment, applySaleCancel: làm ở các bước kế tiếp.
 *
 * Propagation.REQUIRED mặc định — luôn tham gia transaction của Service gọi
 * (Purchase/Sales/Return/Adjustment), không bao giờ REQUIRES_NEW.
 */
@Service
public class InventoryOperationsServiceImpl implements InventoryOperationsService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final StocktakeItemRepository stocktakeItemRepository;
    private final InventoryAdjustmentItemRepository inventoryAdjustmentItemRepository;
    private final AppUserRepository appUserRepository;

    public InventoryOperationsServiceImpl(InventoryRepository inventoryRepository,
                                          InventoryTransactionRepository inventoryTransactionRepository,
                                          StocktakeItemRepository stocktakeItemRepository,
                                          InventoryAdjustmentItemRepository inventoryAdjustmentItemRepository,
                                          AppUserRepository appUserRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.stocktakeItemRepository = stocktakeItemRepository;
        this.inventoryAdjustmentItemRepository = inventoryAdjustmentItemRepository;
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional
    public InventoryTransaction applyPurchaseIn(PurchaseItem sourceItem, CurrentActor actor, LocalDateTime occurredAt) {
        // Lớp bảo vệ thứ 2 (idempotency) — lớp chính là status-check-under-lock ở PurchaseService.
        var existing = inventoryTransactionRepository.findByPurchaseItemId(sourceItem.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Long productId = sourceItem.getProduct().getId();

        // FND-009 §2-3: khóa header đã do PurchaseService thực hiện trước khi gọi vào đây.
        // Ở đây chỉ khóa bước cuối: inventory của đúng 1 product.
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> InventoryErrors.inventoryRowNotFound(productId));

        assertNotBlockedByStocktakeOrAdjustment(productId);

        long delta = sourceItem.getQuantity();
        long newQuantity = inventory.getQuantityOnHand() + delta;
        inventory.setQuantityOnHand(newQuantity);
        InventoryTransaction txn = BeanUtils.instantiateClass(InventoryTransaction.class);
        txn.setProduct(inventory.getProduct());
        txn.setTransactionType(InventoryTransactionType.PURCHASE_IN);
        txn.setQuantityDelta(delta);
        txn.setQuantityAfter(newQuantity);
        txn.setOccurredAt(occurredAt);
        txn.setRecordedBy(appUserRepository.getReferenceById(actor.userId()));
        txn.setPurchaseItem(sourceItem);
        return inventoryTransactionRepository.save(txn);
    }

    @Override
    @Transactional
    public List<InventoryTransaction> applySaleOut(List<SalesOrderItem> sourceItems, CurrentActor actor, LocalDateTime occurredAt) {
        throw new UnsupportedOperationException(
                "applySaleOut: chờ nhóm chốt phương án khóa nhiều dòng (FND-009 §4, phương án A/B)");
    }

    @Override
    @Transactional
    public InventoryTransaction applyReturnIn(SalesReturnItem sourceItem, CurrentActor actor, LocalDateTime occurredAt) {
        // restock_quantity đã là field thật trên sales_return_item (0 <= restock_quantity <= quantity,
        // xem SalesReturnItem.java) — đọc trực tiếp từ Entity, KHÔNG nhận lại qua tham số riêng,
        // để tránh lệch giữa giá trị caller truyền vào và giá trị đã lưu trong DB.
        long restockQuantity = sourceItem.getRestockQuantity();
        if (restockQuantity <= 0) {
            throw new IllegalArgumentException("sourceItem.getRestockQuantity() must be > 0; caller must skip the call otherwise");
        }
        var existing = inventoryTransactionRepository.findBySalesReturnItemId(sourceItem.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Long productId = sourceItem.getSalesOrderItem().getProduct().getId();
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> InventoryErrors.inventoryRowNotFound(productId));

        assertNotBlockedByStocktakeOrAdjustment(productId);

        long newQuantity = inventory.getQuantityOnHand() + restockQuantity;
        inventory.setQuantityOnHand(newQuantity);
        InventoryTransaction txn = BeanUtils.instantiateClass(InventoryTransaction.class);
        txn.setProduct(inventory.getProduct());
        txn.setTransactionType(InventoryTransactionType.RETURN_IN);
        txn.setQuantityDelta(restockQuantity);
        txn.setQuantityAfter(newQuantity);
        txn.setOccurredAt(occurredAt);
        txn.setRecordedBy(appUserRepository.getReferenceById(actor.userId()));
        txn.setSalesReturnItem(sourceItem);
        return inventoryTransactionRepository.save(txn);
    }

    @Override
    @Transactional
    public InventoryTransaction applyAdjustment(InventoryAdjustmentItem sourceItem, CurrentActor actor, LocalDateTime occurredAt) {
        throw new UnsupportedOperationException("applyAdjustment: chưa triển khai");
    }

    @Override
    @Transactional
    public InventoryTransaction applySaleCancel(InventoryTransaction originalSaleOut, String reversalReason, CurrentActor actor, LocalDateTime occurredAt) {
        throw new UnsupportedOperationException("applySaleCancel: chưa triển khai");
    }

    /** database-design.md §4.5 — chặn movement khi SKU đang nằm trong 1 stocktake/adjustment mở. */
    private void assertNotBlockedByStocktakeOrAdjustment(Long productId) {
        boolean stocktakeOpen = stocktakeItemRepository
                .existsByProductIdAndCountedAtIsNullAndStocktake_Status(productId, StocktakeStatus.IN_PROGRESS);
        boolean adjustmentOpen = inventoryAdjustmentItemRepository
                .existsByProductIdAndAdjustment_Status(productId, InventoryAdjustmentStatus.PENDING);
        if (stocktakeOpen || adjustmentOpen) {
            throw InventoryErrors.skuLockedByStocktake(productId);
        }
    }
}