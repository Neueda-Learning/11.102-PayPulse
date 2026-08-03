package com.paypulse.payment.api;

import com.paypulse.common.error.ApiError;
import com.paypulse.common.error.ErrorCode;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.PaymentStatusHistory;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.service.StatusTransitionEngine;
import com.paypulse.payment.service.states.InvalidStatusTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Shared controller file per team protocol.
 * M2 owns:
 * - GET  /payments/{id}/history
 * - POST /payments/{id}/validate
 * - POST /payments/{id}/send
 * - POST /payments/{id}/complete
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final StatusTransitionEngine statusTransitionEngine;

    @GetMapping("/{id}/history")
    public ResponseEntity<List<PaymentStatusHistory>> getHistory(@PathVariable("id") String id) {
        Payment payment = findPaymentOrThrow(id);
        List<PaymentStatusHistory> history = statusTransitionEngine.getHistory(payment.getId());
        return ResponseEntity.ok(history);
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<Payment> validatePayment(@PathVariable("id") String id) {
        Payment payment = findPaymentOrThrow(id);
        Payment updated = statusTransitionEngine.validatePayment(payment, TriggeredBy.CLIENT);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<Payment> sendPayment(@PathVariable("id") String id) {
        Payment payment = findPaymentOrThrow(id);
        Payment updated = statusTransitionEngine.sendPayment(payment, TriggeredBy.CLIENT);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Payment> completePayment(@PathVariable("id") String id) {
        Payment payment = findPaymentOrThrow(id);
        Payment updated = statusTransitionEngine.completePayment(payment, TriggeredBy.CLIENT);
        return ResponseEntity.ok(updated);
    }

    private Payment findPaymentOrThrow(String id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class PaymentNotFoundException extends RuntimeException {
        PaymentNotFoundException(String id) {
            super("No payment found with id " + id);
        }
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiError> handlePaymentNotFound(PaymentNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ErrorCode.PAYMENT_NOT_FOUND, ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidTransition(InvalidStatusTransitionException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ErrorCode.INVALID_STATUS_TRANSITION, ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, IllegalStateException.class})
    public ResponseEntity<ApiError> handleConcurrency(Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        ErrorCode.PROCESSING_ERROR,
                        "Payment was concurrently modified, please retry",
                        req.getRequestURI()
                ));
    }
}