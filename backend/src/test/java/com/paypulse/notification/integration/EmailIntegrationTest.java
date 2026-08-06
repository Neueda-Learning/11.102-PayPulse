// backend/src/test/java/com/paypulse/notification/integration/EmailIntegrationTest.java

package com.paypulse.notification.integration;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.paypulse.notification.domain.NotificationEvent;
import com.paypulse.notification.domain.NotificationStatus;
import com.paypulse.notification.dto.EmailRequest;
import com.paypulse.notification.dto.EmailResult;
import com.paypulse.notification.repository.NotificationLogRepository;
import com.paypulse.notification.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Full integration test using GreenMail (in-process SMTP server).
 * No real emails are sent.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmailIntegrationTest {
    @MockBean
    private io.github.bucket4j.distributed.proxy.ProxyManager<byte[]> proxyManager;

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(
                    GreenMailConfiguration.aConfig()
                            .withUser("test@paypulse.io", "test", "test-password")
            )
            .withPerMethodLifecycle(false);

    @DynamicPropertySource
    static void configureMailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host",     () -> "127.0.0.1");
        registry.add("spring.mail.port",     () -> ServerSetupTest.SMTP.getPort());
        registry.add("spring.mail.username", () -> "test");
        registry.add("spring.mail.password", () -> "test-password");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "true");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("paypulse.notification.email.enabled",     () -> "true");
        registry.add("paypulse.notification.email.from-address",() -> "noreply@paypulse.io");
        registry.add("paypulse.notification.email.from-name",   () -> "PayPulse Payments");
        registry.add("paypulse.notification.email.support-email",() ->"support@paypulse.io");
        registry.add("paypulse.notification.email.base-url",    () -> "http://localhost:3000");
    }

    @Autowired private EmailService           emailService;
    @Autowired private NotificationLogRepository logRepository;

    @Test
    void send_paymentCompleted_deliversMimeMessageAndPersistsLog()
            throws Exception {

        UUID paymentId = UUID.randomUUID();

        EmailRequest request = EmailRequest.builder()
                .to("customer@example.com", "Jane Smith")
                .event(NotificationEvent.PAYMENT_COMPLETED)
                .paymentId(paymentId)
                .variable("amount",             "1500.00")
                .variable("currency",           "INR")
                .variable("referenceId",        "REF-INTEGRATION-001")
                .variable("sourceAccount",      "XXXX001")
                .variable("destinationAccount", "XXXX002")
                .variable("completedAt",        "25 Jun 2025, 03:45 PM UTC")
                .build();

        EmailResult result = emailService.send(request);

        // ── Assert result ─────────────────────────────────────────────────────
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNotificationId()).isNotNull();

        // ── Assert GreenMail received the message ─────────────────────────────
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);

        MimeMessage received = messages[0];
        assertThat(received.getAllRecipients()[0].toString())
                .isEqualTo("customer@example.com");
        assertThat(received.getSubject())
                .contains("Payment Successful");

        // ── Assert DB log ─────────────────────────────────────────────────────
        var logs = logRepository.findByPaymentId(paymentId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(logs.get(0).getRecipientEmail()).isEqualTo("customer@example.com");
    }

    @Test
    void send_paymentFailed_includesFailureReasonInBody() throws Exception {
        UUID paymentId = UUID.randomUUID();

        EmailRequest request = EmailRequest.builder()
                .to("customer@example.com", "John Doe")
                .event(NotificationEvent.PAYMENT_FAILED)
                .paymentId(paymentId)
                .variable("amount",        "750.00")
                .variable("currency",      "USD")
                .variable("referenceId",   "REF-FAIL-001")
                .variable("failureReason", "Insufficient funds in source account")
                .variable("errorCode",     "ERR_INSUFFICIENT_FUNDS")
                .variable("failedAt",      "25 Jun 2025, 04:00 PM UTC")
                .build();

        EmailResult result = emailService.send(request);

        assertThat(result.isSuccess()).isTrue();

        MimeMessage[] messages = greenMail.getReceivedMessages();
        // body contains the failure reason
        String body = messages[messages.length - 1].getContent().toString();
        assertThat(body).containsIgnoringCase("Insufficient funds");
    }

    @Test
    void send_welcome_setsCorrectSubjectAndRecipient() throws Exception {
        EmailRequest request = EmailRequest.builder()
                .to("newuser@example.com", "Alice")
                .event(NotificationEvent.WELCOME)
                .variable("recipientName", "Alice")
                .variable("accountNumber", "ACC-INR-003")
                .variable("currency",      "INR")
                .build();

        emailService.send(request);

        MimeMessage received = greenMail.getReceivedMessages()[
                greenMail.getReceivedMessages().length - 1];
        assertThat(received.getSubject()).containsIgnoringCase("Welcome");
        assertThat(received.getAllRecipients()[0].toString())
                .isEqualTo("newuser@example.com");
    }
}