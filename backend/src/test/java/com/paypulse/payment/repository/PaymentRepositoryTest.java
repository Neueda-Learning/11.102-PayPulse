package com.paypulse.payment.repository;

import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class PaymentRepositoryTest {

    // Seeded in V3__seed_accounts.sql — must reference a real account row (FK constraint)
    private static final String SEEDED_ACTIVE_INR_ACCOUNT_ID = "b2c3d4e5-1111-4a11-8a11-111111111111";

    @Autowired
    private PaymentRepository paymentRepository;

    private Payment newPayment(String idempotencyKey) {
        return Payment.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId(SEEDED_ACTIVE_INR_ACCOUNT_ID)
                .destinationAccount("ACC9999999")
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .targetCurrency("INR")
                .convertedAmount(new BigDecimal("100.00"))
                .fxRate(BigDecimal.ONE)
                .status(PaymentStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .version(0L)
                .build();
    }

    @Test
    void save_andFindById_returnsPayment() {
        Payment saved = paymentRepository.save(newPayment("key-1"));

        var found = paymentRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCurrency()).isEqualTo("INR");
    }

    @Test
    void findByIdempotencyKey_whenExists_returnsPayment() {
        paymentRepository.save(newPayment("key-abc"));

        var found = paymentRepository.findByIdempotencyKey("key-abc");

        assertThat(found).isPresent();
    }

    @Test
    void findByIdempotencyKey_whenMissing_returnsEmpty() {
        var found = paymentRepository.findByIdempotencyKey("no-such-key");

        assertThat(found).isEmpty();
    }

    @Test
    void duplicateIdempotencyKey_throwsDataIntegrityViolation() {
        paymentRepository.saveAndFlush(newPayment("dup-key"));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(newPayment("dup-key")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByStatus_returnsOnlyMatchingStatus() {
        Payment created = newPayment("s1");
        created.setStatus(PaymentStatus.CREATED);
        paymentRepository.save(created);

        Payment failed = newPayment("s2");
        failed.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(failed);

        var page = paymentRepository.findByStatus(PaymentStatus.FAILED, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getIdempotencyKey()).isEqualTo("s2");
    }

    @Test
    void findBySourceAccountId_returnsMatchingPayments() {
        Payment p = newPayment("acc-test");
        paymentRepository.save(p);

        var results = paymentRepository.findBySourceAccountId(SEEDED_ACTIVE_INR_ACCOUNT_ID);

        assertThat(results).hasSize(1);
    }

    @Test
    void findBySourceAccountId_excludesPaymentsFromOtherAccounts(){
        // second seeded account from v3- usd wallet
        String otherAccountId = "c3d4e5f6-2222-4a22-8a22-222222222222";

        Payment ourAccountPayment = newPayment("search-1");
        paymentRepository.save(ourAccountPayment);

        Payment otherAccountPayment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId(otherAccountId)
                .destinationAccount("ACC8888888")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .targetCurrency("USD")
                .convertedAmount(new BigDecimal("50.00"))
                .fxRate(BigDecimal.ONE)
                .status(PaymentStatus.CREATED)
                .idempotencyKey("search-2")
                .version(0L)
                .build();
        paymentRepository.save(otherAccountPayment);

        var results = paymentRepository.findBySourceAccountId(SEEDED_ACTIVE_INR_ACCOUNT_ID);
        assertThat(results)
                .extracting(Payment::getIdempotencyKey)
                .contains("search-1")
                .doesNotContain("search-2");
    }
}