package com.subscription.recovery.retry;

import com.subscription.recovery.domain.FailureReason;
import java.util.Set;

/**
 * <b>Strategy pattern.</b> Each implementation knows how to recover from one family of failure reasons.
 *
 * <p>Why a strategy per reason instead of one big {@code switch}? Each rule set can be read, unit-tested and
 * changed on its own, and adding a new failure reason means adding a class, not editing a growing method
 * (Open/Closed principle). Spring discovers all implementations automatically.
 */
public interface RetryStrategy {

    /** Failure reasons this strategy handles. Each reason must be handled by exactly one strategy. */
    Set<FailureReason> supportedReasons();

    RetryDecision decide(RetryContext context);
}
