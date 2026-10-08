package com.subscription.recovery.retry;

import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.risk.RiskAssessment;
import java.time.LocalDateTime;

/**
 * Input to a retry decision: what failed, how many times, for whom, and how much time is left.
 *
 * @param failedAttempts failed attempts so far on this invoice, including the one that just failed
 * @param maxRetries     retry budget for this customer (set by the policy from the risk band)
 * @param deadline       end of the recovery window; nothing may be scheduled after it
 */
public record RetryContext(
        FailureReason reason,
        int failedAttempts,
        int maxRetries,
        RiskAssessment risk,
        LocalDateTime failedAt,
        LocalDateTime deadline) {

    /** Retries already used (the first failed attempt was the scheduled charge, not a retry). */
    public int retriesUsed() {
        return failedAttempts - 1;
    }

    public int retriesRemaining() {
        return Math.max(0, maxRetries - retriesUsed());
    }

    public RetryContext withMaxRetries(int newMax) {
        return new RetryContext(reason, failedAttempts, newMax, risk, failedAt, deadline);
    }
}
