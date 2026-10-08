package com.subscription.recovery.dto;

/** Result of manually triggering the billing or recovery job (useful for demos and ops). */
public record JobRunResponse(String job, int processed, int succeeded, int failed) {
}
