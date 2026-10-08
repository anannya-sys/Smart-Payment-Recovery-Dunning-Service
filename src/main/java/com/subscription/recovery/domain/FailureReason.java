package com.subscription.recovery.domain;

/**
 * Why a charge was declined. These mirror the most common UPI AutoPay / card decline categories.
 *
 * <p>A <b>hard decline</b> means retrying the same payment instrument cannot succeed: the customer must act
 * (re-authorise the mandate or add a new card). Soft declines are temporary and worth retrying.
 */
public enum FailureReason {
    /** Not enough money in the account right now. Usually fixed after salary credit. */
    INSUFFICIENT_BALANCE(false),
    /** Customer (or bank) cancelled the UPI AutoPay mandate. Retrying is pointless. */
    MANDATE_REVOKED(true),
    /** Daily / per-transaction limit hit. Limits reset, so a retry the next day usually works. */
    LIMIT_EXCEEDED(false),
    /** Issuer bank or NPCI switch was down. Transient, typically resolves within hours. */
    BANK_DOWNTIME(false),
    /** Card on file has expired. Retrying is pointless until the card is updated. */
    CARD_EXPIRED(true);

    private final boolean hardDecline;

    FailureReason(boolean hardDecline) {
        this.hardDecline = hardDecline;
    }

    public boolean isHardDecline() {
        return hardDecline;
    }
}
