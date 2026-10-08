package com.subscription.recovery.dto;

import com.subscription.recovery.domain.PaymentMethod;
import com.subscription.recovery.domain.SubscriptionStatus;
import java.time.LocalDate;

public record SubscriptionResponse(
        Long id,
        Long customerId,
        String customerName,
        Long planId,
        String planName,
        SubscriptionStatus status,
        LocalDate nextBillingDate,
        PaymentMethod paymentMethod,
        LocalDate startDate) {
}
