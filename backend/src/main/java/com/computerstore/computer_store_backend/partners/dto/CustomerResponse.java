package com.computerstore.computer_store_backend.partners.dto;

import java.time.Instant;

public record CustomerResponse(String id, String code, String fullName, boolean active,
        Instant createdAt, Instant updatedAt, Contact contact) {
    public record Contact(String phone, String email, String address, String notes) { }
}
