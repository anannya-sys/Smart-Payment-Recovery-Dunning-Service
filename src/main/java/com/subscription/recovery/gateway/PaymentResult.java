package com.subscription.recovery.gateway;

import com.subscription.recovery.domain.FailureReason;

/** Gateway response. {@code failureReason} is null on success. */
public record PaymentResult(boolean success, FailureReason failureReason, String reference) {

    public static PaymentResult success(String reference) {
        return new PaymentResult(true, null, reference);
    }

    public static PaymentResult failure(FailureReason reason, String reference) {
        return new PaymentResult(false, reason, reference);
    }
}
