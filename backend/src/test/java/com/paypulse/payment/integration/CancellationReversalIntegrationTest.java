package com.paypulse.payment.integration;

import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.api.dto.PaymentResponse;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full HTTP round-trip tests for Feature #18 (Cancellation) and #19 (Reversal).
 * Owner: M2.
 *
 * NOTE (MEM-007/MEM-029/Q15): auto-progression (runAutomaticLifecycle) always
 * races a freshly-created payment past CREATED synchronously, so a real
 * POST /payments call can never leave a payment cancellable. Cancellation
 * tests seed a CREATED row directly via the repository to exercise the
 * cancel endpoint meaningfully — this mirrors the real limitation flagged
 * in 12-CLARIFICATION-QUESTIONS.md Q15, not a test workaround for a bug.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"paypulse.simulation.random-failure-rate=0"})
@ActiveProfiles("test")
class CancellationReversalIntegrationTest {

    private static final String INR_ACCOUNT_ID = "b2c3d4e5-1111-4a11-8a11-111111111111";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void cancel_whenPaymentIsCreated_transitionsToCancelled() {
        Payment seeded = seedCreatedPayment();

        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                "/api/v1/payments/{id}/cancel", null, PaymentResponse.class, seeded.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void cancel_whenAlreadyProgressedPastCreated_returns409() {
        PaymentResponse completed = createAndAutoCompletePayment();
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/payments/{id}/cancel", null, Map.class, completed.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("errorCode")).isEqualTo("PAYMENT_NOT_CANCELLABLE");
    }

    @Test
    void cancel_whenPaymentMissing_returns404() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/payments/{id}/cancel", null, Map.class, "non-existent-id");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("errorCode")).isEqualTo("PAYMENT_NOT_FOUND");
    }

    @Test
    void createCompleteThenReverse_roundTrip_originalUntouched_newPaymentIndependentlyProcessed() {
        PaymentResponse original = createAndAutoCompletePayment();
        assertThat(original.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        ResponseEntity<PaymentResponse> reverseResponse = restTemplate.postForEntity(
                "/api/v1/payments/{id}/reverse", null, PaymentResponse.class, original.getId());

        assertThat(reverseResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse reversal = reverseResponse.getBody();
        assertThat(reversal).isNotNull();
        assertThat(reversal.getId()).isNotEqualTo(original.getId());
        assertThat(reversal.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(reversal.getReversalOfPaymentId()).isEqualTo(original.getId());

        // Original untouched (still COMPLETED) but now flagged reversed with a link.
        ResponseEntity<PaymentResponse> originalAfter = restTemplate.getForEntity(
                "/api/v1/payments/{id}", PaymentResponse.class, original.getId());
        assertThat(originalAfter.getBody()).isNotNull();
        assertThat(originalAfter.getBody().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(originalAfter.getBody().isReversed()).isTrue();
        assertThat(originalAfter.getBody().getReversalPaymentId()).isEqualTo(reversal.getId());
    }

    @Test
    void reverse_whenNotCompleted_returns409() {
        Payment seeded = seedCreatedPayment();

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/payments/{id}/reverse", null, Map.class, seeded.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("errorCode")).isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void reverse_whenAlreadyReversed_returns409() {
        PaymentResponse original = createAndAutoCompletePayment();
        restTemplate.postForEntity("/api/v1/payments/{id}/reverse", null, PaymentResponse.class, original.getId());

        ResponseEntity<Map> secondReverse = restTemplate.postForEntity(
                "/api/v1/payments/{id}/reverse", null, Map.class, original.getId());

        assertThat(secondReverse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondReverse.getBody()).isNotNull();
        assertThat(secondReverse.getBody().get("errorCode")).isEqualTo("PAYMENT_ALREADY_REVERSED");
    }

    @Test
    void reverse_whenPaymentMissing_returns404() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/payments/{id}/reverse", null, Map.class, "non-existent-id");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("errorCode")).isEqualTo("PAYMENT_NOT_FOUND");
    }

    private Payment seedCreatedPayment() {
        Payment payment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId(INR_ACCOUNT_ID)
                .destinationAccount("ACC9009009")
                .amount(new BigDecimal("50.00"))
                .currency("INR")
                .targetCurrency("INR")
                .convertedAmount(new BigDecimal("50.00"))
                .fxRate(BigDecimal.ONE)
                .status(PaymentStatus.CREATED)
                .version(0L)
                .build();
        return paymentRepository.save(payment);
    }

    private PaymentResponse createAndAutoCompletePayment() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .sourceAccountId(INR_ACCOUNT_ID)
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .targetCurrency("INR")
                .destinationAccount("ACC2000002")
                .reference("Integration test payment")
                .forceFailureStage("NONE")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                "/api/v1/payments", new HttpEntity<>(request, headers), PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}