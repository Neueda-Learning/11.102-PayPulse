package com.paypulse.payment.repository;

import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.PaymentStatusHistory;
import com.paypulse.payment.domain.TriggeredBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PaymentStatusHistoryRepositoryTest {

    private static final String SEEDED_ACTIVE_INR_ACCOUNT_ID = "b2c3d4e5-1111-4a11-8a11-111111111111";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentStatusHistoryRepository historyRepository;

    @Test
    void findByPaymentIdOrderByOccurredAtAsc_returnsOrderedEntriesForOnlyOnePayment() {
        Payment p1 = paymentRepository.save(newPayment("hist-1"));
        Payment p2 = paymentRepository.save(newPayment("hist-2"));

        historyRepository.save(PaymentStatusHistory.builder()
                .paymentId(p1.getId())
                .previousStatus(null)
                .newStatus(PaymentStatus.CREATED)
                .triggeredBy(TriggeredBy.CLIENT)
                .occurredAt(Instant.parse("2026-08-03T10:00:02Z"))
                .build());

        historyRepository.save(PaymentStatusHistory.builder()
                .paymentId(p2.getId())
                .previousStatus(null)
                .newStatus(PaymentStatus.CREATED)
                .triggeredBy(TriggeredBy.CLIENT)
                .occurredAt(Instant.parse("2026-08-03T10:00:01Z"))
                .build());

        historyRepository.save(PaymentStatusHistory.builder()
                .paymentId(p1.getId())
                .previousStatus(PaymentStatus.CREATED)
                .newStatus(PaymentStatus.VALIDATED)
                .triggeredBy(TriggeredBy.SYSTEM)
                .occurredAt(Instant.parse("2026-08-03T10:00:03Z"))
                .build());

        var result = historyRepository.findByPaymentIdOrderByOccurredAtAsc(p1.getId());

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(PaymentStatusHistory::getNewStatus)
                .containsExactly(PaymentStatus.CREATED, PaymentStatus.VALIDATED);
    }

    private Payment newPayment(String idempotencyKey) {
        return Payment.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId(SEEDED_ACTIVE_INR_ACCOUNT_ID)
                .destinationAccount("ACC9000001")
                .amount(new BigDecimal("120.00"))
                .currency("INR")
                .targetCurrency("INR")
                .convertedAmount(new BigDecimal("120.00"))
                .fxRate(BigDecimal.ONE)
                .status(PaymentStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .version(0L)
                .build();
    }
}

