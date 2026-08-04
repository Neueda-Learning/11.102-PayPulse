package com.paypulse.payment.repository;


import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
    Page<Payment> findByReferenceContaining(String reference, Pageable pageable);
    List<Payment> findBySourceAccountId(String sourceAccountId);

}
