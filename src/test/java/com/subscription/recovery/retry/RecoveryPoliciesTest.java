package com.subscription.recovery.retry;

import static com.subscription.recovery.retry.RetryTestSupport.HIGH;
import static com.subscription.recovery.retry.RetryTestSupport.LOW;
import static com.subscription.recovery.retry.RetryTestSupport.MEDIUM;
import static com.subscription.recovery.retry.RetryTestSupport.ctx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.retry.RetryDecision.Action;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for the policy layer: strategy selection, risk-based adjustments, and the fixed baseline. */
class RecoveryPoliciesTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 3, 14, 9, 0);
    private final SmartRecoveryPolicy smart = RetryTestSupport.smartPolicy();

    @Test
    void smartPolicyRoutesEachReasonToItsStrategy() {
        assertThat(smart.strategyFor(FailureReason.BANK_DOWNTIME)).isInstanceOf(BankDowntimeRetryStrategy.class);
        assertThat(smart.strategyFor(FailureReason.INSUFFICIENT_BALANCE)).isInstanceOf(SalaryCycleRetryStrategy.class);
        assertThat(smart.strategyFor(FailureReason.LIMIT_EXCEEDED)).isInstanceOf(LimitExceededRetryStrategy.class);
        assertThat(smart.strategyFor(FailureReason.MANDATE_REVOKED)).isInstanceOf(PaymentMethodUpdateStrategy.class);
        assertThat(smart.strategyFor(FailureReason.CARD_EXPIRED)).isInstanceOf(PaymentMethodUpdateStrategy.class);
    }

    @Test
    void highRiskCustomersGetFewerRetriesThanLowRisk() {
        // 3rd failed attempt = 2 retries used. HIGH budget is 2 -> give up; LOW budget is 4 -> keep going.
        assertThat(smart.onFailure(ctx(FailureReason.BANK_DOWNTIME, 3, HIGH, T)).action()).isEqualTo(Action.GIVE_UP);
        assertThat(smart.onFailure(ctx(FailureReason.BANK_DOWNTIME, 3, MEDIUM, T)).action()).isEqualTo(Action.RETRY);
        assertThat(smart.onFailure(ctx(FailureReason.BANK_DOWNTIME, 3, LOW, T)).action()).isEqualTo(Action.RETRY);
    }

    @Test
    void highRiskCustomersAreRemindedOnTheFirstSoftFailure() {
        RetryDecision high = smart.onFailure(ctx(FailureReason.INSUFFICIENT_BALANCE, 1, HIGH, T));
        RetryDecision low = smart.onFailure(ctx(FailureReason.INSUFFICIENT_BALANCE, 1, LOW, T));

        assertThat(high.reminderNow()).isEqualTo(ReminderType.LOW_BALANCE_HEADS_UP);
        assertThat(low.reminderNow()).isNull();
        assertThat(high.nextRetryAt()).isEqualTo(low.nextRetryAt());
    }

    @Test
    void highRiskCustomersAreNotRemindedAboutBankDowntime() {
        RetryDecision d = smart.onFailure(ctx(FailureReason.BANK_DOWNTIME, 1, HIGH, T));
        assertThat(d.reminderNow()).isNull();
    }

    @Test
    void smartPolicyFailsFastIfAReasonHasNoStrategy() {
        assertThatThrownBy(() -> new SmartRecoveryPolicy(List.of(new BankDowntimeRetryStrategy())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No retry strategy registered");
    }

    @Test
    void fixedPolicyRetriesEveryThreeDaysRegardlessOfReason() {
        FixedIntervalRecoveryPolicy fixed = new FixedIntervalRecoveryPolicy();
        RetryDecision d = fixed.onFailure(ctx(FailureReason.MANDATE_REVOKED, 1, LOW, T));

        assertThat(d.action()).isEqualTo(Action.RETRY);
        assertThat(d.nextRetryAt()).isEqualTo(T.plusDays(3));
        assertThat(d.reminderNow()).isEqualTo(ReminderType.PAYMENT_FAILED);
        assertThat(fixed.onFailure(ctx(FailureReason.INSUFFICIENT_BALANCE, 5, LOW, T)).action())
                .isEqualTo(Action.GIVE_UP);
    }
}
