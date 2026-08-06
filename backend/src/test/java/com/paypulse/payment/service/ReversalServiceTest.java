package com.paypulse.payment.service;

import com.paypulse.account.domain.Account;
import com.paypulse.account.repository.AccountRepository;
import com.paypulse.common.error.ErrorCode;
import com.paypulse.notification.service.NotificationService;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReversalServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private NotificationService notificationService;

    private ReversalService reversalService;

    @BeforeEach
    void setup() {
        reversalService = new ReversalService(paymentRepository, paymentService, accountRepository, notificationService);
        // Default: no account found -> notifyReversed() no-ops safely (logs a warning, doesn't throw).
        lenient().when(accountRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void reverse_whenPaymentNotFound_throwsPaymentNotFound() {
        when(paymentRepository.findById("missing-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reversalService.reverse("missing-id"))
                .isInstanceOf(PaymentException.class)
                .satisfies(ex -> assertThat(((PaymentException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    void reverse_whenNotCompleted_throwsConflict() {
        Payment payment = completedPayment(PaymentStatus.CREATED, false);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> reversalService.reverse(payment.getId()))
                .isInstanceOf(PaymentException.class)
                .satisfies(ex -> assertThat(((PaymentException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    void reverse_whenAlreadyReversed_throwsConflict() {
        Payment payment = completedPayment(PaymentStatus.COMPLETED, true);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> reversalService.reverse(payment.getId()))
                .isInstanceOf(PaymentException.class)
                .satisfies(ex -> assertThat(((PaymentException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_ALREADY_REVERSED));
    }

    @Test
    void reverse_whenValid_createsReversalPayment_andMarksOriginalReversed() {
        Payment original = completedPayment(PaymentStatus.COMPLETED, false);
        Payment reversalPayment = completedPayment(PaymentStatus.COMPLETED, false);
        reversalPayment.setId(UUID.randomUUID().toString());

        when(paymentRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(paymentService.createReversalPayment(any(), eq_originalId(original)))
                .thenReturn(reversalPayment);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = reversalService.reverse(original.getId());

        assertThat(result).isEqualTo(reversalPayment);
        assertThat(original.isReversed()).isTrue();
        assertThat(original.getReversalPaymentId()).isEqualTo(reversalPayment.getId());

        ArgumentCaptor<Payment> savedCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().isReversed()).isTrue();
    }

    @Test
    void reverse_whenValid_andAccountHasOwnerEmail_sendsReversalNotification() {
        Payment original = completedPayment(PaymentStatus.COMPLETED, false);
        Payment reversalPayment = completedPayment(PaymentStatus.COMPLETED, false);
        reversalPayment.setId(UUID.randomUUID().toString());

        Account account = Account.builder()
                .id(original.getSourceAccountId())
                .accountNumber("ACC1000001")
                .ownerEmail("owner@example.com")
                .ownerName("Jane Doe")
                .build();

        when(paymentRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(paymentService.createReversalPayment(any(), eq_originalId(original)))
                .thenReturn(reversalPayment);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.findById(original.getSourceAccountId())).thenReturn(Optional.of(account));

        reversalService.reverse(original.getId());

        verify(notificationService).notifyPaymentReversed(
                eq_email(account), eq_name(account), any(UUID.class), any());
    }

    private static String eq_originalId(Payment original) {
        return original.getId();
    }

    private static String eq_email(Account account) {
        return account.getOwnerEmail();
    }

    private static String eq_name(Account account) {
        return account.getOwnerName();
    }

    private Payment completedPayment(PaymentStatus status, boolean reversed) {
        return Payment.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId("b2c3d4e5-1111-4a11-8a11-111111111111")
                .destinationAccount("ACC2000002")
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .status(status)
                .reversed(reversed)
                .version(0L)
                .build();
    }
}