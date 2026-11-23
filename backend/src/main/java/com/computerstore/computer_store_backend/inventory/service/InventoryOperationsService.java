package com.computerstore.computer_store_backend.inventory.service;

import com.computerstore.computer_store_backend.identity.authorization.CurrentActor; // sửa đúng package thật nếu khác
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransaction;
import com.computerstore.computer_store_backend.purchasing.entity.PurchaseItem;
import com.computerstore.computer_store_backend.sales.entity.SalesOrderItem;
import com.computerstore.computer_store_backend.sales.entity.SalesReturnItem;
import com.computerstore.computer_store_backend.inventory.entity.InventoryAdjustmentItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FND-008 — Inventory Operations contract. Mọi method PHẢI được gọi bên trong transaction
 * đã mở bởi Service nghiệp vụ gọi vào (Purchase/Sales/Return/Adjustment), KHÔNG REQUIRES_NEW.
 * Idempotent theo source item ở lớp bảo vệ thứ 2 (UNIQUE constraint + đọc lại bản ghi cũ);
 * lớp bảo vệ chính là status-check-under-lock ở Service gọi (xem FND-011 §1).
 */
public interface InventoryOperationsService {

    InventoryTransaction applyPurchaseIn(PurchaseItem sourceItem, CurrentActor actor, LocalDateTime occurredAt);

    /** Check-all-first: thiếu 1 SKU -> không ghi gì, ném BusinessException (BUSINESS_CONFLICT). */
    List<InventoryTransaction> applySaleOut(List<SalesOrderItem> sourceItems, CurrentActor actor, LocalDateTime occurredAt);

    /** Không gọi nếu restockQuantity == 0 (tránh insert quantity_delta = 0, vi phạm CHECK). */
    InventoryTransaction applyReturnIn(SalesReturnItem sourceItem, CurrentActor actor, LocalDateTime occurredAt);
    /** Loại suy ra từ dấu quantityDelta của sourceItem: dương -> ADJUSTMENT_IN, âm -> ADJUSTMENT_OUT. */
    InventoryTransaction applyAdjustment(InventoryAdjustmentItem sourceItem, CurrentActor actor, LocalDateTime occurredAt);

    /** Chỉ dùng khi hủy đơn CONFIRMED trước COMPLETED; gọi 1 lần / dòng SALE_OUT gốc. */
    InventoryTransaction applySaleCancel(InventoryTransaction originalSaleOut, String reversalReason, CurrentActor actor, LocalDateTime occurredAt);
}