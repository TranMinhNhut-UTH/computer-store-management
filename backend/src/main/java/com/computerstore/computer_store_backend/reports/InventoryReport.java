package com.computerstore.computer_store_backend.reports;

import java.util.List;

public record InventoryReport(Summary summary, List<LowStockProduct> items,
        int page, int size, long totalElements, long totalPages) {
    public record Summary(long activeSkus, String totalQuantity, long outOfStockSkus, long lowStockSkus) { }
    public record LowStockProduct(String sku, String productName, String categoryName,
            String quantityOnHand, String minStockLevel, StockStatus stockStatus) { }
    public enum StockStatus { OUT_OF_STOCK, LOW_STOCK }
}
