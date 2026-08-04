// backend/src/test/java/com/paypulse/notification/service/EmailServiceTest.java

package com.paypulse.notification.service;

import com.paypulse.notification.domain.NotificationEvent;
import com.paypulse.notification.domain.NotificationLog;
import com.paypulse.notification.domain.NotificationStatus;
import com.paypulse.notification.dto.EmailRequest;
import com.paypulse.notification.dto.EmailResult;
import com.paypulse.notification.repository.NotificationLogRepository;
import com.paypulse.notification.template.EmailTemplateEngine;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender      mailSender;
    @Mock private EmailTemplateEngine templateEngine;
    @Mock private NotificationLogRepository logRepository;
    @Mock private MimeMessage         mimeMessage;

    @InjectMocks private EmailService sut;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "fromAddress",  "noreply@paypulse.io");
        ReflectionTestUtils.setField(sut, "fromName",     "PayPulse Payments");
        ReflectionTestUtils.setField(sut, "emailEnabled", true);
        ReflectionTestUtils.setField(sut, "supportEmail", "support@paypulse.io");
        ReflectionTestUtils.setField(sut, "baseUrl",      "http://localhost:3000");
    }

    private EmailRequest buildRequest() {
        return EmailRequest.builder()
                .to("user@example.com", "John Doe")
                .event(NotificationEvent.PAYMENT_COMPLETED)
                .paymentId(UUID.randomUUID())
                .variable("amount",             "1500.00")
                .variable("currency",           "INR")
                .variable("referenceId",        "REF-001")
                .variable("sourceAccount",      "XXXX001")
                .variable("destinationAccount", "XXXX002")
                .variable("completedAt",        "25 Jun 2025, 03:45 PM UTC")
                .build();
    }

    @Nested
    class WhenEmailIsEnabled {

        @Test
        void send_returnsSuccessAndPersistsLog() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(templateEngine.render(anyString(), anyMap()))
                    .thenReturn("<html>Hello John</html>");

            NotificationLog savedLog = mock(NotificationLog.class);
            when(savedLog.getNotificationId()).thenReturn(UUID.randomUUID());
            when(logRepository.save(any())).thenReturn(savedLog);

            EmailResult result = sut.send(buildRequest());

            assertThat(result.isSuccess()).isTrue();
            verify(mailSender).send(mimeMessage);
            // saved twice: PENDING then SENT
            verify(logRepository, times(2)).save(any(NotificationLog.class));
        }

        @Test
        void send_marksLogFailedWhenSmtpThrows() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(templateEngine.render(anyString(), anyMap()))
                    .thenReturn("<html>body</html>");

            NotificationLog savedLog = mock(NotificationLog.class);
            when(savedLog.getNotificationId()).thenReturn(UUID.randomUUID());
            when(logRepository.save(any())).thenReturn(savedLog);

            doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                    .when(mailSender).send(any(MimeMessage.class));

            EmailResult result = sut.send(buildRequest());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("SMTP");
        }

        @Test
        void send_marksLogFailedWhenTemplateRenderingFails() {
            when(templateEngine.render(anyString(), anyMap()))
                    .thenThrow(new IllegalStateException("Template not found"));

            NotificationLog savedLog = mock(NotificationLog.class);
            when(savedLog.getNotificationId()).thenReturn(UUID.randomUUID());
            when(logRepository.save(any())).thenReturn(savedLog);

            EmailResult result = sut.send(buildRequest());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Template");
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        void send_injectsGlobalVariablesIntoContext() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            ArgumentCaptor<Map<String, Object>> ctxCaptor =
                    ArgumentCaptor.forClass(Map.class);
            when(templateEngine.render(anyString(), ctxCaptor.capture()))
                    .thenReturn("<html>ok</html>");

            NotificationLog savedLog = mock(NotificationLog.class);
            when(savedLog.getNotificationId()).thenReturn(UUID.randomUUID());
            when(logRepository.save(any())).thenReturn(savedLog);

            sut.send(buildRequest());

            Map<String, Object> ctx = ctxCaptor.getValue();
            assertThat(ctx).containsKey("supportEmail");
            assertThat(ctx).containsKey("baseUrl");
            assertThat(ctx).containsKey("recipientName");
            // caller-supplied variables also present
            assertThat(ctx).containsEntry("amount", "1500.00");
            assertThat(ctx).containsEntry("currency", "INR");
        }
    }

    @Nested
    class WhenEmailIsDisabled {

        @BeforeEach
        void disableEmail() {
            ReflectionTestUtils.setField(sut, "emailEnabled", false);
        }

        @Test
        void send_skipsDeliveryAndPersistsSkippedLog() {
            NotificationLog savedLog = mock(NotificationLog.class);
            when(savedLog.getNotificationId()).thenReturn(UUID.randomUUID());
            when(logRepository.save(any())).thenReturn(savedLog);

            EmailResult result = sut.send(buildRequest());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("skipped");
            verify(mailSender, never()).send(any(MimeMessage.class));
        }
    }
}