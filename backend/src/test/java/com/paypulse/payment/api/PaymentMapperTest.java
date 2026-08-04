package com.paypulse.payment.api;

import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.api.dto.PaymentHistoryResponse;
import com.paypulse.payment.api.dto.PaymentResponse;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.PaymentStatusHistory;
import com.paypulse.payment.domain.TriggeredBy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapperImpl();

    @Test
    void toEntity_mapsCreateRequestFields() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .sourceAccountId("b2c3d4e5-1111-4a11-8a11-111111111111")
                .amount(new BigDecimal("250.00"))
                .currency("INR")
                .destinationAccount("ACC2000002")
                .reference("Invoice #4471")
                .build();

        Payment payment = mapper.toEntity(request);

        assertThat(payment.getSourceAccountId()).isEqualTo(request.getSourceAccountId());
        assertThat(payment.getAmount()).isEqualByComparingTo(request.getAmount());
        assertThat(payment.getCurrency()).isEqualTo(request.getCurrency());
        assertThat(payment.getDestinationAccount()).isEqualTo(request.getDestinationAccount());
        assertThat(payment.getReference()).isEqualTo(request.getReference());
        assertThat(payment.getId()).isNull();
        assertThat(payment.getStatus()).isNull();
    }

    @Test
    void toResponse_mapsEntityFields() {
        Instant now = Instant.parse("2026-08-04T10:15:30Z");

        Payment payment = Payment.builder()
                .id("f1e2d3c4-4444-4a44-8a44-444444444444")
                .sourceAccountId("b2c3d4e5-1111-4a11-8a11-111111111111")
                .amount(new BigDecimal("999.99"))
                .currency("USD")
                .destinationAccount("ACC8889990")
                .reference("Payroll")
                .status(PaymentStatus.COMPLETED)
                .errorCode(null)
                .errorMessage(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        PaymentResponse response = mapper.toResponse(payment);

        assertThat(response.getId()).isEqualTo(payment.getId());
        assertThat(response.getSourceAccountId()).isEqualTo(payment.getSourceAccountId());
        assertThat(response.getAmount()).isEqualByComparingTo(payment.getAmount());
        assertThat(response.getCurrency()).isEqualTo(payment.getCurrency());
        assertThat(response.getDestinationAccount()).isEqualTo(payment.getDestinationAccount());
        assertThat(response.getReference()).isEqualTo(payment.getReference());
        assertThat(response.getStatus()).isEqualTo(payment.getStatus());
        assertThat(response.getCreatedAt()).isEqualTo(now);
        assertThat(response.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void toHistoryResponse_mapsAuditEntry() {
        Instant now = Instant.parse("2026-08-04T10:20:30Z");

        PaymentStatusHistory history = PaymentStatusHistory.builder()
                .paymentId("payment-1")
                .previousStatus(PaymentStatus.CREATED)
                .newStatus(PaymentStatus.VALIDATED)
                .errorCode(null)
                .errorMessage(null)
                .triggeredBy(TriggeredBy.SYSTEM)
                .occurredAt(now)
                .build();

        PaymentHistoryResponse response = mapper.toHistoryResponse(history);

        assertThat(response.getPreviousStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(response.getNewStatus()).isEqualTo(PaymentStatus.VALIDATED);
        assertThat(response.getTriggeredBy()).isEqualTo("SYSTEM");
        assertThat(response.getOccurredAt()).isEqualTo(now);
    }
}

