package com.computerstore.computer_store_backend.inventory.dto.response;

import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import java.time.Instant;

public record InventoryMovementResponse(
        String id,
        String productId,
        String sku,
        String productName,
        InventoryTransactionType transactionType,
        String quantityDelta,
        String quantityAfter,
        Instant occurredAt,
        String recordedBy,
        String sourceType,
        String sourceId,
        String reversalOfTransactionId,
        String reversalReason,
        String note,
        Instant createdAt) {}
