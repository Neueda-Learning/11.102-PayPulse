package com.paypulse.fx.service;

import com.paypulse.fx.dto.FxRateResponse;

public interface FxRateService {
    FxRateResponse getRate(String from, String to);
}

