package com.paypulse.fx.service;

import com.paypulse.common.error.ErrorCode;
import com.paypulse.fx.config.FxProperties;
import com.paypulse.fx.dto.FxRateResponse;
import com.paypulse.payment.service.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StaticConfigFxRateService implements FxRateService {

    private static final Set<String> SUPPORTED = Set.of("INR", "USD");

    private final FxProperties fxProperties;

    @Override
    public FxRateResponse getRate(String from, String to) {
        String normalizedFrom = normalizeCurrency("from", from);
        String normalizedTo = normalizeCurrency("to", to);

        if (normalizedFrom.equals(normalizedTo)) {
            return FxRateResponse.builder()
                    .from(normalizedFrom)
                    .to(normalizedTo)
                    .rate(BigDecimal.ONE)
                    .asOf(fxProperties.getAsOf())
                    .build();
        }

        BigDecimal configuredRate = fxProperties.getRates().get(normalizedFrom + "-" + normalizedTo);
        if (configuredRate == null) {
            throw new PaymentException(HttpStatus.NOT_FOUND, ErrorCode.FX_RATE_UNAVAILABLE,
                    "No configured FX rate for pair " + normalizedFrom + " -> " + normalizedTo);
        }

        return FxRateResponse.builder()
                .from(normalizedFrom)
                .to(normalizedTo)
                .rate(configuredRate)
                .asOf(fxProperties.getAsOf())
                .build();
    }

    private String normalizeCurrency(String fieldName, String currency) {
        if (currency == null || currency.isBlank()) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CURRENCY,
                    fieldName + " currency is required");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(normalized)) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CURRENCY,
                    fieldName + " currency must be INR or USD, got: " + currency);
        }
        return normalized;
    }
}

