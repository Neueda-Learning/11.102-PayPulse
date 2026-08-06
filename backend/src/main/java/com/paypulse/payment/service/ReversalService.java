package com.paypulse.payment.service;

import com.paypulse.account.domain.Account;
import com.paypulse.account.repository.AccountRepository;
import com.paypulse.common.error.ErrorCode;
import com.paypulse.notification.service.NotificationService;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReversalService {

    private static final Logger log = LoggerFactory.getLogger(ReversalService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final AccountRepository accountRepository;
    private final NotificationService notificationService;

    @Transactional
    public Payment reverse(String id) {
        Payment original = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, ErrorCode.PAYMENT_NOT_FOUND,
                        "No payment found with id " + id));

        if (original.getStatus() != PaymentStatus.COMPLETED) {
            throw new PaymentException(HttpStatus.CONFLICT, ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only COMPLETED payments can be reversed");
        }
        if (original.isReversed()) {
            throw new PaymentException(HttpStatus.CONFLICT, ErrorCode.PAYMENT_ALREADY_REVERSED,
                    "Payment has already been reversed");
        }

        CreatePaymentRequest reversalRequest = new CreatePaymentRequest();
        reversalRequest.setSourceAccountId(original.getSourceAccountId());
        reversalRequest.setDestinationAccount(original.getDestinationAccount());
        reversalRequest.setAmount(original.getAmount());
        reversalRequest.setCurrency(original.getCurrency());
        reversalRequest.setReference("Reversal of " + original.getId());

        Payment reversalPayment = paymentService.createReversalPayment(reversalRequest, original.getId());

        original.setReversed(true);
        original.setReversalPaymentId(reversalPayment.getId());
        paymentRepository.save(original);

        notifyReversed(original, reversalPayment);

        return reversalPayment;
    }

    /**
     * REVERSED is a final state for the ORIGINAL payment (from the user's
     * perspective) — notify here directly rather than via
     * PaymentStatusChangedEvent, since the original's PaymentStatus column
     * itself never changes (stays COMPLETED); only the reversed/
     * reversalPaymentId flags flip. The new reversal payment gets its own
     * independent CREATED/COMPLETED/FAILED notifications through the normal
     * event-driven path.
     */
    private void notifyReversed(Payment original, Payment reversalPayment) {
        Optional<Account> accountOpt = accountRepository.findById(original.getSourceAccountId());
        if (accountOpt.isEmpty()) {
            log.warn("Reversal notification skipped: source account {} not found for payment {}",
                    original.getSourceAccountId(), original.getId());
            return;
        }
        Account account = accountOpt.get();
        String email = account.getOwnerEmail();
        String name = account.getOwnerName();
        if (email == null || email.isBlank()) {
            log.warn("Reversal notification skipped: no owner_email on account {} for payment {}",
                    account.getId(), original.getId());
            return;
        }

        Map<String, Object> vars = Map.of(
                "paymentId", original.getId(),
                "amount", original.getAmount(),
                "currency", original.getCurrency(),
                "sourceAccount", account.getAccountNumber(),
                "destinationAccount", original.getDestinationAccount(),
                "reference", original.getReference() != null ? original.getReference() : "",
                "reversalPaymentId", reversalPayment.getId()
        );

        try {
            notificationService.notifyPaymentReversed(email, name, UUID.fromString(original.getId()), vars);
        } catch (Exception ex) {
            log.error("Reversal notification failed for payment {}: {}", original.getId(), ex.getMessage());
            // Never rethrow — notifications must never affect the reversal outcome
        }
    }
}