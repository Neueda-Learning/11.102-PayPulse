// backend/src/main/java/com/paypulse/notification/api/NotificationController.java

package com.paypulse.notification.api;

import com.paypulse.notification.domain.NotificationEvent;
import com.paypulse.notification.domain.NotificationLog;
import com.paypulse.notification.domain.NotificationStatus;
import com.paypulse.notification.dto.EmailRequest;
import com.paypulse.notification.dto.EmailResult;
import com.paypulse.notification.repository.NotificationLogRepository;
import com.paypulse.notification.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Email notification management and audit log")
public class NotificationController {

    private final EmailService emailService;
    private final NotificationLogRepository logRepository;

    public NotificationController(
            EmailService emailService,
            NotificationLogRepository logRepository
    ) {
        this.emailService  = emailService;
        this.logRepository = logRepository;
    }

    /**
     * Send a custom ad-hoc email (useful for demos and manual testing).
     *
     * POST /api/v1/notifications/send
     * {
     *   "recipientEmail": "user@example.com",
     *   "recipientName":  "John Doe",
     *   "event":          "PAYMENT_COMPLETED",
     *   "paymentId":      "uuid-here",
     *   "variables": {
     *     "amount":             "1500.00",
     *     "currency":          "INR",
     *     "referenceId":       "PAY-REF-001",
     *     "sourceAccount":     "ACC-INR-001",
     *     "destinationAccount":"EXT-9876",
     *     "completedAt":       "25 Jun 2025, 03:45 PM UTC"
     *   }
     * }
     */
    @PostMapping("/send")
    @Operation(summary = "Send an ad-hoc email notification")
    public ResponseEntity<EmailResult> sendNotification(
            @RequestBody SendNotificationRequest body
    ) {
        EmailRequest request = EmailRequest.builder()
                .to(body.recipientEmail(), body.recipientName())
                .event(NotificationEvent.valueOf(body.event()))
                .paymentId(body.paymentId() != null ? UUID.fromString(body.paymentId()) : null)
                .variables(body.variables() != null ? body.variables() : Map.of())
                .build();

        EmailResult result = emailService.send(request);
        return result.isSuccess()
                ? ResponseEntity.ok(result)
                : ResponseEntity.internalServerError().body(result);
    }

    /**
     * List notification logs with optional status filter.
     * GET /api/v1/notifications?status=FAILED&page=0&size=20
     */
    @GetMapping
    @Operation(summary = "List notification audit logs")
    public ResponseEntity<Page<NotificationLog>> listNotifications(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<NotificationLog> results = (status != null)
                ? logRepository.findByStatus(status, pageable)
                : logRepository.findAll(pageable);

        return ResponseEntity.ok(results);
    }

    /**
     * Get a single notification log by its UUID.
     * GET /api/v1/notifications/{notificationId}
     */
    @GetMapping("/{notificationId}")
    @Operation(summary = "Get notification log by ID")
    public ResponseEntity<NotificationLog> getNotification(
            @PathVariable UUID notificationId
    ) {
        return logRepository.findByNotificationId(notificationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all notifications linked to a specific payment.
     * GET /api/v1/notifications/by-payment/{paymentId}
     */
    @GetMapping("/by-payment/{paymentId}")
    @Operation(summary = "Get all notifications for a payment")
    public ResponseEntity<List<NotificationLog>> getByPayment(
            @PathVariable UUID paymentId
    ) {
        return ResponseEntity.ok(logRepository.findByPaymentId(paymentId));
    }

    // ── Request Record ────────────────────────────────────────────────────────

    record SendNotificationRequest(
            String recipientEmail,
            String recipientName,
            String event,
            String paymentId,
            Map<String, Object> variables
    ) {}
}