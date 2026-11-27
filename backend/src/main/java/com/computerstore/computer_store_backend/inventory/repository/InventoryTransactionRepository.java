package com.computerstore.computer_store_backend.inventory.repository;

import com.computerstore.computer_store_backend.inventory.entity.InventoryTransaction;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    interface MovementRow {
        String getSku();
        String getProductName();
        InventoryTransactionType getMovementType();
        long getQuantityDelta();
        LocalDateTime getOccurredAt();
    }

    @Query("""
            select p.sku as sku, p.name as productName, t.transactionType as movementType,
                   t.quantityDelta as quantityDelta, t.occurredAt as occurredAt
            from InventoryTransaction t join t.product p
            where (:pattern is null or p.sku like upper(:pattern) escape '!' or p.name like :pattern escape '!')
              and (:type is null or t.transactionType = :type)
              and (:fromUtc is null or t.occurredAt >= :fromUtc)
              and (:untilUtc is null or t.occurredAt < :untilUtc)
            """)
    Page<MovementRow> findMovements(String pattern, InventoryTransactionType type,
            LocalDateTime fromUtc, LocalDateTime untilUtc, Pageable page);

    // Idempotency lớp 2 (bảo vệ cuối) — xem inventory-transaction-locking.md §FND-011.1
    boolean existsByPurchaseItemId(Long purchaseItemId);
    Optional<InventoryTransaction> findByPurchaseItemId(Long purchaseItemId);

    boolean existsBySalesOrderItemId(Long salesOrderItemId);
    Optional<InventoryTransaction> findBySalesOrderItemId(Long salesOrderItemId);

    boolean existsBySalesReturnItemId(Long salesReturnItemId);
    Optional<InventoryTransaction> findBySalesReturnItemId(Long salesReturnItemId);

    boolean existsByAdjustmentItemId(Long adjustmentItemId);
    Optional<InventoryTransaction> findByAdjustmentItemId(Long adjustmentItemId);

    boolean existsByReversalOfTransactionId(Long reversalOfTransactionId);
    Optional<InventoryTransaction> findByReversalOfTransactionId(Long reversalOfTransactionId);
}
