package com.computerstore.computer_store_backend.partners.dto;

import java.time.Instant;

public record SupplierResponse(String id, String code, String name, String phone, String email,
        String address, String notes, boolean active, Instant createdAt, Instant updatedAt) { }
