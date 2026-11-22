package com.computerstore.computer_store_backend.inventory.repository;

import com.computerstore.computer_store_backend.inventory.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

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
}