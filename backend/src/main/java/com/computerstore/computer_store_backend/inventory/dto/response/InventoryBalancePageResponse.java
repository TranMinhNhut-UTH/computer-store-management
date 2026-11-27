package com.computerstore.computer_store_backend.inventory.dto.response;

import java.util.List;

public record InventoryBalancePageResponse(
        List<InventoryBalanceResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
