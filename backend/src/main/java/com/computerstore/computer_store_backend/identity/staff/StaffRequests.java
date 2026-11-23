package com.computerstore.computer_store_backend.identity.staff;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;

public final class StaffRequests {
    private StaffRequests() { }

    public record Create(@NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 150) String fullName,
            @Email @Size(max = 254) String email, @Size(max = 30) String phone,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotNull @Size(min = 1, max = 72) char[] password,
            @NotNull @Size(min = 1, max = 4) List<@NotNull String> roles) {
        public Create { email = blankToNull(email); }

        @JsonAnySetter public void rejectUnknown(String field, Object value) { throw StaffService.invalid(); }

        void erasePassword() {
            if (password != null) Arrays.fill(password, (char) 0);
        }

        @Override public String toString() { return "StaffRequests.Create[redacted]"; }
    }

    public record Update(@NotBlank @Size(max = 150) String fullName,
            @Email @Size(max = 254) String email, @Size(max = 30) String phone) {
        public Update { email = blankToNull(email); }
        @JsonAnySetter public void rejectUnknown(String field, Object value) { throw StaffService.invalid(); }
    }

    public record AssignRoles(@NotNull @Size(min = 1, max = 4) List<@NotNull String> roles) {
        @JsonAnySetter public void rejectUnknown(String field, Object value) { throw StaffService.invalid(); }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
