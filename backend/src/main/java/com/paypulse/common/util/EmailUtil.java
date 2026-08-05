// backend/src/main/java/com/paypulse/common/util/EmailUtil.java

package com.paypulse.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Static utility methods for building email variable maps.
 *
 * Centralises the key-name conventions so templates and callers
 * always agree on variable names. No Spring beans required.
 */
public final class EmailUtil {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private EmailUtil() {}

    // ── Variable Map Builders ─────────────────────────────────────────────────

    /**
     * Build variables for PAYMENT_CREATED and PAYMENT_VALIDATED notifications.
     */
    public static Map<String, Object> paymentCreatedVars(
            UUID   paymentId,
            String referenceId,
            String amount,
            String currency,
            String sourceAccount,
            String destinationAccount,
            LocalDateTime submittedAt
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("paymentId",          paymentId.toString());
        vars.put("referenceId",        referenceId);
        vars.put("amount",             amount);
        vars.put("currency",           currency);
        vars.put("sourceAccount",      maskAccount(sourceAccount));
        vars.put("destinationAccount", maskAccount(destinationAccount));
        vars.put("submittedAt",        format(submittedAt));
        return vars;
    }

    /**
     * Build variables for PAYMENT_COMPLETED notifications.
     */
    public static Map<String, Object> paymentCompletedVars(
            UUID   paymentId,
            String referenceId,
            String amount,
            String currency,
            String sourceAccount,
            String destinationAccount,
            LocalDateTime completedAt
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("paymentId",          paymentId.toString());
        vars.put("referenceId",        referenceId);
        vars.put("amount",             amount);
        vars.put("currency",           currency);
        vars.put("sourceAccount",      maskAccount(sourceAccount));
        vars.put("destinationAccount", maskAccount(destinationAccount));
        vars.put("completedAt",        format(completedAt));
        return vars;
    }

    /**
     * Build variables for PAYMENT_FAILED notifications.
     */
    public static Map<String, Object> paymentFailedVars(
            UUID   paymentId,
            String referenceId,
            String amount,
            String currency,
            String failureReason,
            String errorCode,
            LocalDateTime failedAt
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("paymentId",     paymentId.toString());
        vars.put("referenceId",   referenceId);
        vars.put("amount",        amount);
        vars.put("currency",      currency);
        vars.put("failureReason", failureReason);
        vars.put("errorCode",     errorCode != null ? errorCode : "UNKNOWN");
        vars.put("failedAt",      format(failedAt));
        return vars;
    }

    /**
     * Build variables for WELCOME notifications.
     */
    public static Map<String, Object> welcomeVars(
            String recipientName,
            String accountNumber,
            String currency
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName",  recipientName);
        vars.put("accountNumber",  maskAccount(accountNumber));
        vars.put("currency",       currency);
        return vars;
    }

    // ── Formatting Helpers ────────────────────────────────────────────────────

    /**
     * Masks an account number showing only the last 4 characters.
     * e.g. "ACC-INR-001" → "XXXX-001"  or  "1234567890" → "XXXXXX7890"
     */
    public static String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) return accountNumber;
        String visible = accountNumber.substring(accountNumber.length() - 4);
        return "XXXX" + accountNumber.charAt(accountNumber.length() - 5) + visible;
    }

    /**
     * Format a LocalDateTime for human-readable display in emails.
     */
    public static String format(LocalDateTime dateTime) {
        if (dateTime == null) return "—";
        return dateTime.format(DISPLAY_FORMAT) + " UTC";
    }

    /**
     * Shorten a UUID for display (first 8 characters + ellipsis).
     */
    public static String shortId(UUID uuid) {
        return uuid.toString().substring(0, 8).toUpperCase() + "…";
    }
}