package com.subscription.recovery.retry;

import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.risk.RiskBand;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The "context" of the Strategy pattern: picks the right {@link RetryStrategy} for the failure reason, and
 * applies the cross-cutting, risk-based rules that apply to every strategy:
 *
 * <ul>
 *   <li><b>Retry budget by risk band</b>: LOW 4, MEDIUM 3, HIGH 2. High-risk customers get fewer retries,
 *       because every failed debit triggers a bank SMS that nudges an already-wavering customer to cancel.</li>
 *   <li><b>Earlier reminder for high-risk customers</b>: on the very first soft failure they get an actionable
 *       message right away, instead of only from the second failure.</li>
 * </ul>
 */
@Component
public class SmartRecoveryPolicy implements RecoveryPolicy {

    static final Map<RiskBand, Integer> MAX_RETRIES = Map.of(RiskBand.LOW, 4, RiskBand.MEDIUM, 3, RiskBand.HIGH, 2);

    private final Map<FailureReason, RetryStrategy> strategies = new EnumMap<>(FailureReason.class);

    /** Spring injects every {@link RetryStrategy} bean; we index them by the reasons they support. */
    public SmartRecoveryPolicy(List<RetryStrategy> allStrategies) {
        for (RetryStrategy s : allStrategies) {
            for (FailureReason r : s.supportedReasons()) {
                RetryStrategy previous = strategies.put(r, s);
                if (previous != null) {
                    throw new IllegalStateException("Two strategies handle " + r + ": "
                            + previous.getClass().getSimpleName() + " and " + s.getClass().getSimpleName());
                }
            }
        }
        // Fail fast at startup if a failure reason has no strategy, instead of at 3am in the retry job.
        for (FailureReason r : FailureReason.values()) {
            if (!strategies.containsKey(r)) {
                throw new IllegalStateException("No retry strategy registered for " + r);
            }
        }
    }

    @Override
    public String name() {
        return "SMART";
    }

    @Override
    public RetryDecision onFailure(RetryContext context) {
        RiskBand band = context.risk().band();
        RetryContext ctx = context.withMaxRetries(MAX_RETRIES.get(band));
        RetryDecision decision = strategies.get(ctx.reason()).decide(ctx);

        boolean customerCanHelp = ctx.reason() == FailureReason.INSUFFICIENT_BALANCE
                || ctx.reason() == FailureReason.LIMIT_EXCEEDED;
        if (band == RiskBand.HIGH && customerCanHelp && decision.action() == RetryDecision.Action.RETRY
                && decision.reminderNow() == null) {
            decision = decision.withReminderNow(ReminderType.LOW_BALANCE_HEADS_UP)
                    .withRationale(decision.rationale() + "; high-risk customer, reminded immediately");
        }
        return decision;
    }

    /** Visible for tests. */
    RetryStrategy strategyFor(FailureReason reason) {
        return strategies.get(reason);
    }
}
