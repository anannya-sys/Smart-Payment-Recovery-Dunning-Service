package com.subscription.recovery.simulation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/** Body of POST /api/simulations. All fields optional. */
public record SimulationRequest(
        @Min(10) @Max(20000) @Schema(example = "1000") Integer customers,
        @Min(1) @Max(24) @Schema(example = "6") Integer months,
        @Schema(example = "42") Long seed,
        @Schema(example = "2026-01-01") LocalDate startDate) {

    public static final int DEFAULT_CUSTOMERS = 1000;
    public static final int DEFAULT_MONTHS = 6;
    public static final long DEFAULT_SEED = 42L;
    public static final LocalDate DEFAULT_START = LocalDate.of(2026, 1, 1);

    public int customersOrDefault() {
        return customers == null ? DEFAULT_CUSTOMERS : customers;
    }

    public int monthsOrDefault() {
        return months == null ? DEFAULT_MONTHS : months;
    }

    public long seedOrDefault() {
        return seed == null ? DEFAULT_SEED : seed;
    }

    public LocalDate startOrDefault() {
        return startDate == null ? DEFAULT_START : startDate;
    }
}
