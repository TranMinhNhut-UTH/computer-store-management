package com.computerstore.computer_store_backend.catalog.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ProductCreateRequest(
        @NotNull @Valid ProductWriteRequest information,
        @NotBlank @Pattern(regexp = "0|[1-9][0-9]{0,18}") String sellingPrice) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported product creation field.");
    }
}
