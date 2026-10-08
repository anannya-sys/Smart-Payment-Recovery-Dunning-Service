package com.subscription.recovery.dto;

import com.subscription.recovery.domain.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/** Body of POST /api/subscriptions. */
public record SubscriptionRequest(
        @NotNull @Positive Long customerId,
        @NotNull @Positive Long planId,
        @NotNull @Schema(example = "UPI_AUTOPAY") PaymentMethod paymentMethod,
        @FutureOrPresent @Schema(description = "First billing date; defaults to today") LocalDate startDate) {
}
