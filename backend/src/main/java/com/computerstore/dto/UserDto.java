package com.computerstore.dto;

import com.computerstore.entity.Gender;
import com.computerstore.entity.User;
import com.computerstore.entity.UserStatus;

import java.time.Instant;
import java.time.LocalDate;

public record UserDto(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        String roleCode,
        String roleName,
        Gender gender,
        LocalDate dateOfBirth,
        String avatarUrl,
        boolean verified,
        UserStatus status,
        Instant lastLoginAt,
        Instant createdAt) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(),
                u.getRole().getCode(), u.getRole().getName(), u.getGender(), u.getDateOfBirth(),
                u.getAvatarUrl(), u.isVerified(), u.getStatus(), u.getLastLoginAt(), u.getCreatedAt());
    }
}
