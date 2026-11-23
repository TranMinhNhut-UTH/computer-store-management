package com.computerstore.computer_store_backend.partners.dto;

import java.util.List;

public record CustomerPageResponse(List<CustomerResponse> items, int page, int size, long totalElements,
        int totalPages, List<String> allowedActions) { }
