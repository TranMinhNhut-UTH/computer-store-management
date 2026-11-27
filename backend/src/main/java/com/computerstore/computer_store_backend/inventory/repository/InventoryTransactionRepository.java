package com.computerstore.computer_store_backend.inventory.repository;

import com.computerstore.computer_store_backend.inventory.entity.InventoryTransaction;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

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

    @Query(value = "select t from InventoryTransaction t "
            + "join fetch t.product p join fetch t.recordedBy u "
            + "where (:productId is null or t.product.id = :productId) "
            + "and (:type is null or t.transactionType = :type) "
            + "and (:from is null or t.occurredAt >= :from) "
            + "and (:to is null or t.occurredAt < :to)",
            countQuery = "select count(t) from InventoryTransaction t "
                    + "where (:productId is null or t.product.id = :productId) "
                    + "and (:type is null or t.transactionType = :type) "
                    + "and (:from is null or t.occurredAt >= :from) "
                    + "and (:to is null or t.occurredAt < :to)")
    Page<InventoryTransaction> search(
            @Param("productId") Long productId,
            @Param("type") InventoryTransactionType type,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}