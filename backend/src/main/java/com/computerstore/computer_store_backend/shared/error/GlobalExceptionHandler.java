package com.computerstore.computer_store_backend.shared.error;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> business(BusinessException exception, WebRequest request) {
        return response(exception.code(), exception.publicDetail(), List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
        return response(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.detail(), List.of(), request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorCode code = status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR
                : status.value() == 404 ? ErrorCode.RESOURCE_NOT_FOUND : ErrorCode.VALIDATION_ERROR;
        ProblemDetail problem = ApiProblems.create(status, code.name(),
                status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR.detail()
                        : status.value() == 400 ? ErrorCode.VALIDATION_ERROR.detail()
                        : status.value() == 404 ? ErrorCode.RESOURCE_NOT_FOUND.detail()
                        : "The request could not be processed.", instance(request));
        if (code == ErrorCode.VALIDATION_ERROR) problem.setProperty("fieldErrors", List.of());
        if (exception instanceof MethodArgumentNotValidException invalid) {
            List<FieldErrorDetail> errors = invalid.getBindingResult().getAllErrors().stream()
                    .map(error -> new FieldErrorDetail(
                            error instanceof org.springframework.validation.FieldError field
                                    ? field.getField() : "$",
                            "Invalid value."))
                    .distinct().sorted(Comparator.comparing(FieldErrorDetail::field)
                            .thenComparing(FieldErrorDetail::message)).toList();
            problem.setProperty("fieldErrors", errors);
        } else if (exception instanceof HandlerMethodValidationException invalid) {
            List<FieldErrorDetail> errors = invalid.getParameterValidationResults().stream()
                    .map(result -> new FieldErrorDetail(
                            result.getMethodParameter().getParameterName() == null ? "$"
                                    : result.getMethodParameter().getParameterName(), "Invalid value."))
                    .distinct().sorted(Comparator.comparing(FieldErrorDetail::field)).toList();
            problem.setProperty("fieldErrors", errors);
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, responseHeaders, status);
    }

    private ResponseEntity<Object> response(ErrorCode code, String detail,
            List<FieldErrorDetail> errors, WebRequest request) {
        ProblemDetail problem = ApiProblems.create(code, detail, instance(request));
        if (code == ErrorCode.VALIDATION_ERROR) problem.setProperty("fieldErrors", errors);
        return ResponseEntity.status(code.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private URI instance(WebRequest request) {
        // Exclude query parameters, which may contain secrets.
        if (request instanceof ServletWebRequest servlet) {
            try { return URI.create(servlet.getRequest().getRequestURI()); }
            catch (IllegalArgumentException ignored) { return null; }
        }
        return null;
    }
}
