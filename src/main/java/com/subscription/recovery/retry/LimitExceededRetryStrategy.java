package com.subscription.recovery.retry;

import com.subscription.recovery.domain.FailureReason;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * LIMIT_EXCEEDED: the customer's daily UPI/card limit was already used. Limits reset at midnight, so retry the
 * next morning, then every 2 days.
 */
@Component
public class LimitExceededRetryStrategy implements RetryStrategy {

    @Override
    public Set<FailureReason> supportedReasons() {
        return Set.of(FailureReason.LIMIT_EXCEEDED);
    }

    @Override
    public RetryDecision decide(RetryContext ctx) {
        if (ctx.retriesRemaining() == 0) {
            return RetryDecision.giveUp("Limit still exceeded after " + ctx.retriesUsed() + " retries");
        }
        int days = ctx.retriesUsed() == 0 ? 1 : 2;
        LocalDateTime next = RetryTimes.morningOf(ctx.failedAt().toLocalDate().plusDays(days));
        if (!RetryTimes.fitsBefore(next, ctx.deadline())) {
            return RetryDecision.giveUp("Next limit retry would fall after the recovery deadline");
        }
        return RetryDecision.retryAt(next, "Daily limit exceeded; limits reset overnight, retrying " + next);
    }
}
