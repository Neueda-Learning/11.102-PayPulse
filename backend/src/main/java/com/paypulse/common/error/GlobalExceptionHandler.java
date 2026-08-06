package com.paypulse.common.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.paypulse.payment.service.PaymentException;

/**
 * Central exception → ApiError mapper.
 * NFR-14: never leaks stack traces or internal exception class names.
 * Owner: M3 — add new exception mappings here as features are built.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Domain/business errors from the payments flow. */
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiError> handlePayment(PaymentException ex, HttpServletRequest req) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    /**
     * Race-condition duplicate detection (Q15 follow-up): since
     * PaymentService.createPayment() is no longer wrapped in one top-level
     * transaction (needed to make cancellation reachable — see PaymentService
     * Javadoc), two concurrent requests with the same Idempotency-Key can
     * both pass the findExisting() check before either commits. The DB's
     * uq_payment_idempotency_key unique constraint is the real safety net —
     * this maps that constraint violation to the same DUPLICATE_PAYMENT
     * error code/shape the client already understands, instead of a raw 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDuplicateKey(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation at {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(ErrorCode.DUPLICATE_PAYMENT,
                        "A payment with this idempotency key is already being processed. Please retry.",
                        req.getRequestURI()));
    }

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