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

    // This is the missing method that AnalyticsService is looking for!
    @Query(value =
            "SELECT AVG(TIMESTAMPDIFF(SECOND, p.created_at, h.occurred_at)) " +
                    "FROM payment_status_history h " +
                    "JOIN payment p ON p.id = h.payment_id " +
                    "WHERE h.new_status = 'COMPLETED' " +
                    "AND p.created_at BETWEEN :from AND :to",
            nativeQuery = true)
    Double avgProcessingTimeSeconds(@Param("from") Instant from, @Param("to") Instant to);
}