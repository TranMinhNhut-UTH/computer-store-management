package com.computerstore.computer_store_backend.shared.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    BUSINESS_CONFLICT(HttpStatus.CONFLICT, "The operation conflicts with the current state."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

    private final HttpStatus status;
    private final String detail;

    ErrorCode(HttpStatus status, String detail) {
        this.status = status;
        this.detail = detail;
    }

    public HttpStatus status() { return status; }
    public String detail() { return detail; }
}
