package com.subscription.recovery.dto;

import com.subscription.recovery.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

/** Body of PUT /api/subscriptions/{id}/payment-method (customer re-authorised mandate / added a card). */
public record PaymentMethodUpdateRequest(@NotNull PaymentMethod paymentMethod) {
}
