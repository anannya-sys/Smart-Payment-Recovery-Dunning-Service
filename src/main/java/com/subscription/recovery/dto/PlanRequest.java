package com.subscription.recovery.dto;

import com.subscription.recovery.domain.BillingCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body of POST/PUT /api/plans. */
public record PlanRequest(
        @NotBlank @Size(max = 80) @Schema(example = "Premium") String name,
        @NotNull @DecimalMin("1.00") @Digits(integer = 10, fraction = 2) @Schema(example = "499.00")
        BigDecimal monthlyPrice,
        @NotNull @Schema(example = "MONTHLY") BillingCycle billingCycle,
        @Schema(description = "Defaults to true") Boolean active) {
}
