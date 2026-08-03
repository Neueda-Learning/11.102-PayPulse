package com.paypulse.common.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Uniform error response body returned by every API error.
 * Shape frozen in docs/openapi.yaml components.schemas.ApiError — never add fields
 * without updating the spec first (see docs/13-WORK-DISTRIBUTION.md §3).
 * Owner: M3
 */
@Getter
@Builder
public class ApiError {

    private final String errorCode;
    private final String message;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant timestamp;

    private final String path;

    public static ApiError of(ErrorCode code, String message, String path) {
        return ApiError.builder()
                .errorCode(code.name())
                .message(message)
                .timestamp(Instant.now())
                .path(path)
                .build();
    }
}

