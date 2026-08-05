package com.paypulse.payment.service;

import com.paypulse.common.error.ErrorCode;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReversalService {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Transactional
    public Payment reverse(String id) {
        Payment original = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, ErrorCode.PAYMENT_NOT_FOUND,
                        "No payment found with id " + id));

        if (original.getStatus() != PaymentStatus.COMPLETED) {
            throw new PaymentException(HttpStatus.CONFLICT, ErrorCode.PAYMENT_NOT_CANCELLABLE,
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

        return reversalPayment;
    }
}