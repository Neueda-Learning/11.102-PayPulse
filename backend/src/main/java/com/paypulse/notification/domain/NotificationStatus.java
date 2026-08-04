
package com.paypulse.notification.domain;

/**
 * Lifecycle states for an email notification attempt.
 */
public enum NotificationStatus {

    /** Queued, not yet attempted. */
    PENDING,

    /** Successfully delivered to the SMTP relay. */
    SENT,

    /** All retry attempts exhausted; delivery failed. */
    FAILED,

    /** Notification was deliberately skipped (e.g. feature disabled). */
    SKIPPED
}