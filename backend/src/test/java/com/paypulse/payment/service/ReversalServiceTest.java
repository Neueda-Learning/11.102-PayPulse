package com.paypulse.payment.service;

import com.paypulse.common.error.ErrorCode;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReversalServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    private ReversalService reversalService;

    @BeforeEach
    void setup() {
        reversalService = new ReversalService(paymentRepository, paymentService);
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
                        .isEqualTo(ErrorCode.PAYMENT_NOT_CANCELLABLE));
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

    private static String eq_originalId(Payment original) {
        return original.getId();
    }

    private Payment completedPayment(PaymentStatus status, boolean reversed) {
        return Payment.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId("b2c3d4e5-1111-4a11-8a11-111111111111")
                .destinationAccount("ACC2000002")
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .targetCurrency("INR")
                .convertedAmount(new BigDecimal("100.00"))
                .fxRate(BigDecimal.ONE)
                .status(status)
                .reversed(reversed)
                .version(0L)
                .build();
    }
}