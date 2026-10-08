package com.subscription.recovery.domain;

/** How often a plan is billed. The invoice amount is the monthly price times {@link #months()}. */
public enum BillingCycle {
    MONTHLY(1),
    QUARTERLY(3),
    YEARLY(12);

    private final int months;

    BillingCycle(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }
}
