package com.paypulse.common.idempotency;

import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final PaymentRepository paymentRepository;

    /**     * Returns the existing payment for this key, if any.     * Empty if key is null/blank (no dedup requested — A-5) or unseen.     */
    public Optional<Payment> findExisting(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return paymentRepository.findByIdempotencyKey(idempotencyKey);
    }
}
