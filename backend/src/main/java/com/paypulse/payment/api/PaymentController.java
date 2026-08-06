package com.paypulse.payment.api;

import java.io.IOException;
import com.paypulse.common.error.ApiError;
import com.paypulse.common.error.ErrorCode;
import com.paypulse.common.export.PaymentCsvExportService;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.api.dto.PaymentHistoryResponse;
import com.paypulse.payment.api.dto.PaymentResponse;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.read.PaymentReadRepository;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.service.PaymentCreationResult;
import com.paypulse.payment.service.PaymentService;
import com.paypulse.payment.service.ReversalService;
import com.paypulse.payment.service.StatusTransitionEngine;
import com.paypulse.payment.service.states.InvalidStatusTransitionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
/**
 * Shared controller file per team protocol.
 * M3 owns: POST /payments, GET /payments/export (V2, feature #14)
 * M2 owns: GET /payments/{id}/history, POST validate/send/complete
 * M4 owns: GET /payments/{id}, GET /payments
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Create, retrieve, and track payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentReadRepository paymentReadRepository;
    private final StatusTransitionEngine statusTransitionEngine;
    private final PaymentMapper paymentMapper;
    private final ReversalService reversalService;
    private final PaymentCsvExportService csvExportService;
    // ── M3 ──────────────────────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Create a new payment")
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        PaymentCreationResult result = paymentService.createPayment(idempotencyKey, request);
        if (!result.created()) {
            return ResponseEntity.ok(result.payment());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.payment().getId())
                .toUri();
        return ResponseEntity.created(location).body(result.payment());
    }

    // ── M3 (V2, feature #14 — CSV Export) ─────────────────────────────
    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Stream the current filtered payment list as CSV (V2, feature #14)")
    public void export(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sourceAccountId,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            HttpServletResponse response) throws IOException {

        String[] s = sort.split(",");
        String rawField = s[0];
        String rawDirection = s.length > 1 ? s[1] : "desc";

        // Validate (sort allow-list + row-count cap) BEFORE writing any response
        // headers — an EXPORT_TOO_LARGE/VALIDATION_FAILED rejection must still be
        // a clean JSON ApiError, not a corrupted "text/csv" response.
        String[] resolved = csvExportService.validateExportRequest(status, search, sourceAccountId, rawField, rawDirection);

        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"payments-export-" + System.currentTimeMillis() + ".csv\""
        );

        csvExportService.streamExport(status, search, sourceAccountId, resolved[0], resolved[1], response.getWriter());
    }

    // ── M4 ──────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<PaymentResponse> getById(@PathVariable("id") String id) {
        Payment payment = findPaymentOrThrow(id);
        return ResponseEntity.ok(paymentMapper.toResponse(payment));
    }

    private static final java.util.Set<String> SORTABLE_FIELDS =
            java.util.Set.of("createdAt", "amount", "status");

    @GetMapping
    @Operation(summary = "List/search/filter payments")
    public ResponseEntity<Page<PaymentResponse>> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sourceAccountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        size = Math.min(size, 100);
        String[] s = sort.split(",");
        String field = s[0];
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new com.paypulse.payment.service.PaymentException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "Invalid sort field '" + field + "'. Allowed: createdAt, amount, status");
        }
        Sort sortObj = Sort.by(Sort.Direction.fromString(s.length > 1 ? s[1] : "desc"), field);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Page<Payment> result = paymentReadRepository.search(status, search, sourceAccountId, pageable);
        return ResponseEntity.ok(result.map(paymentMapper::toResponse));
    }

    // ── M2 ──────────────────────────────────────────────────────────
    @GetMapping("/{id}/history")
    @Operation(summary = "Get payment status-transition history")
    public ResponseEntity<List<PaymentHistoryResponse>> getHistory(@PathVariable("id") String id) {
        Payment payment = findPaymentOrThrow(id);
        List<PaymentHistoryResponse> history = statusTransitionEngine.getHistory(payment.getId()).stream()
                .map(paymentMapper::toHistoryResponse)
                .toList();
        return ResponseEntity.ok(history);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a CREATED payment")
    public ResponseEntity<PaymentResponse> cancel(@PathVariable("id") String id) {
        Payment updated = paymentService.cancelPayment(id);
        return ResponseEntity.ok(paymentMapper.toResponse(updated));
    }

    @PostMapping("/{id}/reverse")
    @Operation(summary = "Reverse a COMPLETED payment")
    public ResponseEntity<PaymentResponse> reverse(@PathVariable("id") String id) {
        Payment reversal = reversalService.reverse(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentMapper.toResponse(reversal));
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
