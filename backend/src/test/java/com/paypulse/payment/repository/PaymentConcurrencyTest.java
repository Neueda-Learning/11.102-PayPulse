package com.paypulse.payment.repository;

import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.PaymentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves NFR-8: @Version column prevents two concurrent transitions
 * on the SAME payment row from silently overwriting each other.
 * Owner: M1 (Day 5, docs/13-WORK-DISTRIBUTION.md).
 */
@DataJpaTest
class PaymentConcurrencyTest {

    private static final String SEEDED_ACTIVE_INR_ACCOUNT_ID = "b2c3d4e5-1111-4a11-8a11-111111111111";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void concurrentUpdate_withStaleVersion_throwsOptimisticLockException() {
        // Arrange: save and commit one payment row
        Payment original = Payment.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId(SEEDED_ACTIVE_INR_ACCOUNT_ID)
                .destinationAccount("ACC9999999")
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .targetCurrency("INR")
                .convertedAmount(new BigDecimal("100.00"))
                .fxRate(BigDecimal.ONE)
                .status(PaymentStatus.CREATED)
                .version(0L)
                .build();
        paymentRepository.saveAndFlush(original);
        entityManager.clear(); // detach so both loads below are independent

        // Simulate two concurrent "sessions" loading the same row
        Payment session1Copy = paymentRepository.findById(original.getId()).orElseThrow();
        entityManager.detach(session1Copy);

        Payment session2Copy = paymentRepository.findById(original.getId()).orElseThrow();

        // Session 2 updates and commits first — version bumps 0 -> 1
        session2Copy.setStatus(PaymentStatus.VALIDATED);
        paymentRepository.saveAndFlush(session2Copy);
        entityManager.clear();

        // Session 1 still holds the OLD version (0) and tries to update — must fail
        session1Copy.setStatus(PaymentStatus.FAILED);

//

        assertThatThrownBy(() -> {
            paymentRepository.saveAndFlush(session1Copy);
        }).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // Final DB state must reflect session 2's committed change only
        Payment finalState = paymentRepository.findById(original.getId()).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
    }
}