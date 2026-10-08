package com.subscription.recovery.dto;

import com.subscription.recovery.domain.BillingCycle;
import java.math.BigDecimal;

public record PlanResponse(
        Long id,
        String name,
        BigDecimal monthlyPrice,
        BillingCycle billingCycle,
        BigDecimal pricePerCycle,
        boolean active) {
}
