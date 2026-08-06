package com.paypulse.fx.api;

import com.paypulse.common.error.ErrorCode;
import com.paypulse.fx.dto.FxRateResponse;
import com.paypulse.fx.service.FxRateService;
import com.paypulse.payment.service.PaymentException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FxController.class)
class FxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FxRateService fxRateService;

    @Test
    void getRate_whenKnownPair_returnsConfiguredRate() throws Exception {
        when(fxRateService.getRate("INR", "USD")).thenReturn(FxRateResponse.builder()
                .from("INR")
                .to("USD")
                .rate(new BigDecimal("0.012"))
                .asOf(Instant.parse("2026-08-06T00:00:00Z"))
                .build());

        mockMvc.perform(get("/api/v1/fx/rate").param("from", "INR").param("to", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("INR"))
                .andExpect(jsonPath("$.to").value("USD"))
                .andExpect(jsonPath("$.rate").value(0.012));
    }

    @Test
    void getRate_whenPairUnavailable_returns404() throws Exception {
        when(fxRateService.getRate("USD", "INR")).thenThrow(
                new PaymentException(HttpStatus.NOT_FOUND, ErrorCode.FX_RATE_UNAVAILABLE,
                        "No configured FX rate for pair USD -> INR")
        );

        mockMvc.perform(get("/api/v1/fx/rate").param("from", "USD").param("to", "INR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FX_RATE_UNAVAILABLE"));
    }
}

