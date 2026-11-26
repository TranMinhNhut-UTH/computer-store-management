package com.computerstore.computer_store_backend.partners.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public record CustomerResponse(String id, String code, String fullName, boolean active,
        Instant createdAt, Instant updatedAt, @JsonInclude(JsonInclude.Include.NON_NULL) Contact contact) {
    public record Contact(String phone, String email, String address, String notes) {
        @Override public String toString() { return "Contact[redacted]"; }
    }
}
