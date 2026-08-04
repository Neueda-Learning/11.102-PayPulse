package com.paypulse.payment.api;

import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.service.StatusTransitionEngine;
import com.paypulse.payment.service.states.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;


    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private StatusTransitionEngine statusTransitionEngine;

    @MockBean
    private PaymentMapper paymentMapper;

    @Test
    void getHistory_whenPaymentMissing_returns404WithErrorCode() throws Exception {
        when(paymentRepository.findById("missing-id")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/payments/missing-id/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void validatePayment_whenValid_returnsUpdatedPayment() throws Exception {
        Payment payment = payment(PaymentStatus.CREATED);
        Payment updated = payment(PaymentStatus.VALIDATED);
        updated.setId(payment.getId());

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(statusTransitionEngine.validatePayment(eq(payment), eq(TriggeredBy.CLIENT))).thenReturn(updated);
        when(paymentMapper.toResponse(updated)).thenReturn(
                PaymentResponse.builder().id(updated.getId()).status(updated.getStatus()).build());
        mockMvc.perform(post("/api/v1/payments/{id}/validate", payment.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    @Test
    void completePayment_whenIllegalTransition_returns400() throws Exception {
        Payment payment = payment(PaymentStatus.CREATED);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(statusTransitionEngine.completePayment(eq(payment), eq(TriggeredBy.CLIENT)))
                .thenThrow(new InvalidStatusTransitionException(PaymentStatus.CREATED, "complete"));

        mockMvc.perform(post("/api/v1/payments/{id}/complete", payment.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId("b2c3d4e5-1111-4a11-8a11-111111111111")
                .destinationAccount("ACC9001001")
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .status(status)
                .build();
    }
}

