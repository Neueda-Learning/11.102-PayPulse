package com.paypulse.payment.api;

import com.paypulse.common.error.ErrorCode;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.PaymentStatusHistory;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.service.StatusTransitionEngine;
import com.paypulse.payment.service.states.InvalidStatusTransitionException;
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
    public ResponseEntity<ErrorBody> handlePaymentNotFound(PaymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody(ErrorCode.PAYMENT_NOT_FOUND.name(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorBody> handleInvalidTransition(InvalidStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody(ErrorCode.INVALID_STATUS_TRANSITION.name(), ex.getMessage()));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, IllegalStateException.class})
    public ResponseEntity<ErrorBody> handleConcurrency(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody(ErrorCode.PROCESSING_ERROR.name(), "Payment was concurrently modified, please retry"));
    }

    public record ErrorBody(String errorCode, String message) {}
}