package com.paypulse.payment.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request payload for POST /api/v1/payments.
 * Contract is aligned with docs/openapi.yaml CreatePaymentRequest schema.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentRequest {

    @NotBlank(message = "sourceAccountId is required")
    private String sourceAccountId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be >= 0.01")
    @DecimalMax(value = "1000000", message = "amount must be <= 1000000")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Pattern(regexp = "INR|USD", message = "currency must be INR or USD")
    private String currency;

    @NotBlank(message = "destinationAccount is required")
    @Size(min = 8, max = 20, message = "destinationAccount length must be 8-20")
    private String destinationAccount;

    @Size(max = 255, message = "reference length must be <= 255")
    private String reference;

    /**
     * Optional, UI/QA-only switch to deterministically force a payment to fail
     * at a specific lifecycle stage. COMPLETE is intentionally excluded from
     * the choices — a payment cannot be forced to fail at completion via this
     * switch (it can still fail there through the existing account-based /
     * random chaos-testing config, just not via this explicit UI control).
     */
    @Pattern(regexp = "NONE|CREATE|VALIDATE|SEND",
            message = "forceFailureStage must be one of NONE, CREATE, VALIDATE, SEND")
    private String forceFailureStage;
}

