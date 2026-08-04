package com.paypulse.payment.repository;

import com.paypulse.payment.domain.PaymentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, Long> {

    List<PaymentStatusHistory> findByPaymentIdOrderByOccurredAtAsc(String paymentId);

    @Query("select avg(function('TIMESTAMPDIFF', SECOND, c.occurredAt, t.occurredAt)) " +
            "from PaymentStatusHistory c, PaymentStatusHistory t " +
            "where c.paymentId = t.paymentId and c.previousStatus is null " +
            "and t.newStatus in ('COMPLETED','FAILED') and c.occurredAt between :from and :to")
    Double avgProcessingTimeSeconds(@Param("from") Instant from, @Param("to") Instant to);
}
