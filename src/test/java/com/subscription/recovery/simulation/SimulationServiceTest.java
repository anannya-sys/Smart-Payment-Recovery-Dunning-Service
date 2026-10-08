package com.subscription.recovery.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.retry.BankDowntimeRetryStrategy;
import com.subscription.recovery.retry.FixedIntervalRecoveryPolicy;
import com.subscription.recovery.retry.LimitExceededRetryStrategy;
import com.subscription.recovery.retry.PaymentMethodUpdateStrategy;
import com.subscription.recovery.retry.ReminderType;
import com.subscription.recovery.retry.SalaryCycleRetryStrategy;
import com.subscription.recovery.retry.SmartRecoveryPolicy;
import com.subscription.recovery.risk.RiskScoringService;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationServiceTest {

    private final SimulationService service = new SimulationService(
            new SmartRecoveryPolicy(List.of(new BankDowntimeRetryStrategy(), new SalaryCycleRetryStrategy(),
                    new LimitExceededRetryStrategy(), new PaymentMethodUpdateStrategy())),
            new FixedIntervalRecoveryPolicy(), new RiskScoringService());

    @Test
    void sameSeedProducesIdenticalReports() {
        SimulationRequest req = new SimulationRequest(300, 3, 99L, null);
        assertThat(service.run(req)).isEqualTo(service.run(req));
    }

    @Test
    void smartPolicyRecoversMoreRevenueWithFewerRetriesOnTheDefaultScenario() {
        SimulationReport r = service.run(new SimulationRequest(null, null, null, null)); // 1000 x 6, seed 42

        assertThat(r.smart().recoveryRatePercent()).isGreaterThan(r.fixed().recoveryRatePercent());
        assertThat(r.smart().policyRetries()).isLessThan(r.fixed().policyRetries());
        assertThat(r.smart().activeSubscriptionsAtEnd()).isGreaterThan(r.fixed().activeSubscriptionsAtEnd());
    }

    @Test
    void hardDeclinesRecoverBetterWithSpecificRemindersThanWithBlindRetries() {
        SimulationReport r = service.run(new SimulationRequest(2000, 6, 7L, null));
        assertThat(rate(r.smart(), FailureReason.MANDATE_REVOKED))
                .isGreaterThan(rate(r.fixed(), FailureReason.MANDATE_REVOKED));
    }

    @Test
    void specificRemindersAreMoreRelevantThanGenericOnes() {
        assertThat(RecoverySimulation.relevance(ReminderType.UPDATE_PAYMENT_METHOD, FailureReason.CARD_EXPIRED))
                .isGreaterThan(RecoverySimulation.relevance(ReminderType.PAYMENT_FAILED, FailureReason.CARD_EXPIRED));
    }

    private static double rate(PolicyResult p, FailureReason reason) {
        return p.byFailureReason().stream().filter(x -> x.reason() == reason).findFirst()
                .orElseThrow().recoveryRatePercent().doubleValue();
    }
}
