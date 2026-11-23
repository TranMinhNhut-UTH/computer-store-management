package com.computerstore.computer_store_backend.identity.staff;

import java.time.Instant;
import java.util.List;

public record StaffResponse(String id, String username, String fullName, String email, String phone,
        boolean active, List<String> roles, Instant updatedAt) {
    public record Page(List<StaffResponse> items, int page, int size, long totalElements, int totalPages,
            List<String> allowedActions) { }
}
