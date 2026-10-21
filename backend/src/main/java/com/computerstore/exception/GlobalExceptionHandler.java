package com.computerstore.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TITLE = "Yêu cầu không thành công";

    private static ProblemDetail problem(int status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        pd.setTitle(TITLE);
        return pd;
    }

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleApp(AppException ex) {
        return problem(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(403, "Bạn không có quyền thực hiện thao tác này.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleConflict(DataIntegrityViolationException ex) {
        log.warn("Vi phạm ràng buộc dữ liệu: {}", ex.getMostSpecificCause().getMessage());
        return problem(409, "Dữ liệu bị trùng hoặc không hợp lệ (ví dụ email/số điện thoại đã tồn tại).");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleOther(Exception ex) {
        log.error("Lỗi không xử lý được", ex);
        return problem(500, "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map((FieldError f) -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(problem(400, detail.isBlank() ? "Dữ liệu không hợp lệ." : detail));
    }
}
