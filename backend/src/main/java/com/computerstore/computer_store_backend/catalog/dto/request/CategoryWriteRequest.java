package com.computerstore.computer_store_backend.catalog.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;

/** Full information payload for create/PUT; state, IDs and audit fields are server-owned. */
public record CategoryWriteRequest(@NotBlank String code, @NotBlank String name, String description) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported category request field.");
    }
}
