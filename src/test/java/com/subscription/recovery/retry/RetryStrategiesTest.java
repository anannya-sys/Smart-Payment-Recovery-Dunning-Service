package com.subscription.recovery.retry;

import static com.subscription.recovery.retry.RetryTestSupport.HIGH;
import static com.subscription.recovery.retry.RetryTestSupport.LOW;
import static com.subscription.recovery.retry.RetryTestSupport.ctx;
import static org.assertj.core.api.Assertions.assertThat;

import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.retry.RetryDecision.Action;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Unit tests for each retry strategy in isolation: pure functions, no Spring, no database. */
class RetryStrategiesTest {

    private static final LocalDateTime MID_MONTH = LocalDateTime.of(2026, 3, 14, 9, 0);

    @Nested
    @DisplayName("BANK_DOWNTIME")
    class BankDowntime {

        private final BankDowntimeRetryStrategy strategy = new BankDowntimeRetryStrategy();

        @Test
        void retriesWithinHoursUsingExponentialBackoff() {
            RetryDecision first = strategy.decide(ctx(FailureReason.BANK_DOWNTIME, 1, LOW, MID_MONTH));
            RetryDecision second = strategy.decide(ctx(FailureReason.BANK_DOWNTIME, 2, LOW, MID_MONTH));
            RetryDecision third = strategy.decide(ctx(FailureReason.BANK_DOWNTIME, 3, LOW, MID_MONTH));

            assertThat(first.nextRetryAt()).isEqualTo(MID_MONTH.plusHours(2));
            assertThat(second.nextRetryAt()).isEqualTo(MID_MONTH.plusHours(4));
            assertThat(third.nextRetryAt()).isEqualTo(MID_MONTH.plusHours(8));
            assertThat(first.reminderNow()).as("not the customer's fault, don't bother them").isNull();
        }

        @Test
        void givesUpWhenRetryBudgetIsUsed() {
            // 5 failed attempts = 1 scheduled charge + 4 retries = budget of 4 used up.
            RetryDecision d = strategy.decide(ctx(FailureReason.BANK_DOWNTIME, 5, LOW, MID_MONTH));
            assertThat(d.action()).isEqualTo(Action.GIVE_UP);
        }
    }

    @Nested
    @DisplayName("INSUFFICIENT_BALANCE")
    class InsufficientBalance {

        private final SalaryCycleRetryStrategy strategy = new SalaryCycleRetryStrategy();

        @Test
        void midMonthFailureRetriesOnTheFirstOfNextMonthAt10am() {
            RetryDecision d = strategy.decide(ctx(FailureReason.INSUFFICIENT_BALANCE, 1, LOW, MID_MONTH));
            assertThat(d.action()).isEqualTo(Action.RETRY);
            assertThat(d.nextRetryAt()).isEqualTo(LocalDateTime.of(2026, 4, 1, 10, 0));
            assertThat(d.followUpAt()).as("no reminder after the first failure").isNull();
        }

        @Test
        void failureOnTheSecondRetriesOnTheFifth() {
            LocalDateTime second = LocalDateTime.of(2026, 3, 2, 9, 0);
            RetryDecision d = strategy.decide(ctx(FailureReason.INSUFFICIENT_BALANCE, 1, LOW, second));
            assertThat(d.nextRetryAt()).isEqualTo(LocalDateTime.of(2026, 3, 5, 10, 0));
        }

        @Test
        void secondFailureSchedulesAHeadsUpTheDayBeforeTheRetry() {
            LocalDateTime firstOfMonth = LocalDateTime.of(2026, 4, 1, 10, 0);
            RetryDecision d = strategy.decide(ctx(FailureReason.INSUFFICIENT_BALANCE, 2, LOW, firstOfMonth));
            assertThat(d.nextRetryAt()).isEqualTo(LocalDateTime.of(2026, 4, 5, 10, 0));
            assertThat(d.followUpAt()).isEqualTo(LocalDateTime.of(2026, 4, 4, 10, 0));
        }

        @Test
        void fallsBackToShortRetryWhenNoSalaryDateFitsBeforeDeadline() {
            RetryContext tight = new RetryContext(FailureReason.INSUFFICIENT_BALANCE, 1, 4, LOW, MID_MONTH,
                    MID_MONTH.plusDays(7));
            RetryDecision d = strategy.decide(tight);
            assertThat(d.nextRetryAt()).isEqualTo(LocalDateTime.of(2026, 3, 17, 10, 0));
        }
    }

    @Test
    void limitExceededRetriesNextMorningWhenLimitsHaveReset() {
        RetryDecision d = new LimitExceededRetryStrategy()
                .decide(ctx(FailureReason.LIMIT_EXCEEDED, 1, LOW, MID_MONTH));
        assertThat(d.nextRetryAt()).isEqualTo(LocalDateTime.of(2026, 3, 15, 10, 0));
    }

    @ParameterizedTest
    @EnumSource(value = FailureReason.class, names = {"MANDATE_REVOKED", "CARD_EXPIRED"})
    void hardDeclinesNeverRetryBlindlyAndAskForANewPaymentMethod(FailureReason reason) {
        RetryDecision d = new PaymentMethodUpdateStrategy().decide(ctx(reason, 1, LOW, MID_MONTH));

        assertThat(d.action()).isEqualTo(Action.AWAIT_CUSTOMER);
        assertThat(d.nextRetryAt()).isNull();
        assertThat(d.reminderNow()).isEqualTo(ReminderType.UPDATE_PAYMENT_METHOD);
        assertThat(d.followUpAt()).isEqualTo(MID_MONTH.plusDays(4));
    }

    @Test
    void hardDeclineFollowUpComesSoonerForHighRiskCustomers() {
        RetryDecision d = new PaymentMethodUpdateStrategy()
                .decide(ctx(FailureReason.MANDATE_REVOKED, 1, HIGH, MID_MONTH));
        assertThat(d.followUpAt()).isEqualTo(MID_MONTH.plusDays(2));
    }
}
