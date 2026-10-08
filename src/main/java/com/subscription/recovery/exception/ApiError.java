package com.subscription.recovery.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Uniform error body for every failed request, so API clients only need to handle one shape.
 *
 * @param fieldErrors field name -> message, only present for validation errors
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {
}
