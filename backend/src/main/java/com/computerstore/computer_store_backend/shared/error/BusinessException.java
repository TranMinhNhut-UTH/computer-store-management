package com.computerstore.computer_store_backend.shared.error;

import java.util.Objects;

/** Only pass developer-controlled, public-safe detail; never raw exception messages or input. */
public final class BusinessException extends RuntimeException {
    private final ErrorCode code;

    public BusinessException(ErrorCode code, String publicDetail) {
        super(Objects.requireNonNull(publicDetail, "publicDetail"));
        this.code = Objects.requireNonNull(code, "code");
        if (code == ErrorCode.INTERNAL_ERROR) {
            throw new IllegalArgumentException("Internal failures are not business exceptions.");
        }
        if (publicDetail.isBlank()) {
            throw new IllegalArgumentException("Public detail must not be blank.");
        }
    }

    public ErrorCode code() { return code; }
    public String publicDetail() { return getMessage(); }
}
