package com.paypulse.payment.repository;

import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
    Page<Payment> findByReferenceContaining(String reference, Pageable pageable);
    List<Payment> findBySourceAccountId(String sourceAccountId);

    long countByCreatedAtBetween(Instant from, Instant to);
    long countByStatusAndCreatedAtBetween(PaymentStatus status, Instant from, Instant to);

    @Query("select p.currency, sum(p.amount) from Payment p where p.createdAt between :from and :to group by p.currency")
    List<Object[]> sumAmountByCurrency(Instant from, Instant to);

    @Query("select p.errorCode, count(p) from Payment p where p.status = 'FAILED' and p.createdAt between :from and :to group by p.errorCode order by count(p) desc")
    List<Object[]> topFailureReasons(Instant from, Instant to);

    @Query("select p from Payment p where " +
            "(:status is null or p.status = :status) and " +
            "(:sourceAccountId is null or p.sourceAccountId = :sourceAccountId) and " +
            "(:search is null or p.id like %:search% or p.reference like %:search%)")
    Page<Payment> search(@Param("status") PaymentStatus status,
                         @Param("search") String search,
                         @Param("sourceAccountId") String sourceAccountId,
                         Pageable pageable);
}