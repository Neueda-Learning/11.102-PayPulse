// backend/src/main/java/com/paypulse/notification/domain/NotificationLog.java

package com.paypulse.notification.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent audit record for every email notification attempt.
 * Nothing is ever deleted - failures and retries are tracked here.
 */
@Entity
@Table(name = "notification_log")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, unique = true, updatable = false)
    private UUID notificationId = UUID.randomUUID();

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_variables", columnDefinition = "jsonb")
    private Map<String, Object> templateVariables;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private NotificationEvent eventType;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    protected NotificationLog() {}

    public static NotificationLog create(
            String recipientEmail,
            String recipientName,
            String subject,
            NotificationEvent event,
            Map<String, Object> variables,
            UUID paymentId
    ) {
        NotificationLog log = new NotificationLog();
        log.recipientEmail = recipientEmail;
        log.recipientName = recipientName;
        log.subject = subject;
        log.eventType = event;
        log.templateName = event.getTemplateName();
        log.templateVariables = variables;
        log.paymentId = paymentId;
        log.status = NotificationStatus.PENDING;
        return log;
    }

    // ── State Transitions ─────────────────────────────────────────────────────

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.lastAttemptedAt = LocalDateTime.now();
        this.attempts++;
    }

    public void markFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
        this.lastAttemptedAt = LocalDateTime.now();
        this.attempts++;
    }

    public void markSkipped(String reason) {
        this.status = NotificationStatus.SKIPPED;
        this.failureReason = reason;
    }

    public void incrementAttempt() {
        this.attempts++;
        this.lastAttemptedAt = LocalDateTime.now();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getRecipientName() { return recipientName; }
    public String getSubject() { return subject; }
    public String getTemplateName() { return templateName; }
    public Map<String, Object> getTemplateVariables() { return templateVariables; }
    public NotificationStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public UUID getPaymentId() { return paymentId; }
    public NotificationEvent getEventType() { return eventType; }
    public int getAttempts() { return attempts; }
    public LocalDateTime getLastAttemptedAt() { return lastAttemptedAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}