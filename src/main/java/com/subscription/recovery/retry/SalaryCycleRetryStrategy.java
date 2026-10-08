package com.subscription.recovery.retry;

import com.subscription.recovery.domain.FailureReason;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * INSUFFICIENT_BALANCE: retrying tomorrow rarely helps, because the balance only changes when salary arrives.
 * Most salaried Indians are paid on the 1st, or by the 5th-7th. So retry on the next 1st or 5th of the month
 * (10:00 IST). From the second failure on, also send a heads-up the day before the retry asking the customer to
 * keep the amount available.
 *
 * <p>If no salary date fits before the deadline, fall back to one retry 3 days later.
 */
@Component
public class SalaryCycleRetryStrategy implements RetryStrategy {

    static final int[] SALARY_DAYS = {1, 5};
    static final int FALLBACK_DELAY_DAYS = 3;

    @Override
    public Set<FailureReason> supportedReasons() {
        return Set.of(FailureReason.INSUFFICIENT_BALANCE);
    }

    @Override
    public RetryDecision decide(RetryContext ctx) {
        if (ctx.retriesRemaining() == 0) {
            return RetryDecision.giveUp("Insufficient balance persisted through " + ctx.retriesUsed()
                    + " salary-date retries");
        }
        LocalDateTime salaryRetry = nextSalaryDate(ctx.failedAt());
        RetryDecision decision;
        if (RetryTimes.fitsBefore(salaryRetry, ctx.deadline())) {
            decision = RetryDecision.retryAt(salaryRetry, "Insufficient balance; retrying on salary date "
                    + salaryRetry.toLocalDate() + " when the account is most likely funded");
        } else {
            LocalDateTime fallback = RetryTimes.morningOf(ctx.failedAt().toLocalDate().plusDays(FALLBACK_DELAY_DAYS));
            if (!RetryTimes.fitsBefore(fallback, ctx.deadline())) {
                return RetryDecision.giveUp("No retry slot left before the recovery deadline");
            }
            decision = RetryDecision.retryAt(fallback,
                    "Insufficient balance; no salary date before the deadline, retrying in " + FALLBACK_DELAY_DAYS
                            + " days");
        }
        // From the 2nd failure, remind the customer the day before we try again.
        if (ctx.failedAttempts() >= 2) {
            LocalDateTime dayBefore = decision.nextRetryAt().minusDays(1);
            if (dayBefore.isAfter(ctx.failedAt())) {
                decision = decision.withFollowUp(dayBefore);
            }
        }
        return decision;
    }

    /**
     * First 1st-or-5th of a month at 10:00 that is at least 12 hours away (so we never retry twice the same day).
     */
    static LocalDateTime nextSalaryDate(LocalDateTime from) {
        LocalDateTime earliest = from.plusHours(12);
        LocalDate month = from.toLocalDate().withDayOfMonth(1);
        for (int m = 0; m < 3; m++) {
            for (int day : SALARY_DAYS) {
                LocalDateTime candidate = RetryTimes.morningOf(month.plusMonths(m).withDayOfMonth(day));
                if (!candidate.isBefore(earliest)) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("unreachable: a salary date always exists within 3 months");
    }
}
