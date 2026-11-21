package com.computerstore.computer_store_backend.catalog.dto.response;

import java.util.List;

public record CategoryPageResponse(List<CategoryResponse> items, int page, int size,
        long totalElements, int totalPages) {
    public CategoryPageResponse { items = List.copyOf(items); }
}
