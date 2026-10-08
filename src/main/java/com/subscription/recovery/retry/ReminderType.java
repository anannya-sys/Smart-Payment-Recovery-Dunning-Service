package com.subscription.recovery.retry;

/** Kinds of customer messages the recovery engine can send. */
public enum ReminderType {
    /** Generic "your payment failed" notice (what the fixed baseline sends). */
    PAYMENT_FAILED,
    /** Specific, actionable: "your mandate was revoked / card expired, update it here". */
    UPDATE_PAYMENT_METHOD,
    /** "We'll retry on the 1st, please keep INR X in your account". */
    LOW_BALANCE_HEADS_UP,
    /** Confirmation after a successful retry. */
    PAYMENT_RECOVERED,
    /** Sent when we give up and cancel the subscription. */
    SUBSCRIPTION_CANCELLED
}
