package com.computerstore.computer_store_backend.partners.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;

public record SupplierWriteRequest(@NotBlank String code, @NotBlank String name, String phone,
        String email, String address, String notes) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported supplier request field.");
    }
}
