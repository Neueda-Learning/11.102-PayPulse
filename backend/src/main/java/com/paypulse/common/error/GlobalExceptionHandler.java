package com.paypulse.common.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Central exception → ApiError mapper.
 * NFR-14: never leaks stack traces or internal exception class names.
 * Owner: M3 — add new exception mappings here as features are built.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Bean Validation failures (field-level @Valid) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");

        return ResponseEntity.badRequest()
                .body(ApiError.of(ErrorCode.VALIDATION_FAILED, message, req.getRequestURI()));
    }

    /** Catch-all — returns 500 but NEVER exposes internal detail */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ErrorCode.PROCESSING_ERROR,
                        "An internal error occurred. Please try again.",
                        req.getRequestURI()));
    }
}

