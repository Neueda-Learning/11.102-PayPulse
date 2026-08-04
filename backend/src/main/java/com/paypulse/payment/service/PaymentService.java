package com.paypulse.payment.service;

import com.paypulse.account.domain.Account;
import com.paypulse.account.domain.AccountStatus;
import com.paypulse.account.repository.AccountRepository;
import com.paypulse.common.error.ErrorCode;
import com.paypulse.common.idempotency.IdempotencyService;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.api.PaymentMapper;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates POST /payments: idempotency -> account validation -> persistence
 * -> automatic lifecycle progression (FR-2.4, Feature #4, owned by M2's
 * StatusTransitionEngine).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentMapper paymentMapper;
    private final StatusTransitionEngine statusTransitionEngine;

    @Transactional
    public PaymentCreationResult createPayment(String idempotencyKey, CreatePaymentRequest request) {
        return idempotencyService.findExisting(idempotencyKey)
                .map(existing -> new PaymentCreationResult(paymentMapper.toResponse(existing), false))
                .orElseGet(() -> new PaymentCreationResult(
                        paymentMapper.toResponse(createAndProgressPayment(idempotencyKey, request)), true));
    }

    private Payment createAndProgressPayment(String idempotencyKey, CreatePaymentRequest request) {
        Payment created = saveNewPayment(idempotencyKey, request);
        statusTransitionEngine.recordCreation(created, TriggeredBy.CLIENT);
        return statusTransitionEngine.runAutomaticLifecycle(created);
    }

    private Payment saveNewPayment(String idempotencyKey, CreatePaymentRequest request) {
        Account sourceAccount = accountRepository.findById(request.getSourceAccountId())
                .orElseThrow(() -> new PaymentException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "No account found with id " + request.getSourceAccountId()));

        if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new PaymentException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_ACCOUNT,
                    "Source account is not ACTIVE");
        }

        if (request.getCurrency() == null || sourceAccount.getCurrency() == null
                || !sourceAccount.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new PaymentException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_CURRENCY,
                    "Payment currency must match the source account currency");
        }

        Payment payment = paymentMapper.toEntity(request);
        payment.setId(UUID.randomUUID().toString());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setIdempotencyKey(normalizeIdempotencyKey(idempotencyKey));
        payment.setErrorCode(null);
        payment.setErrorMessage(null);

        return paymentRepository.save(payment);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        return (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey.trim();
    }
}