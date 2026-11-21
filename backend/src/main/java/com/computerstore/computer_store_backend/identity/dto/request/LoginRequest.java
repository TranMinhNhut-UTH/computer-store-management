package com.computerstore.computer_store_backend.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    @Override public String toString() { return "LoginRequest[credentials redacted]"; }
}
