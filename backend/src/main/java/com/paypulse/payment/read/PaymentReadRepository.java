package com.paypulse.payment.read;

import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentReadRepository {

    Page<Payment> search(PaymentStatus status, String search, String sourceAccountId, Pageable pageable);

    long count(PaymentStatus status, String search, String sourceAccountId);
}

