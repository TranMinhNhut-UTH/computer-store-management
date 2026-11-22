package com.computerstore.computer_store_backend.catalog.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ProductWriteRequest(
        @NotBlank String sku, @NotBlank String name, @NotBlank String categoryId,
        String brandName, String modelCode, String description,
        @NotBlank @Pattern(regexp = "0|[1-9][0-9]{0,18}") String minStockLevel) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported product information field.");
    }
}
