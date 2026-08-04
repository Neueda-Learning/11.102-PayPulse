// backend/src/main/java/com/paypulse/notification/dto/EmailResult.java

package com.paypulse.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Result returned after an email send attempt.
 */
public class EmailResult {

    private final boolean success;
    private final UUID notificationId;
    private final String message;
    private final LocalDateTime timestamp;

    private EmailResult(boolean success, UUID notificationId, String message) {
        this.success        = success;
        this.notificationId = notificationId;
        this.message        = message;
        this.timestamp      = LocalDateTime.now();
    }

    public static EmailResult success(UUID notificationId) {
        return new EmailResult(true, notificationId, "Email dispatched successfully");
    }

    public static EmailResult failure(UUID notificationId, String reason) {
        return new EmailResult(false, notificationId, reason);
    }

    public static EmailResult skipped(String reason) {
        return new EmailResult(false, null, "Skipped: " + reason);
    }

    public boolean isSuccess()              { return success; }
    public UUID getNotificationId()         { return notificationId; }
    public String getMessage()              { return message; }
    public LocalDateTime getTimestamp()     { return timestamp; }
}