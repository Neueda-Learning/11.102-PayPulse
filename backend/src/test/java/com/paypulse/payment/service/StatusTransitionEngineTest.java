package com.paypulse.payment.service;

import com.paypulse.common.resilience.ResilienceConfig;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.repository.PaymentStatusHistoryRepository;
import com.paypulse.payment.service.states.InvalidStatusTransitionException;
import com.paypulse.payment.service.states.PaymentStateFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusTransitionEngineTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentStatusHistoryRepository historyRepository;

    private final PaymentStateFactory stateFactory = new PaymentStateFactory();

    @Mock
    private ResilienceConfig resilienceConfig;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private StatusTransitionEngine engine;

    @BeforeEach
    void setup() {
        engine = new StatusTransitionEngine(
                paymentRepository,
                historyRepository,
                stateFactory,
                resilienceConfig,
                new Random(7),
                eventPublisher
        );

        ReflectionTestUtils.setField(engine, "deterministicFailureAccount", "FAILTEST01");
        ReflectionTestUtils.setField(engine, "randomFailureRate", 0.0d);

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Boolean> supplier = invocation.getArgument(1);
            supplier.get();
            return true;
        }).when(resilienceConfig).execute(any(String.class), any(Supplier.class));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void validatePayment_transitionsCreatedToValidated_andWritesHistory() {
        Payment payment = paymentWithStatus(PaymentStatus.CREATED, "ACC2000002");

        Payment updated = engine.validatePayment(payment, TriggeredBy.CLIENT);

        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
        verify(historyRepository).save(any());
    }

    @Test
    void validatePayment_publishesPaymentStatusChangedEvent() {
        Payment payment = paymentWithStatus(PaymentStatus.CREATED, "ACC2000002");

        engine.validatePayment(payment, TriggeredBy.CLIENT);

        ArgumentCaptor<PaymentStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        PaymentStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.paymentId()).isEqualTo(payment.getId());
        assertThat(event.previousStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(event.newStatus()).isEqualTo(PaymentStatus.VALIDATED);
        assertThat(event.triggeredBy()).isEqualTo(TriggeredBy.CLIENT);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void sendPayment_whenInvalidState_throwsInvalidTransition() {
        Payment payment = paymentWithStatus(PaymentStatus.CREATED, "ACC2000002");

        assertThatThrownBy(() -> engine.sendPayment(payment, TriggeredBy.CLIENT))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void sendPayment_whenDeterministicFailure_transitionsToFailedWithNetworkError() {
        Payment payment = paymentWithStatus(PaymentStatus.VALIDATED, "FAILTEST01");

        Payment updated = engine.sendPayment(payment, TriggeredBy.CLIENT);

        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(updated.getErrorCode()).isEqualTo("NETWORK_ERROR");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(resilienceConfig).execute(eq("paymentSend"), any(Supplier.class));
    }

    @Test
    void completePayment_whenValid_transitionsToCompleted() {
        Payment payment = paymentWithStatus(PaymentStatus.SENT, "ACC2000002");

        Payment updated = engine.completePayment(payment, TriggeredBy.CLIENT);

        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(updated.getErrorCode()).isNull();
        verify(resilienceConfig).execute(eq("paymentComplete"), any(Supplier.class));
    }

    @Test
    void completePayment_whenDeterministicFailure_transitionsToFailedWithNetworkError() {
        Payment payment = paymentWithStatus(PaymentStatus.SENT, "FAILTEST01");

        Payment updated = engine.completePayment(payment, TriggeredBy.CLIENT);

        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(updated.getErrorCode()).isEqualTo("NETWORK_ERROR");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(resilienceConfig).execute(eq("paymentComplete"), any(Supplier.class));
    }

    @Test
    void runAutomaticLifecycle_whenAllStepsSucceed_progressesToCompleted() {
        Payment payment = paymentWithStatus(PaymentStatus.CREATED, "ACC2000002");

        Payment result = engine.runAutomaticLifecycle(payment);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(historyRepository, org.mockito.Mockito.times(3)).save(any());
    }

    @Test
    void runAutomaticLifecycle_whenSendFails_shortCircuitsAndNeverCallsComplete() {
        Payment payment = paymentWithStatus(PaymentStatus.CREATED, "FAILTEST01");

        Payment result = engine.runAutomaticLifecycle(payment);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getErrorCode()).isEqualTo("NETWORK_ERROR");
        verify(resilienceConfig).execute(eq("paymentSend"), any(Supplier.class));
        verify(resilienceConfig, org.mockito.Mockito.never()).execute(eq("paymentComplete"), any(Supplier.class));
    }

    private Payment paymentWithStatus(PaymentStatus status, String destinationAccount) {
        return Payment.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId("b2c3d4e5-1111-4a11-8a11-111111111111")
                .destinationAccount(destinationAccount)
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .status(status)
                .version(0L)
                .build();
    }
}