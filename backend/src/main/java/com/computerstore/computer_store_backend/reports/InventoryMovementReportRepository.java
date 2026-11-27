package com.computerstore.computer_store_backend.reports;

import com.computerstore.computer_store_backend.inventory.entity.InventoryTransaction;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface InventoryMovementReportRepository extends Repository<InventoryTransaction, Long> {
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
}
