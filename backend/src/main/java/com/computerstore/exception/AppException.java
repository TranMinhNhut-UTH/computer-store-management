package com.computerstore.exception;

import lombok.Getter;

/** Lỗi nghiệp vụ kèm mã HTTP; GlobalExceptionHandler chuyển thành ProblemDetail. */
@Getter
public class AppException extends RuntimeException {
    private final int status;

    public AppException(int status, String message) {
        super(message);
        this.status = status;
    }
}
