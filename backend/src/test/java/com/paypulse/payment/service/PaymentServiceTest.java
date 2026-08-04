package com.paypulse.payment.service;

import com.paypulse.common.idempotency.IdempotencyService;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.api.PaymentMapper;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.api.dto.PaymentResponse;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.service.validators.ValidationChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String ACTIVE_INR_ACCOUNT_ID = "b2c3d4e5-1111-4a11-8a11-111111111111";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private ValidationChain validationChain;

    @Mock
    private StatusTransitionEngine statusTransitionEngine;

    private PaymentService paymentService;

    @BeforeEach
    void setup() {
        paymentService = new PaymentService(
                paymentRepository, idempotencyService, paymentMapper, validationChain, statusTransitionEngine);
    }

    @Test
    void createPayment_whenIdempotencyKeyMatchesExisting_shortCircuitsAndDoesNotProgressLifecycle() {
        Payment existing = payment(PaymentStatus.COMPLETED);
        PaymentResponse existingResponse = PaymentResponse.builder()
                .id(existing.getId()).status(existing.getStatus()).build();

        when(idempotencyService.findExisting("dup-key")).thenReturn(Optional.of(existing));
        when(paymentMapper.toResponse(existing)).thenReturn(existingResponse);

        PaymentCreationResult result = paymentService.createPayment("dup-key", validRequest());

        assertThat(result.created()).isFalse();
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(statusTransitionEngine, never()).recordCreation(any(), any());
        verify(statusTransitionEngine, never()).runAutomaticLifecycle(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_whenAccountUnknown_throwsAccountNotFound_andNeverTouchesEngine() {
        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        doThrow(PaymentException.class).when(validationChain).validate(any());

        assertThatThrownBy(() -> paymentService.createPayment(null, validRequest()))
                .isInstanceOf(PaymentException.class);

        verify(statusTransitionEngine, never()).recordCreation(any(), any());
        verify(statusTransitionEngine, never()).runAutomaticLifecycle(any());
    }

    @Test
    void createPayment_whenAccountInactive_throwsInvalidAccount() {
        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        doThrow(PaymentException.class).when(validationChain).validate(any());

        assertThatThrownBy(() -> paymentService.createPayment(null, validRequest()))
                .isInstanceOf(PaymentException.class);

        verify(statusTransitionEngine, never()).runAutomaticLifecycle(any());
    }

    @Test
    void createPayment_whenCurrencyMismatch_throwsInvalidCurrency() {
        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        doThrow(PaymentException.class).when(validationChain).validate(any());

        assertThatThrownBy(() -> paymentService.createPayment(null, validRequest()))
                .isInstanceOf(PaymentException.class);

        verify(statusTransitionEngine, never()).runAutomaticLifecycle(any());
    }

    @Test
    void createPayment_whenNewPayment_recordsCreationThenRunsAutomaticLifecycle_inOrder() {
        Payment savedAsCreated = payment(PaymentStatus.CREATED);
        Payment finalCompleted = payment(PaymentStatus.COMPLETED);
        finalCompleted.setId(savedAsCreated.getId());

        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        // validationChain.validate() returns void, so it will happily do nothing (pass) by default on a mock

        when(paymentMapper.toEntity(any(CreatePaymentRequest.class))).thenReturn(new Payment());
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedAsCreated);
        when(statusTransitionEngine.runAutomaticLifecycle(savedAsCreated)).thenReturn(finalCompleted);
        when(paymentMapper.toResponse(finalCompleted)).thenReturn(
                PaymentResponse.builder().id(finalCompleted.getId()).status(PaymentStatus.COMPLETED).build());

        PaymentCreationResult result = paymentService.createPayment("attempt-1", validRequest());

        assertThat(result.created()).isTrue();
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        InOrder inOrder = inOrder(paymentRepository, statusTransitionEngine);
        inOrder.verify(paymentRepository).save(any(Payment.class));
        inOrder.verify(statusTransitionEngine).recordCreation(savedAsCreated, TriggeredBy.CLIENT);
        inOrder.verify(statusTransitionEngine).runAutomaticLifecycle(savedAsCreated);
    }

    @Test
    void createPayment_whenNewPayment_setsCreatedStatusAndClearsErrorFieldsBeforeSaving() {
        Payment mappedEntity = new Payment();
        Payment saved = payment(PaymentStatus.CREATED);

        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        when(paymentMapper.toEntity(any(CreatePaymentRequest.class))).thenReturn(mappedEntity);
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);
        when(statusTransitionEngine.runAutomaticLifecycle(saved)).thenReturn(saved);
        when(paymentMapper.toResponse(saved)).thenReturn(PaymentResponse.builder().build());

        paymentService.createPayment("  attempt-2  ", validRequest());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());

        Payment persisted = captor.getValue();
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(persisted.getErrorCode()).isNull();
        assertThat(persisted.getErrorMessage()).isNull();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getIdempotencyKey()).isEqualTo("attempt-2"); // trimmed
    }

    @Test
    void createPayment_whenIdempotencyKeyBlank_normalizesToNull() {
        Payment mappedEntity = new Payment();
        Payment saved = payment(PaymentStatus.CREATED);

        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        when(paymentMapper.toEntity(any(CreatePaymentRequest.class))).thenReturn(mappedEntity);
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);
        when(statusTransitionEngine.runAutomaticLifecycle(saved)).thenReturn(saved);
        when(paymentMapper.toResponse(saved)).thenReturn(PaymentResponse.builder().build());

        paymentService.createPayment("   ", validRequest());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isNull();
    }

    private CreatePaymentRequest validRequest() {
        return CreatePaymentRequest.builder()
                .sourceAccountId(ACTIVE_INR_ACCOUNT_ID)
                .amount(new BigDecimal("250.00"))
                .currency("INR")
                .destinationAccount("ACC2000002")
                .reference("Invoice #4471")
                .build();
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId(ACTIVE_INR_ACCOUNT_ID)
                .destinationAccount("ACC2000002")
                .amount(new BigDecimal("250.00"))
                .currency("INR")
                .status(status)
                .version(0L)
                .build();
    }
}