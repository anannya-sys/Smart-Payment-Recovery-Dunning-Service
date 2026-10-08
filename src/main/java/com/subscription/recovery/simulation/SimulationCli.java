package com.subscription.recovery.simulation;

import com.subscription.recovery.retry.BankDowntimeRetryStrategy;
import com.subscription.recovery.retry.FixedIntervalRecoveryPolicy;
import com.subscription.recovery.retry.LimitExceededRetryStrategy;
import com.subscription.recovery.retry.PaymentMethodUpdateStrategy;
import com.subscription.recovery.retry.SalaryCycleRetryStrategy;
import com.subscription.recovery.retry.SmartRecoveryPolicy;
import com.subscription.recovery.risk.RiskScoringService;
import java.time.LocalDate;
import java.util.List;

/**
 * Command-line entry point that runs the simulation <b>without</b> Spring or a database:
 *
 * <pre>mvn -q compile exec:java                          # 1000 customers, 6 months, seed 42
 * mvn -q compile exec:java -Dexec.args="5000 12 7"     # customers months seed</pre>
 *
 * The objects are wired by hand here, which also shows the production classes have no hidden framework coupling.
 */
public final class SimulationCli {

    private SimulationCli() {
    }

    public static void main(String[] args) {
        Integer customers = args.length > 0 ? Integer.valueOf(args[0]) : null;
        Integer months = args.length > 1 ? Integer.valueOf(args[1]) : null;
        Long seed = args.length > 2 ? Long.valueOf(args[2]) : null;
        LocalDate start = args.length > 3 ? LocalDate.parse(args[3]) : null;

        SmartRecoveryPolicy smart = new SmartRecoveryPolicy(List.of(new BankDowntimeRetryStrategy(),
                new SalaryCycleRetryStrategy(), new LimitExceededRetryStrategy(), new PaymentMethodUpdateStrategy()));
        SimulationService service = new SimulationService(smart, new FixedIntervalRecoveryPolicy(),
                new RiskScoringService());

        SimulationReport report = service.run(new SimulationRequest(customers, months, seed, start));
        System.out.println(SimulationService.format(report));
    }
}
