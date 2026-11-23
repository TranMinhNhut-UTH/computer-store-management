package com.computerstore.computer_store_backend.catalog.dto.response;

import java.time.Instant;

public record ProductResponse(
        String id, String sku, String name, String categoryId, String categoryName,
        String brandName, String modelCode, String description, String sellingPrice,
        String minStockLevel, boolean active, Instant createdAt, Instant updatedAt) { }
