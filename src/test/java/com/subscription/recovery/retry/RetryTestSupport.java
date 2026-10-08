package com.subscription.recovery.retry;

import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.risk.RiskAssessment;
import com.subscription.recovery.risk.RiskBand;
import java.time.LocalDateTime;
import java.util.List;

/** Builders shared by the retry tests. */
final class RetryTestSupport {

    static final RiskAssessment LOW = new RiskAssessment(10, RiskBand.LOW, List.of());
    static final RiskAssessment MEDIUM = new RiskAssessment(50, RiskBand.MEDIUM, List.of());
    static final RiskAssessment HIGH = new RiskAssessment(80, RiskBand.HIGH, List.of());

    private RetryTestSupport() {
    }

    static RetryContext ctx(FailureReason reason, int failedAttempts, RiskAssessment risk, LocalDateTime failedAt) {
        return new RetryContext(reason, failedAttempts, 4, risk, failedAt, failedAt.plusDays(28));
    }

    static SmartRecoveryPolicy smartPolicy() {
        return new SmartRecoveryPolicy(List.of(new BankDowntimeRetryStrategy(), new SalaryCycleRetryStrategy(),
                new LimitExceededRetryStrategy(), new PaymentMethodUpdateStrategy()));
    }
}
