package com.subscription.recovery.retry;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * The <b>baseline</b> most billing systems ship with: retry every 3 days, 4 times, whatever the failure reason
 * and whoever the customer, and send a generic "payment failed" notice each time. Used for comparison in the
 * simulation (and selectable with {@code recovery.policy=FIXED}).
 */
@Component
public class FixedIntervalRecoveryPolicy implements RecoveryPolicy {

    static final int INTERVAL_DAYS = 3;
    static final int MAX_RETRIES = 4;

    @Override
    public String name() {
        return "FIXED";
    }

    @Override
    public RetryDecision onFailure(RetryContext ctx) {
        RetryContext fixed = ctx.withMaxRetries(MAX_RETRIES);
        if (fixed.retriesRemaining() == 0) {
            return RetryDecision.giveUp("Fixed policy: " + MAX_RETRIES + " retries exhausted");
        }
        LocalDateTime next = ctx.failedAt().plusDays(INTERVAL_DAYS);
        if (!RetryTimes.fitsBefore(next, ctx.deadline())) {
            return RetryDecision.giveUp("Fixed policy: next retry after deadline");
        }
        return RetryDecision.retryAt(next, "Fixed policy: retry every " + INTERVAL_DAYS + " days")
                .withReminderNow(ReminderType.PAYMENT_FAILED);
    }
}
