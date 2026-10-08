package com.subscription.recovery.dto;

import com.subscription.recovery.domain.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Body of PATCH /api/subscriptions/{id}/status. Clients may pause, resume (ACTIVE) or cancel.
 * PAST_DUE is set only by the billing engine.
 */
public record StatusChangeRequest(@NotNull @Schema(example = "PAUSED") SubscriptionStatus status) {
}
