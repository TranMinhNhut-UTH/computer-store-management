package com.computerstore.computer_store_backend.catalog.dto.response;

import java.util.List;

public record ProductPageResponse(
        List<ProductResponse> items, int page, int size, long totalElements, int totalPages,
        List<String> allowedActions) { }
