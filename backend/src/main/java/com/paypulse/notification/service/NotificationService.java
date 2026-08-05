// backend/src/main/java/com/paypulse/notification/service/NotificationService.java

package com.paypulse.notification.service;

import com.paypulse.notification.domain.NotificationEvent;
import com.paypulse.notification.dto.EmailRequest;
import com.paypulse.notification.dto.EmailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * High-level notification facade.
 *
 * Other services (PaymentService, AccountService, etc.) should call THIS class,
 * not EmailService directly. This keeps business logic out of the email plumbing.
 *
 * Every method accepts a variable-argument map so the caller decides exactly
 * what data to embed in the email body - fully flexible, no hard-coded fields.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final EmailService emailService;

    public NotificationService(EmailService emailService) {
        this.emailService = emailService;
    }

    // ── Payment Lifecycle Notifications ───────────────────────────────────────

    /**
     * Notify the user that their payment was received and is being processed.
     *
     * Recommended variables:
     *   paymentId, referenceId, amount, currency,
     *   sourceAccount, destinationAccount, submittedAt
     */
    public CompletableFuture<EmailResult> notifyPaymentCreated(
            String recipientEmail,
            String recipientName,
            UUID paymentId,
            Map<String, Object> variables
    ) {
        return sendAsync(recipientEmail, recipientName,
                NotificationEvent.PAYMENT_CREATED, paymentId, variables);
    }

    /**
     * Notify the user that their payment completed successfully.
     *
     * Recommended variables:
     *   paymentId, referenceId, amount, currency,
     *   sourceAccount, destinationAccount, completedAt
     */
    public CompletableFuture<EmailResult> notifyPaymentCompleted(
            String recipientEmail,
            String recipientName,
            UUID paymentId,
            Map<String, Object> variables
    ) {
        return sendAsync(recipientEmail, recipientName,
                NotificationEvent.PAYMENT_COMPLETED, paymentId, variables);
    }

    /**
     * Notify the user that their payment failed.
     *
     * Recommended variables:
     *   paymentId, referenceId, amount, currency,
     *   failureReason, errorCode, failedAt
     */
    public CompletableFuture<EmailResult> notifyPaymentFailed(
            String recipientEmail,
            String recipientName,
            UUID paymentId,
            Map<String, Object> variables
    ) {
        return sendAsync(recipientEmail, recipientName,
                NotificationEvent.PAYMENT_FAILED, paymentId, variables);
    }

    /**
     * Send a welcome email (non-payment event, no paymentId).
     *
     * Recommended variables:
     *   recipientName, accountNumber, currency
     */
    public CompletableFuture<EmailResult> sendWelcomeEmail(
            String recipientEmail,
            String recipientName,
            Map<String, Object> variables
    ) {
        return sendAsync(recipientEmail, recipientName,
                NotificationEvent.WELCOME, null, variables);
    }

    /**
     * Generic send method for any event.
     * Use this when no typed convenience method exists for your event.
     *
     * @param recipientEmail target inbox
     * @param recipientName  display name shown in greeting
     * @param event          which template/subject to use
     * @param paymentId      nullable; links this notification to a payment
     * @param variables      arbitrary key-value data embedded in the template
     */
    public CompletableFuture<EmailResult> send(
            String recipientEmail,
            String recipientName,
            NotificationEvent event,
            UUID paymentId,
            Map<String, Object> variables
    ) {
        return sendAsync(recipientEmail, recipientName, event, paymentId, variables);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private CompletableFuture<EmailResult> sendAsync(
            String recipientEmail,
            String recipientName,
            NotificationEvent event,
            UUID paymentId,
            Map<String, Object> variables
    ) {
        log.info("Dispatching notification: event={}, recipient={}, paymentId={}",
                event, recipientEmail, paymentId);

        EmailRequest request = EmailRequest.builder()
                .to(recipientEmail, recipientName)
                .event(event)
                .paymentId(paymentId)
                .variables(variables != null ? variables : Map.of())
                .build();

        return emailService.sendAsync(request);
    }
}