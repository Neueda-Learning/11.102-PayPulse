package com.paypulse.payment.service;

import com.paypulse.account.domain.Account;
import com.paypulse.account.domain.AccountStatus;
import com.paypulse.account.repository.AccountRepository;
import com.paypulse.common.idempotency.IdempotencyService;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.api.PaymentMapper;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.api.dto.PaymentResponse;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private AccountRepository accountRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private StatusTransitionEngine statusTransitionEngine;

    private PaymentService paymentService;

    @BeforeEach
    void setup() {
        paymentService = new PaymentService(
                paymentRepository, accountRepository, idempotencyService, paymentMapper,
                statusTransitionEngine, new Random(42));
        ReflectionTestUtils.setField(paymentService, "createFailureAccount", "FAILCREATE01");
        ReflectionTestUtils.setField(paymentService, "randomFailureRate", 0.0d);
    }

    @Test
    void createPayment_whenDeterministicCreationFailureAccount_throwsServiceUnavailable_andNeverSaves() {
        Account activeInr = account(AccountStatus.ACTIVE, "INR");
        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        when(accountRepository.findById(ACTIVE_INR_ACCOUNT_ID)).thenReturn(Optional.of(activeInr));

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .sourceAccountId(ACTIVE_INR_ACCOUNT_ID)
                .amount(new BigDecimal("250.00"))
                .currency("INR")
                .destinationAccount("FAILCREATE01")
                .reference("chaos-test")
                .build();

        assertThatThrownBy(() -> paymentService.createPayment("key-1", request))
                .isInstanceOf(PaymentException.class)
                .satisfies(ex -> assertThat(((PaymentException) ex).getStatus())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verify(paymentRepository, never()).save(any());
        verify(statusTransitionEngine, never()).recordCreation(any(), any());
    }

    @Test
    void createPayment_whenForceFailureStageIsCreate_throwsServiceUnavailable_andNeverSaves() {
        Account activeInr = account(AccountStatus.ACTIVE, "INR");
        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        when(accountRepository.findById(ACTIVE_INR_ACCOUNT_ID)).thenReturn(Optional.of(activeInr));

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .sourceAccountId(ACTIVE_INR_ACCOUNT_ID)
                .amount(new BigDecimal("250.00"))
                .currency("INR")
                .destinationAccount("ACC2000002")
                .forceFailureStage("CREATE")
                .build();

        assertThatThrownBy(() -> paymentService.createPayment("key-2", request))
                .isInstanceOf(PaymentException.class)
                .satisfies(ex -> assertThat(((PaymentException) ex).getStatus())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verify(paymentRepository, never()).save(any());
        verify(statusTransitionEngine, never()).recordCreation(any(), any());
    }

    @Test
    void createPayment_whenForceFailureStageIsSend_propagatesOntoPersistedPayment() {
        Account activeInr = account(AccountStatus.ACTIVE, "INR");
        Payment mappedEntity = new Payment();
        Payment saved = payment(PaymentStatus.CREATED);

        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        when(accountRepository.findById(ACTIVE_INR_ACCOUNT_ID)).thenReturn(Optional.of(activeInr));
        when(paymentMapper.toEntity(any(CreatePaymentRequest.class))).thenReturn(mappedEntity);
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);
        when(statusTransitionEngine.runAutomaticLifecycle(saved)).thenReturn(saved);
        when(paymentMapper.toResponse(saved)).thenReturn(PaymentResponse.builder().build());

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .sourceAccountId(ACTIVE_INR_ACCOUNT_ID)
                .amount(new BigDecimal("250.00"))
                .currency("INR")
                .destinationAccount("ACC2000002")
                .forceFailureStage("SEND")
                .build();

        paymentService.createPayment("attempt-3", request);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getForcedFailureStage()).isEqualTo("SEND");
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
        when(accountRepository.findById(ACTIVE_INR_ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(null, validRequest()))
                .isInstanceOf(PaymentException.class);

        verify(statusTransitionEngine, never()).recordCreation(any(), any());
        verify(statusTransitionEngine, never()).runAutomaticLifecycle(any());
    }

    @Test
    void createPayment_whenAccountInactive_throwsInvalidAccount() {
        Account inactive = account(AccountStatus.INACTIVE, "INR");
        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        when(accountRepository.findById(ACTIVE_INR_ACCOUNT_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> paymentService.createPayment(null, validRequest()))
                .isInstanceOf(PaymentException.class);

        verify(statusTransitionEngine, never()).runAutomaticLifecycle(any());
    }

    @Test
    void createPayment_whenCurrencyMismatch_throwsInvalidCurrency() {
        Account usdAccount = account(AccountStatus.ACTIVE, "USD");
        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        when(accountRepository.findById(ACTIVE_INR_ACCOUNT_ID)).thenReturn(Optional.of(usdAccount));

        assertThatThrownBy(() -> paymentService.createPayment(null, validRequest()))
                .isInstanceOf(PaymentException.class);

        verify(statusTransitionEngine, never()).runAutomaticLifecycle(any());
    }

    @Test
    void createPayment_whenNewPayment_recordsCreationThenRunsAutomaticLifecycle_inOrder() {
        Account activeInr = account(AccountStatus.ACTIVE, "INR");
        Payment savedAsCreated = payment(PaymentStatus.CREATED);
        Payment finalCompleted = payment(PaymentStatus.COMPLETED);
        finalCompleted.setId(savedAsCreated.getId());

        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        when(accountRepository.findById(ACTIVE_INR_ACCOUNT_ID)).thenReturn(Optional.of(activeInr));
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
        Account activeInr = account(AccountStatus.ACTIVE, "INR");
        Payment mappedEntity = new Payment();
        Payment saved = payment(PaymentStatus.CREATED);

        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        when(accountRepository.findById(ACTIVE_INR_ACCOUNT_ID)).thenReturn(Optional.of(activeInr));
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
        Account activeInr = account(AccountStatus.ACTIVE, "INR");
        Payment mappedEntity = new Payment();
        Payment saved = payment(PaymentStatus.CREATED);

        when(idempotencyService.findExisting(any())).thenReturn(Optional.empty());
        when(accountRepository.findById(ACTIVE_INR_ACCOUNT_ID)).thenReturn(Optional.of(activeInr));
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

    private Account account(AccountStatus status, String currency) {
        return Account.builder()
                .id(ACTIVE_INR_ACCOUNT_ID)
                .label("Primary")
                .accountNumber("ACC1000001")
                .currency(currency)
                .status(status)
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