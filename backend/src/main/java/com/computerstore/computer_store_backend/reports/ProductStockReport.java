package com.computerstore.computer_store_backend.reports;

public record ProductStockReport(String productId, String sku, String productName,
        String quantityOnHand, String minStockLevel, InventoryReport.StockStatus stockStatus) { }
