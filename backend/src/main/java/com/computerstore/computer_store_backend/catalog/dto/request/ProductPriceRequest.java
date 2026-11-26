package com.computerstore.computer_store_backend.catalog.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ProductPriceRequest(
        @JsonDeserialize(using = ProductPriceStringDeserializer.class)
        @NotBlank @Pattern(regexp = "0|[1-9][0-9]{0,18}") String sellingPrice) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported product price field.");
    }
}
