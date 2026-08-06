package com.paypulse.notification.domain;

/**
 * Business events that can trigger an email notification.
 * Maps 1-to-1 with an HTML template in resources/templates/email/.
 */
public enum NotificationEvent {
    PAYMENT_CREATED("payment-created", "Payment Received – Reference #{referenceId}"),
    PAYMENT_COMPLETED("payment-completed", "Payment Successful – {amount} {currency}"),
    PAYMENT_FAILED("payment-failed", "Payment Failed – Action Required"),
    PAYMENT_CANCELLED("payment-cancelled", "Payment Cancelled – Reference #{referenceId}"),
    PAYMENT_REVERSED("payment-reversed", "Payment Reversed – {amount} {currency}"),
    WELCOME("welcome", "Welcome to PayPulse!");


    private final String templateName;
    private final String subjectTemplate;

    NotificationEvent(String templateName, String subjectTemplate) {
        this.templateName = templateName;
        this.subjectTemplate = subjectTemplate;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getSubjectTemplate() {
        return subjectTemplate;
    }


}