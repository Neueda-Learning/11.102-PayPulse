package com.paypulse.common.idempotency;


import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IdempotencyServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    private IdempotencyService idempotencyService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(paymentRepository);
    }

    @Test
    void findExisting_withNullKey_returnsEmptyAndSkipsRepository(){
        var result = idempotencyService.findExisting(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void findExisting_withBlankKey_returnsEmptyAndSkipsRepository() {
        var result = idempotencyService.findExisting("   ");

        assertThat(result).isEmpty();
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void findExisting_withKnownKey_returnsPayment() {
        Payment payment = Payment.builder().id("p1").idempotencyKey("key-1").build();
        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(payment));

        var result = idempotencyService.findExisting("key-1");

        assertThat(result).contains(payment);
        verify(paymentRepository).findByIdempotencyKey("key-1");
    }

    @Test
    void findExisting_withUnknownKey_returnsEmpty() {
        when(paymentRepository.findByIdempotencyKey("unknown")).thenReturn(Optional.empty());

        var result = idempotencyService.findExisting("unknown");

        assertThat(result).isEmpty();
    }



}
