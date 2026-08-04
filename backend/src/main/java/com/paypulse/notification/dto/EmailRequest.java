// backend/src/main/java/com/paypulse/notification/dto/EmailRequest.java

package com.paypulse.notification.dto;

import com.paypulse.notification.domain.NotificationEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fluent builder for constructing an email notification request.
 *
 * Usage:
 * <pre>
 *   EmailRequest.builder()
 *       .to("user@example.com", "John Doe")
 *       .event(NotificationEvent.PAYMENT_COMPLETED)
 *       .paymentId(paymentId)
 *       .variable("amount", "500.00")
 *       .variable("currency", "INR")
 *       .variable("recipientAccount", "XXXX1234")
 *       .build();
 * </pre>
 */
public class EmailRequest {

    private final String recipientEmail;
    private final String recipientName;
    private final NotificationEvent event;
    private final UUID paymentId;
    private final Map<String, Object> variables;

    private EmailRequest(Builder builder) {
        this.recipientEmail = builder.recipientEmail;
        this.recipientName  = builder.recipientName;
        this.event          = builder.event;
        this.paymentId      = builder.paymentId;
        this.variables      = Map.copyOf(builder.variables);
    }

    public static Builder builder() {
        return new Builder();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getRecipientEmail() { return recipientEmail; }
    public String getRecipientName()  { return recipientName; }
    public NotificationEvent getEvent() { return event; }
    public UUID getPaymentId()        { return paymentId; }
    public Map<String, Object> getVariables() { return variables; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {

        private String recipientEmail;
        private String recipientName;
        private NotificationEvent event;
        private UUID paymentId;
        private final Map<String, Object> variables = new HashMap<>();

        private Builder() {}

        /** Recipient's email address and display name. */
        public Builder to(String email, String name) {
            this.recipientEmail = email;
            this.recipientName  = name;
            return this;
        }

        /** The business event driving this notification. */
        public Builder event(NotificationEvent event) {
            this.event = event;
            return this;
        }

        /** Associated payment ID for audit linkage (nullable for non-payment events). */
        public Builder paymentId(UUID paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        /**
         * Add a single template variable.
         * The key must match a Thymeleaf variable in the template (e.g. "amount").
         */
        public Builder variable(String key, Object value) {
            this.variables.put(key, value);
            return this;
        }

        /** Bulk-add template variables from an existing map. */
        public Builder variables(Map<String, Object> vars) {
            this.variables.putAll(vars);
            return this;
        }

        public EmailRequest build() {
            validate();
            return new EmailRequest(this);
        }

        private void validate() {
            if (recipientEmail == null || recipientEmail.isBlank()) {
                throw new IllegalArgumentException("Recipient email is required");
            }
            if (event == null) {
                throw new IllegalArgumentException("Notification event is required");
            }
        }
    }
}