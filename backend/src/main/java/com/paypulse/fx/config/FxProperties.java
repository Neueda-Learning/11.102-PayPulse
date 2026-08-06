package com.paypulse.fx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "paypulse.fx")
@Getter
@Setter
public class FxProperties {

    private Instant asOf = Instant.parse("2026-08-06T00:00:00Z");

    private Map<String, BigDecimal> rates = new LinkedHashMap<>();
}

