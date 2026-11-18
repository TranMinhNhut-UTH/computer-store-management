package com.computerstore.computer_store_backend.identity.dto.response;

import com.computerstore.computer_store_backend.identity.security.EmployeePrincipal;

/** Auth DTO uses decimal-string IDs to avoid loss of precision in JavaScript. */
public record CurrentUserResponse(String userId, String username) {
    public static CurrentUserResponse from(EmployeePrincipal principal) {
        return new CurrentUserResponse(principal.getUserId().toString(), principal.getUsername());
    }
}
