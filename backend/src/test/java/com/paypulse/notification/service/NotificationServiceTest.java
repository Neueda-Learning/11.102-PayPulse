// backend/src/test/java/com/paypulse/notification/service/NotificationServiceTest.java

package com.paypulse.notification.service;

import com.paypulse.notification.domain.NotificationEvent;
import com.paypulse.notification.dto.EmailRequest;
import com.paypulse.notification.dto.EmailResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock  private EmailService emailService;
    @InjectMocks private NotificationService sut;

    private final UUID paymentId = UUID.randomUUID();

    @Test
    void notifyPaymentCompleted_delegatesToEmailService() {
        when(emailService.sendAsync(any(EmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        EmailResult.success(UUID.randomUUID())));

        CompletableFuture<EmailResult> future = sut.notifyPaymentCompleted(
                "user@example.com", "John",
                paymentId,
                Map.of("amount", "1000.00", "currency", "INR")
        );

        assertThat(future).isCompletedWithValueMatching(EmailResult::isSuccess);
        verify(emailService).sendAsync(any(EmailRequest.class));
    }

    @Test
    void notifyPaymentFailed_delegatesToEmailService() {
        when(emailService.sendAsync(any(EmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        EmailResult.success(UUID.randomUUID())));

        sut.notifyPaymentFailed(
                "user@example.com", "Jane",
                paymentId,
                Map.of("failureReason", "Insufficient funds", "errorCode", "ERR_002")
        );

        ArgumentCaptor<EmailRequest> captor =
                ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendAsync(captor.capture());

        assertThat(captor.getValue().getEvent())
                .isEqualTo(NotificationEvent.PAYMENT_FAILED);
        assertThat(captor.getValue().getPaymentId()).isEqualTo(paymentId);
    }

    @Test
    void notifyPaymentCreated_setsCorrectEvent() {
        when(emailService.sendAsync(any())).thenReturn(
                CompletableFuture.completedFuture(EmailResult.success(UUID.randomUUID())));

        sut.notifyPaymentCreated("a@b.com", "Alice", paymentId, Map.of());

        ArgumentCaptor<EmailRequest> captor =
                ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendAsync(captor.capture());
        assertThat(captor.getValue().getEvent())
                .isEqualTo(NotificationEvent.PAYMENT_CREATED);
    }

    @Test
    void sendWelcomeEmail_passesNullPaymentId() {
        when(emailService.sendAsync(any())).thenReturn(
                CompletableFuture.completedFuture(EmailResult.success(UUID.randomUUID())));

        sut.sendWelcomeEmail("new@user.com", "Bob",
                Map.of("currency", "USD", "accountNumber", "ACC-USD-001"));

        ArgumentCaptor<EmailRequest> captor =
                ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendAsync(captor.capture());
        assertThat(captor.getValue().getPaymentId()).isNull();
    }
}