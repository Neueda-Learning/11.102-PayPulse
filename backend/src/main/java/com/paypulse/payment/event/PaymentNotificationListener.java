package com.paypulse.payment.event;

import com.paypulse.account.domain.Account;
import com.paypulse.account.repository.AccountRepository;
import com.paypulse.notification.service.NotificationService;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.service.PaymentStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PaymentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationListener.class);
    private static final Pattern FAILURE_STAGE_PATTERN = Pattern.compile("(?:during|transition)\\s+([A-Z]+)");

    private final NotificationService notificationService;
    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;

    public PaymentNotificationListener(NotificationService notificationService,
                                       AccountRepository accountRepository,
                                       PaymentRepository paymentRepository) {
        this.notificationService = notificationService;
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentStatusChanged(PaymentStatusChangedEvent event) {
        PaymentStatus newStatus = event.newStatus();
        // Only notify on the initial state (CREATED) and terminal/final states
        // (COMPLETED, FAILED, CANCELLED). Intermediate lifecycle steps
        // (VALIDATED, SENT) are silent — no email noise for in-flight stages.
        if (newStatus == PaymentStatus.VALIDATED || newStatus == PaymentStatus.SENT) {
            return;
        }

        Optional<Payment> paymentOpt = paymentRepository.findById(event.paymentId());
        if (paymentOpt.isEmpty()) {
            log.warn("Notification skipped: payment {} not found", event.paymentId());
            return;
        }
        Payment payment = paymentOpt.get();

        Optional<Account> accountOpt = accountRepository.findById(payment.getSourceAccountId());
        if (accountOpt.isEmpty()) {
            log.warn("Notification skipped: source account {} not found for payment {}",
                    payment.getSourceAccountId(), payment.getId());
            return;
        }
        Account account = accountOpt.get();

        String email = account.getOwnerEmail();
        String name = account.getOwnerName();

        if (email == null || email.isBlank()) {
            log.warn("Notification skipped: no owner_email on account {} for payment {}",
                    account.getId(), payment.getId());
            return;
        }

        UUID paymentUUID;
        try {
            paymentUUID = UUID.fromString(payment.getId());
        } catch (IllegalArgumentException ex) {
            log.warn("Notification skipped: payment id {} is not a valid UUID", payment.getId());
            return;
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("paymentId", payment.getId());
        vars.put("amount", payment.getAmount());
        vars.put("currency", payment.getCurrency());
        vars.put("sourceAccount", account.getAccountNumber());
        vars.put("destinationAccount", payment.getDestinationAccount());
        vars.put("reference", payment.getReference() != null ? payment.getReference() : "");
        vars.put("referenceId", payment.getReference() != null ? payment.getReference() : "");
        vars.put("submittedAt", payment.getCreatedAt());

        if (newStatus == PaymentStatus.COMPLETED) {
            vars.put("completedAt", payment.getUpdatedAt());
        }

        if (newStatus == PaymentStatus.CANCELLED) {
            vars.put("cancelledAt", payment.getUpdatedAt());
        }

        // FAILED needs to show exactly which stage the payment failed at
        // (e.g. it failed during VALIDATE vs SEND vs COMPLETE) so devs/QA can
        // verify the state machine behaved as expected.
        if (newStatus == PaymentStatus.FAILED) {
            String failedAtStage = resolveFailedAtStage(event);
            vars.put("failedAtStage", failedAtStage);
            vars.put("failedAt", event.occurredAt());
            vars.put("failureReason", event.errorMessage() != null ? event.errorMessage() : "Unknown error");
            vars.put("errorCode", event.errorCode() != null ? event.errorCode() : "ERR_UNKNOWN");
        }

        try {
            switch (newStatus) {
                case CREATED   -> notificationService.notifyPaymentCreated(email, name, paymentUUID, vars);
                case COMPLETED -> notificationService.notifyPaymentCompleted(email, name, paymentUUID, vars);
                case FAILED    -> notificationService.notifyPaymentFailed(email, name, paymentUUID, vars);
                case CANCELLED -> notificationService.notifyPaymentCancelled(email, name, paymentUUID, vars);
                case VALIDATED, SENT -> {} // unreachable — filtered out above
            }
        } catch (Exception ex) {
            log.error("Notification send failed for payment {} status {}: {}",
                    payment.getId(), newStatus, ex.getMessage());
            // Never rethrow — notifications must never affect payment outcome
        }
    }

    private String resolveFailedAtStage(PaymentStatusChangedEvent event) {
        String message = event.errorMessage();
        if (message != null) {
            Matcher matcher = FAILURE_STAGE_PATTERN.matcher(message.toUpperCase());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        PaymentStatus previousStatus = event.previousStatus();
        if (previousStatus == null) {
            return "UNKNOWN";
        }
        return switch (previousStatus) {
            case CREATED -> "VALIDATE";
            case VALIDATED -> "SEND";
            case SENT -> "COMPLETE";
            default -> "UNKNOWN";
        };
    }
}
