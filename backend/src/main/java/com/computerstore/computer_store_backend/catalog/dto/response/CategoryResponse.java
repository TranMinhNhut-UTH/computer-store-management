package com.computerstore.computer_store_backend.catalog.dto.response;

import java.time.Instant;

public record CategoryResponse(String id, String code, String name, String description,
        boolean active, Instant createdAt, Instant updatedAt) { }
