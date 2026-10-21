package com.computerstore.dto;

import com.computerstore.entity.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class StaffDtos {
    private StaffDtos() {}

    public record CreateStaffRequest(
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @NotBlank @Size(max = 150) String email,
            @Size(max = 20) String phoneNumber,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank String roleCode) {}

    public record UpdateStaffRequest(
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @Size(max = 20) String phoneNumber,
            @NotBlank String roleCode) {}

    public record UpdateStaffStatusRequest(@NotNull UserStatus status) {}
}
