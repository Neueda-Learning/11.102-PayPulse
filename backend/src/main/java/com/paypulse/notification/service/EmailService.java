// backend/src/main/java/com/paypulse/notification/service/EmailService.java

package com.paypulse.notification.service;

import com.paypulse.notification.domain.NotificationLog;
import com.paypulse.notification.domain.NotificationStatus;
import com.paypulse.notification.dto.EmailRequest;
import com.paypulse.notification.dto.EmailResult;
import com.paypulse.notification.repository.NotificationLogRepository;
import com.paypulse.notification.template.EmailTemplateEngine;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Core email delivery service.
 *
 * Responsibilities:
 *  - Persist a NotificationLog record BEFORE sending (audit trail)
 *  - Render the HTML body via Thymeleaf
 *  - Send via JavaMailSender (SMTP)
 *  - Update the log record with SENT / FAILED result
 *
 * All sends are async so the calling thread (e.g. payment processing)
 * is never blocked waiting for SMTP.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender      mailSender;
    private final EmailTemplateEngine templateEngine;
    private final NotificationLogRepository logRepository;

    @Value("${paypulse.notification.email.from-address}")
    private String fromAddress;

    @Value("${paypulse.notification.email.from-name}")
    private String fromName;

    @Value("${paypulse.notification.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${paypulse.notification.email.support-email:support@paypulse.io}")
    private String supportEmail;

    @Value("${paypulse.notification.email.base-url:http://localhost:3000}")
    private String baseUrl;

    public EmailService(
            JavaMailSender mailSender,
            EmailTemplateEngine templateEngine,
            NotificationLogRepository logRepository
    ) {
        this.mailSender     = mailSender;
        this.templateEngine = templateEngine;
        this.logRepository  = logRepository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Send an email asynchronously.
     * Returns a CompletableFuture so callers can optionally await the result.
     *
     * @param request fully constructed EmailRequest (use EmailRequest.builder())
     * @return EmailResult indicating success or failure
     */
    @Async
    @Transactional
    public CompletableFuture<EmailResult> sendAsync(EmailRequest request) {
        return CompletableFuture.completedFuture(doSend(request));
    }

    /**
     * Send an email synchronously (use in tests or when result is required).
     *
     * @param request fully constructed EmailRequest
     * @return EmailResult indicating success or failure
     */
    @Transactional
    public EmailResult send(EmailRequest request) {
        return doSend(request);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private EmailResult doSend(EmailRequest request) {

        // ── 1. Resolve subject ────────────────────────────────────────────────
        String subject = resolveSubject(request);

        // ── 2. Persist audit log (PENDING) ────────────────────────────────────
        NotificationLog notifLog = NotificationLog.create(
                request.getRecipientEmail(),
                request.getRecipientName(),
                subject,
                request.getEvent(),
                request.getVariables(),
                request.getPaymentId()
        );
        notifLog = logRepository.save(notifLog);

        // ── 3. Feature flag check ─────────────────────────────────────────────
        if (!emailEnabled) {
            log.warn("Email notifications are disabled. Skipping send for notificationId={}",
                    notifLog.getNotificationId());
            notifLog.markSkipped("Email feature is disabled via configuration");
            logRepository.save(notifLog);
            return EmailResult.skipped("Email feature disabled");
        }

        // ── 4. Build template context (inject global variables) ───────────────
        Map<String, Object> ctx = buildContext(request);

        // ── 5. Render HTML body ───────────────────────────────────────────────
        String htmlBody;
        try {
            htmlBody = templateEngine.render(request.getEvent().getTemplateName(), ctx);
        } catch (Exception ex) {
            log.error("Template rendering failed for notificationId={}: {}",
                    notifLog.getNotificationId(), ex.getMessage());
            notifLog.markFailed("Template rendering error: " + ex.getMessage());
            logRepository.save(notifLog);
            return EmailResult.failure(notifLog.getNotificationId(), ex.getMessage());
        }

        // ── 6. Build and send MIME message ────────────────────────────────────
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromAddress, fromName);
            helper.setTo(request.getRecipientEmail());
            helper.setSubject(subject);
            helper.setText(htmlBody, true);   // true = isHtml

            mailSender.send(mimeMessage);

            notifLog.markSent();
            logRepository.save(notifLog);

            log.info("Email sent successfully: notificationId={}, event={}, recipient={}",
                    notifLog.getNotificationId(),
                    request.getEvent(),
                    request.getRecipientEmail());

            return EmailResult.success(notifLog.getNotificationId());

        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            String reason = "SMTP delivery failure: " + ex.getMessage();
            log.error("Failed to send email notificationId={}: {}",
                    notifLog.getNotificationId(), ex.getMessage(), ex);
            notifLog.markFailed(reason);
            logRepository.save(notifLog);
            return EmailResult.failure(notifLog.getNotificationId(), reason);
        }
    }

    /**
     * Builds the full variable context passed to Thymeleaf.
     * Merges global platform variables with caller-supplied variables.
     * Caller-supplied values always win (they override globals of the same key).
     */
    private Map<String, Object> buildContext(EmailRequest request) {
        Map<String, Object> ctx = new HashMap<>();

        // Global variables available in ALL templates
        ctx.put("supportEmail", supportEmail);
        ctx.put("baseUrl",      baseUrl);
        ctx.put("fromName",     fromName);
        ctx.put("recipientName",
                request.getRecipientName() != null ? request.getRecipientName() : "Valued Customer");

        // Caller-supplied variables (may override globals)
        if (request.getVariables() != null) {
            ctx.putAll(request.getVariables());
        }

        return ctx;
    }

    /**
     * Resolves the email subject by substituting simple {placeholder} tokens
     * from the event's subject template using the request's variables.
     */
    private String resolveSubject(EmailRequest request) {
        String template = request.getEvent().getSubjectTemplate();
        if (request.getVariables() == null) return template;

        String resolved = template;
        for (Map.Entry<String, Object> entry : request.getVariables().entrySet()) {
            resolved = resolved.replace(
                    "{" + entry.getKey() + "}",
                    entry.getValue() != null ? entry.getValue().toString() : ""
            );
        }
        return resolved;
    }
}