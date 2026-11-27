package com.computerstore.computer_store_backend.reports;

import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import java.time.Instant;
import java.util.List;

public record InventoryMovementPage(List<Movement> items, int page, int size, long totalElements, int totalPages) {
    public record Movement(String sku, String productName, InventoryTransactionType movementType,
            String quantityDelta, Instant occurredAt) { }
}
