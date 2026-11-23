package com.computerstore.computer_store_backend.reports;

import java.util.List;

public record ReportsOverview(StatusCounts products, StatusCounts categories, StatusCounts suppliers,
        StatusCounts customers, List<ProductCategoryCount> productsByCategory) {
    public record StatusCounts(long total, long active, long inactive) { }
    public record ProductCategoryCount(String code, String name, StatusCounts products) { }
}
