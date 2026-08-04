// backend/src/main/java/com/paypulse/notification/repository/NotificationLogRepository.java

package com.paypulse.notification.repository;

import com.paypulse.notification.domain.NotificationLog;
import com.paypulse.notification.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Optional<NotificationLog> findByNotificationId(UUID notificationId);

    List<NotificationLog> findByPaymentId(UUID paymentId);

    Page<NotificationLog> findByStatus(NotificationStatus status, Pageable pageable);

    Page<NotificationLog> findByRecipientEmail(String email, Pageable pageable);

    List<NotificationLog> findByStatusAndAttemptsLessThan(
            NotificationStatus status,
            int maxAttempts
    );
}