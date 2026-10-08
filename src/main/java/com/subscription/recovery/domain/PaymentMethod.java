package com.subscription.recovery.domain;

/** How a subscription is charged. UPI AutoPay (NPCI e-mandate) dominates Indian subscriptions. */
public enum PaymentMethod {
    UPI_AUTOPAY,
    CARD
}
