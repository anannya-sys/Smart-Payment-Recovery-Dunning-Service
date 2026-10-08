package com.subscription.recovery.retry;

import com.subscription.recovery.domain.FailureReason;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * BANK_DOWNTIME: the bank or NPCI switch was down. Not the customer's fault and usually fixed within hours,
 * so retry quickly with exponential backoff (2h, 4h, 8h, ...) and don't bother the customer.
 */
@Component
public class BankDowntimeRetryStrategy implements RetryStrategy {

    static final int BASE_DELAY_HOURS = 2;

    @Override
    public Set<FailureReason> supportedReasons() {
        return Set.of(FailureReason.BANK_DOWNTIME);
    }

    @Override
    public RetryDecision decide(RetryContext ctx) {
        if (ctx.retriesRemaining() == 0) {
            return RetryDecision.giveUp("Bank still down after " + ctx.retriesUsed() + " retries; retry budget used up");
        }
        long delayHours = (long) BASE_DELAY_HOURS << ctx.retriesUsed(); // 2, 4, 8, ...
        LocalDateTime next = ctx.failedAt().plusHours(delayHours);
        if (!RetryTimes.fitsBefore(next, ctx.deadline())) {
            return RetryDecision.giveUp("Next downtime retry would fall after the recovery deadline");
        }
        return RetryDecision.retryAt(next,
                "Bank downtime is transient; retrying in " + delayHours + "h (exponential backoff)");
    }
}
