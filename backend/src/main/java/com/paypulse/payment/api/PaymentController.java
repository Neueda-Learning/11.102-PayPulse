package com.paypulse.payment.api;

import com.paypulse.common.error.ApiError;
import com.paypulse.common.error.ErrorCode;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.api.dto.PaymentHistoryResponse;
import com.paypulse.payment.api.dto.PaymentResponse;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.PaymentStatusHistory;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.service.PaymentCreationResult;
import com.paypulse.payment.service.PaymentService;
import com.paypulse.payment.service.StatusTransitionEngine;
import com.paypulse.payment.service.states.InvalidStatusTransitionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Shared controller file per team protocol.
 * M3 owns:
 * - POST /payments
 * M2 owns:
 * - GET  /payments/{id}/history
 * - POST /payments/{id}/validate
 * - POST /payments/{id}/send
 * - POST /payments/{id}/complete
 * M4 will own:
 * - GET /payments/{id}
 * - GET /payments
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Create, retrieve, and track payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final StatusTransitionEngine statusTransitionEngine;
    private final PaymentMapper paymentMapper;

    @GetMapping("/{id}/history")
    @Operation(summary = "Get payment status-transition history")
    public ResponseEntity<List<PaymentHistoryResponse>> getHistory(@PathVariable("id") String id) {
        Payment payment = findPaymentOrThrow(id);
        List<PaymentHistoryResponse> history = statusTransitionEngine.getHistory(payment.getId()).stream()
                .map(paymentMapper::toHistoryResponse)
                .toList();
        return ResponseEntity.ok(history);
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Trigger CREATED -> VALIDATED transition")
    public ResponseEntity<PaymentResponse> validatePayment(@PathVariable("id") String id) {
        Payment payment = findPaymentOrThrow(id);
        Payment updated = statusTransitionEngine.validatePayment(payment, TriggeredBy.CLIENT);
        return ResponseEntity.ok(paymentMapper.toResponse(updated));
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Trigger VALIDATED -> SENT transition")
    public ResponseEntity<PaymentResponse> sendPayment(@PathVariable("id") String id) {
        Payment payment = findPaymentOrThrow(id);
        Payment updated = statusTransitionEngine.sendPayment(payment, TriggeredBy.CLIENT);
        return ResponseEntity.ok(paymentMapper.toResponse(updated));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Trigger SENT -> COMPLETED transition")
    public ResponseEntity<PaymentResponse> completePayment(@PathVariable("id") String id) {
        Payment payment = findPaymentOrThrow(id);
        Payment updated = statusTransitionEngine.completePayment(payment, TriggeredBy.CLIENT);
        return ResponseEntity.ok(paymentMapper.toResponse(updated));
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