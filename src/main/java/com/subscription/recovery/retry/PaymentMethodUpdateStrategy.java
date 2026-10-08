package com.subscription.recovery.retry;

import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.risk.RiskBand;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * MANDATE_REVOKED / CARD_EXPIRED are hard declines: retrying the same instrument can never succeed and only
 * annoys the customer with failed-debit SMSes. So: <b>no blind retries</b>. Send an "update your payment
 * method" message now and one follow-up, then wait. When the customer updates the mandate/card, the API
 * schedules an immediate retry on the new instrument. If they never do, the invoice is written off at the
 * deadline.
 *
 * <p>High-risk customers get the follow-up sooner (after 2 days instead of 4) because they churn faster.
 */
@Component
public class PaymentMethodUpdateStrategy implements RetryStrategy {

    static final int FOLLOW_UP_DAYS = 4;
    static final int FOLLOW_UP_DAYS_HIGH_RISK = 2;

    @Override
    public Set<FailureReason> supportedReasons() {
        return Set.of(FailureReason.MANDATE_REVOKED, FailureReason.CARD_EXPIRED);
    }

    @Override
    public RetryDecision decide(RetryContext ctx) {
        int followUpDays = ctx.risk().band() == RiskBand.HIGH ? FOLLOW_UP_DAYS_HIGH_RISK : FOLLOW_UP_DAYS;
        var followUp = ctx.failedAt().plusDays(followUpDays);
        String what = ctx.reason() == FailureReason.CARD_EXPIRED ? "Card expired" : "UPI mandate revoked";
        return RetryDecision.awaitCustomer(ReminderType.UPDATE_PAYMENT_METHOD,
                RetryTimes.fitsBefore(followUp, ctx.deadline()) ? followUp : null,
                what + "; retries cannot succeed, asked customer to update payment method (follow-up in "
                        + followUpDays + " days)");
    }
}
