package com.computerstore.computer_store_backend.inventory.dto.response;

public record InventoryBalanceResponse(
        String productId,
        String sku,
        String productName,
        String quantityOnHand,
        String minStockLevel,
        StockStatus stockStatus) {
    public enum StockStatus { OUT_OF_STOCK, LOW_STOCK, NORMAL }
}
