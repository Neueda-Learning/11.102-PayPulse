package com.paypulse.payment.event;

import com.paypulse.account.domain.Account;
import com.paypulse.account.domain.AccountStatus;
import com.paypulse.account.repository.AccountRepository;
import com.paypulse.notification.service.NotificationService;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.service.PaymentStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentNotificationListener listener;

    private Payment payment;
    private Account account;

    @BeforeEach
    void setup() {
        payment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId("b2c3d4e5-1111-4a11-8a11-111111111111")
                .destinationAccount("ACC9998887")
                .amount(new BigDecimal("250.00"))
                .currency("INR")
                .reference("Invoice-4471")
                .createdAt(Instant.parse("2026-08-06T09:10:00Z"))
                .updatedAt(Instant.parse("2026-08-06T09:10:20Z"))
                .status(PaymentStatus.FAILED)
                .build();

        account = Account.builder()
                .id(payment.getSourceAccountId())
                .accountNumber("ACC1002003")
                .ownerEmail("owner@paypulse.io")
                .ownerName("Owner User")
                .currency("INR")
                .status(AccountStatus.ACTIVE)
                .build();

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(accountRepository.findById(payment.getSourceAccountId())).thenReturn(Optional.of(account));

        when(notificationService.notifyPaymentCreated(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(notificationService.notifyPaymentCompleted(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(notificationService.notifyPaymentFailed(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(notificationService.notifyPaymentCancelled(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void onPaymentStatusChanged_whenFailed_parsesStageFromMessage_andPassesTemplateVariables() {
        PaymentStatusChangedEvent event = new PaymentStatusChangedEvent(
                payment.getId(),
                PaymentStatus.CREATED,
                PaymentStatus.FAILED,
                "PROCESSING_ERROR",
                "Simulated failure during CREATE (UI-selected forced failure)",
                TriggeredBy.SYSTEM,
                Instant.parse("2026-08-06T09:10:21Z")
        );

        listener.onPaymentStatusChanged(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).notifyPaymentFailed(
                eq("owner@paypulse.io"),
                eq("Owner User"),
                eq(UUID.fromString(payment.getId())),
                varsCaptor.capture()
        );

        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars).containsEntry("reference", "Invoice-4471");
        assertThat(vars).containsEntry("referenceId", "Invoice-4471");
        assertThat(vars).containsEntry("failedAtStage", "CREATE");
        assertThat(vars).containsEntry("errorCode", "PROCESSING_ERROR");
        assertThat(vars).containsEntry("failedAt", Instant.parse("2026-08-06T09:10:21Z"));
    }

    @Test
    void onPaymentStatusChanged_whenCompleted_includesCompletedAtAndReferenceId() {
        payment.setStatus(PaymentStatus.COMPLETED);

        PaymentStatusChangedEvent event = new PaymentStatusChangedEvent(
                payment.getId(),
                PaymentStatus.SENT,
                PaymentStatus.COMPLETED,
                null,
                null,
                TriggeredBy.SYSTEM,
                Instant.parse("2026-08-06T09:10:20Z")
        );

        listener.onPaymentStatusChanged(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).notifyPaymentCompleted(
                eq("owner@paypulse.io"),
                eq("Owner User"),
                eq(UUID.fromString(payment.getId())),
                varsCaptor.capture()
        );

        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars).containsEntry("referenceId", "Invoice-4471");
        assertThat(vars).containsEntry("completedAt", payment.getUpdatedAt());
        assertThat(vars).containsEntry("submittedAt", payment.getCreatedAt());
    }
}

