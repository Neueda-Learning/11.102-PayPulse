package com.paypulse.fx.api;

import com.paypulse.fx.dto.FxRateResponse;
import com.paypulse.fx.service.FxRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx")
@RequiredArgsConstructor
@Tag(name = "FX", description = "Hardcoded currency conversion rate used for cross-currency payments")
public class FxController {

    private final FxRateService fxRateService;

    @GetMapping("/rate")
    @Operation(summary = "Get the currently configured hardcoded FX conversion rate")
    public ResponseEntity<FxRateResponse> getRate(
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(fxRateService.getRate(from, to));
    }
}

