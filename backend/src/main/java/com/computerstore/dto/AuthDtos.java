package com.computerstore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @Size(max = 150) String email,
            @Size(max = 20) String phoneNumber,
            @NotBlank @Size(min = 8, max = 100) String password) {}

    public record VerifyOtpRequest(
            @NotBlank String identifier,
            @NotBlank @Size(min = 6, max = 6) String code) {}

    public record IdentifierRequest(@NotBlank String identifier) {}

    public record LoginRequest(
            @NotBlank String identifier,
            @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record ResetPasswordRequest(
            @NotBlank String identifier,
            @NotBlank @Size(min = 6, max = 6) String code,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            java.time.Instant accessTokenExpiresAt,
            UserDto user) {}
}
