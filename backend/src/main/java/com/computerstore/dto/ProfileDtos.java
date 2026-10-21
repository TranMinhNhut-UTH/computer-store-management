package com.computerstore.dto;

import com.computerstore.entity.Address;
import com.computerstore.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 150) String fullName,
            Gender gender,
            LocalDate dateOfBirth,
            @Size(max = 255) String avatarUrl) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {}

    public record AddressRequest(
            @NotBlank @Size(max = 150) String recipientName,
            @NotBlank @Size(max = 20) String phoneNumber,
            @NotBlank @Size(max = 100) String province,
            @NotBlank @Size(max = 100) String district,
            @NotBlank @Size(max = 100) String ward,
            @NotBlank @Size(max = 255) String street,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean defaultAddress) {}

    public record AddressDto(
            Long id,
            String recipientName,
            String phoneNumber,
            String province,
            String district,
            String ward,
            String street,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean defaultAddress) {

        public static AddressDto from(Address a) {
            return new AddressDto(a.getId(), a.getRecipientName(), a.getPhoneNumber(), a.getProvince(),
                    a.getDistrict(), a.getWard(), a.getStreet(), a.getLatitude(), a.getLongitude(),
                    a.isDefaultAddress());
        }
    }
}
