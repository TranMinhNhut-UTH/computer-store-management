package com.computerstore.computer_store_backend.inventory.service;

import com.computerstore.computer_store_backend.identity.authorization.CurrentActor;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.inventory.entity.Inventory;
import com.computerstore.computer_store_backend.inventory.entity.InventoryAdjustmentItem;
import com.computerstore.computer_store_backend.inventory.entity.InventoryAdjustmentStatus;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransaction;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import com.computerstore.computer_store_backend.inventory.exception.InventoryErrors;
import com.computerstore.computer_store_backend.inventory.exception.ShortfallLine;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        inventory.setQuantityOnHand(newQuantity); // managed entity — Hibernate tự tăng version khi flush

        // Follow existing bootstrap construction (xem CategoryService.create()) thay vì
        // sửa protected constructor của Entity.
        InventoryTransaction txn = BeanUtils.instantiateClass(InventoryTransaction.class);
        txn.setProduct(inventory.getProduct());
        txn.setTransactionType(InventoryTransactionType.PURCHASE_IN);
        txn.setQuantityDelta(delta);
        txn.setQuantityAfter(newQuantity);
        txn.setOccurredAt(occurredAt);
        // CurrentActor chỉ mang userId/username (xem CurrentActor.java) — lấy proxy AppUser
        // qua getReferenceById (Spring Data JPA), không query thêm, không cascade. Nhất
        // quán với cách Service hiện có tiêm Repository thay vì EntityManager trực tiếp.
        txn.setRecordedBy(appUserRepository.getReferenceById(actor.userId()));
        txn.setPurchaseItem(sourceItem);

        return inventoryTransactionRepository.save(txn);
    }

    @Override
    @Transactional
    public List<InventoryTransaction> applySaleOut(List<SalesOrderItem> sourceItems, CurrentActor actor, LocalDateTime occurredAt) {
        if (sourceItems == null || sourceItems.isEmpty()) {
            throw new IllegalArgumentException("sourceItems must not be empty");
        }

        // Idempotency: giả định all-or-nothing trong 1 lần confirm atomic — nếu dòng đầu tiên
        // đã có transaction thì toàn bộ đơn đã được xử lý ở lần confirm trước (confirm lặp),
        // trả lại đúng các transaction cũ theo thứ tự sourceItems, không đụng vào inventory.
        // Trạng thái dở dang (chỉ 1 phần có transaction) không thể xảy ra vì method này chạy
        // trong 1 transaction duy nhất — lỗi giữa chừng sẽ rollback toàn bộ, không để sót.
        var firstExisting = inventoryTransactionRepository.findBySalesOrderItemId(sourceItems.get(0).getId());
        if (firstExisting.isPresent()) {
            return sourceItems.stream()
                    .map(item -> inventoryTransactionRepository.findBySalesOrderItemId(item.getId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Inconsistent partial SALE_OUT state for sales_order_item_id=" + item.getId())))
                    .toList();
        }

        // FND-009 §4 — Phương án A: 1 câu lệnh ORDER BY + FOR UPDATE, khóa theo product_id
        // tăng dần, MySQL InnoDB cấp lock đúng thứ tự quét để tránh deadlock giữa các đơn
        // tranh nhau nhiều SKU theo thứ tự khác nhau.
        List<Long> sortedProductIds = sourceItems.stream()
                .map(item -> item.getProduct().getId())
                .distinct()
                .sorted()
                .toList();
        List<Inventory> locked = inventoryRepository.findAllByProductIdInForUpdate(sortedProductIds);
        var inventoryByProductId = locked.stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), inv -> inv));

        // Check-all-first: không ghi gì cho đến khi toàn bộ dòng đều đủ điều kiện.
        List<ShortfallLine> shortfalls = new ArrayList<>();
        for (SalesOrderItem item : sourceItems) {
            Long productId = item.getProduct().getId();
            Inventory inventory = inventoryByProductId.get(productId);
            if (inventory == null) {
                throw InventoryErrors.inventoryRowNotFound(productId);
            }
            assertNotBlockedByStocktakeOrAdjustment(productId);
            long requested = item.getQuantity();
            if (inventory.getQuantityOnHand() < requested) {
                shortfalls.add(new ShortfallLine(productId, requested, inventory.getQuantityOnHand()));
            }
        }
        if (!shortfalls.isEmpty()) {
            throw InventoryErrors.insufficientStock(shortfalls);
        }

        // Chỉ sau khi toàn bộ dòng pass hết mới thực sự trừ tồn + insert, theo đúng thứ tự sourceItems.
        List<InventoryTransaction> result = new ArrayList<>(sourceItems.size());
        for (SalesOrderItem item : sourceItems) {
            Inventory inventory = inventoryByProductId.get(item.getProduct().getId());
            long delta = -item.getQuantity();
            long newQuantity = inventory.getQuantityOnHand() + delta;
            inventory.setQuantityOnHand(newQuantity);

            InventoryTransaction txn = BeanUtils.instantiateClass(InventoryTransaction.class);
            txn.setProduct(inventory.getProduct());
            txn.setTransactionType(InventoryTransactionType.SALE_OUT);
            txn.setQuantityDelta(delta);
            txn.setQuantityAfter(newQuantity);
            txn.setOccurredAt(occurredAt);
            txn.setRecordedBy(appUserRepository.getReferenceById(actor.userId()));
            txn.setSalesOrderItem(item);

            result.add(inventoryTransactionRepository.save(txn));
        }
        return result;
    }

    @Override
    @Transactional
    public InventoryTransaction applyReturnIn(SalesReturnItem sourceItem, CurrentActor actor, LocalDateTime occurredAt) {
        // restock_quantity đã là field thật trên sales_return_item (0 <= restock_quantity <= quantity,
        // xem SalesReturnItem.java) — đọc trực tiếp từ Entity, KHÔNG nhận lại qua tham số riêng,
        // để tránh lệch giữa giá trị caller truyền vào và giá trị đã lưu trong DB.
        long restockQuantity = sourceItem.getRestockQuantity();

        // Lỗi lập trình của caller, không phải lỗi nghiệp vụ: SalesReturnService không được gọi
        // vào đây khi restockQuantity == 0 (tránh insert quantity_delta = 0, vi phạm CHECK
        // ck_inventory_transaction_delta). Xem inventory-transaction-locking.md §2.
        if (restockQuantity <= 0) {
            throw new IllegalArgumentException("sourceItem.getRestockQuantity() must be > 0; caller must skip the call otherwise");
        }

        // Lớp bảo vệ thứ 2 (idempotency) — lớp chính là status-check-under-lock ở SalesReturnService.
        var existing = inventoryTransactionRepository.findBySalesReturnItemId(sourceItem.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        // SalesReturnItem không có product trực tiếp — lấy qua SalesOrderItem gốc.
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
        // Lớp bảo vệ thứ 2 (idempotency) — lớp chính là status-check-under-lock ở
        // InventoryAdjustmentService.approve() (chỉ gọi vào đây khi adjustment vừa chuyển
        // sang APPROVED, trong cùng transaction).
        var existing = inventoryTransactionRepository.findByAdjustmentItemId(sourceItem.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Long productId = sourceItem.getProduct().getId();
        long delta = sourceItem.getQuantityDelta(); // <> 0, ép bởi CHECK ck_inventory_adjustment_item_delta

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> InventoryErrors.inventoryRowNotFound(productId));

        assertNotBlockedByStocktakeOrAdjustment(productId);

        long newQuantity = inventory.getQuantityOnHand() + delta;
        if (newQuantity < 0) {
            // delta âm làm tồn âm — không cho phép, giống quy tắc check đủ tồn của SALE_OUT.
            throw InventoryErrors.insufficientStock(
                    List.of(new ShortfallLine(productId, -delta, inventory.getQuantityOnHand())));
        }
        inventory.setQuantityOnHand(newQuantity);

        // Loại suy ra từ dấu delta — 0 không hợp lệ, đã bị chặn bởi CHECK ở Entity.
        InventoryTransactionType type = delta > 0
                ? InventoryTransactionType.ADJUSTMENT_IN
                : InventoryTransactionType.ADJUSTMENT_OUT;

        InventoryTransaction txn = BeanUtils.instantiateClass(InventoryTransaction.class);
        txn.setProduct(inventory.getProduct());
        txn.setTransactionType(type);
        txn.setQuantityDelta(delta);
        txn.setQuantityAfter(newQuantity);
        txn.setOccurredAt(occurredAt);
        txn.setRecordedBy(appUserRepository.getReferenceById(actor.userId()));
        txn.setAdjustmentItem(sourceItem);

        return inventoryTransactionRepository.save(txn);
    }

    @Override
    @Transactional
    public InventoryTransaction applySaleCancel(InventoryTransaction originalSaleOut, String reversalReason, CurrentActor actor, LocalDateTime occurredAt) {
        // Lỗi lập trình của caller (SalesOrderService.cancel()), không phải lỗi nghiệp vụ:
        // chỉ được reverse đúng 1 SALE_OUT gốc, không reverse 1 reversal khác (database-design.md §5:
        // "không cho đảo movement đảo hoặc đảo lặp").
        if (originalSaleOut.getTransactionType() != InventoryTransactionType.SALE_OUT) {
            throw new IllegalArgumentException("applySaleCancel chỉ nhận transaction gốc kiểu SALE_OUT");
        }
        if (reversalReason == null || reversalReason.isBlank()) {
            throw new IllegalArgumentException("reversalReason must not be blank");
        }

        // Lớp bảo vệ thứ 2 (idempotency) — lớp chính là status-check-under-lock ở SalesOrderService.cancel().
        var existing = inventoryTransactionRepository.findByReversalOfTransactionId(originalSaleOut.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Long productId = originalSaleOut.getProduct().getId();

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> InventoryErrors.inventoryRowNotFound(productId));

        assertNotBlockedByStocktakeOrAdjustment(productId);

        long restoreQuantity = Math.abs(originalSaleOut.getQuantityDelta());
        long newQuantity = inventory.getQuantityOnHand() + restoreQuantity;
        inventory.setQuantityOnHand(newQuantity);

        InventoryTransaction txn = BeanUtils.instantiateClass(InventoryTransaction.class);
        txn.setProduct(inventory.getProduct());
        txn.setTransactionType(InventoryTransactionType.SALE_CANCEL_IN);
        txn.setQuantityDelta(restoreQuantity);
        txn.setQuantityAfter(newQuantity);
        txn.setOccurredAt(occurredAt);
        txn.setRecordedBy(appUserRepository.getReferenceById(actor.userId()));
        txn.setReversalOfTransaction(originalSaleOut); // 4 source FK khác đều null — đúng CHECK ck_inventory_transaction_source
        txn.setReversalReason(reversalReason);

        return inventoryTransactionRepository.save(txn);
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