package com.subscription.recovery.retry;

/**
 * Decides what happens after a failed payment. Two implementations exist: {@link SmartRecoveryPolicy}
 * (strategy per failure reason + risk-aware) and {@link FixedIntervalRecoveryPolicy} (the classic baseline),
 * so the simulation can compare them on identical inputs.
 */
public interface RecoveryPolicy {

    String name();

    RetryDecision onFailure(RetryContext context);
}
