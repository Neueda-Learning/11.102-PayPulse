package com.paypulse.fx.service;

import com.paypulse.common.error.ErrorCode;
import com.paypulse.fx.config.FxProperties;
import com.paypulse.fx.dto.FxRateResponse;
import com.paypulse.payment.service.PaymentException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaticConfigFxRateServiceTest {

    private final StaticConfigFxRateService service = new StaticConfigFxRateService(properties());

    @Test
    void getRate_whenSameCurrency_returnsOneToOneRate() {
        FxRateResponse response = service.getRate("INR", "INR");

        assertThat(response.getRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.getFrom()).isEqualTo("INR");
        assertThat(response.getTo()).isEqualTo("INR");
    }

    @Test
    void getRate_whenConfiguredPair_returnsConfiguredRate() {
        FxRateResponse response = service.getRate("INR", "USD");

        assertThat(response.getRate()).isEqualByComparingTo("0.012");
        assertThat(response.getAsOf()).isEqualTo(Instant.parse("2026-08-06T00:00:00Z"));
    }

    @Test
    void getRate_whenPairMissing_throwsFxRateUnavailable() {
        assertThatThrownBy(() -> service.getRate("USD", "EUR"))
                .isInstanceOf(PaymentException.class)
                .satisfies(ex -> assertThat(((PaymentException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CURRENCY));
    }

    @Test
    void getRate_whenSupportedPairNotConfigured_throwsFxRateUnavailable() {
        FxProperties properties = new FxProperties();
        properties.setAsOf(Instant.parse("2026-08-06T00:00:00Z"));
        properties.setRates(Map.of("INR-USD", new BigDecimal("0.012")));

        StaticConfigFxRateService localService = new StaticConfigFxRateService(properties);

        assertThatThrownBy(() -> localService.getRate("USD", "INR"))
                .isInstanceOf(PaymentException.class)
                .satisfies(ex -> assertThat(((PaymentException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FX_RATE_UNAVAILABLE));
    }

    private FxProperties properties() {
        FxProperties properties = new FxProperties();
        properties.setAsOf(Instant.parse("2026-08-06T00:00:00Z"));
        properties.setRates(Map.of(
                "INR-USD", new BigDecimal("0.012"),
                "USD-INR", new BigDecimal("83.330000")
        ));
        return properties;
    }
}

