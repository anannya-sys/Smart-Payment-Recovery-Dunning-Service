package com.subscription.recovery.simulation;

import com.subscription.recovery.retry.FixedIntervalRecoveryPolicy;
import com.subscription.recovery.retry.SmartRecoveryPolicy;
import com.subscription.recovery.risk.RiskScoringService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the smart and the fixed policy on the <b>same</b> synthetic customers with the <b>same</b> seed and
 * reports both. This is an A/B test you can run on a laptop before risking real revenue.
 *
 * <p>Pure in-memory: nothing is written to the database.
 */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);
    static final int RECOVERY_WINDOW_DAYS = 28;

    static final List<String> ASSUMPTIONS = List.of(
            "Gateway: UPI mandates/cards break (hard decline) at 1.2-4.2% per cycle and stay broken until replaced",
            "Gateway: each bank is down in ~2% of 2-hour windows",
            "Gateway: after each salary credit an account stays funded for a stress-dependent number of days, then is "
                    + "dry until the next salary (5% daily chance of other inflow)",
            "Gateway: 0.5-4.5% of accounts per cycle are abandoned (never funded again); they decline as "
                    + "INSUFFICIENT_BALANCE, so no policy can tell them apart from a temporary low balance",
            "Salary days: 55% on the 1st, 30% on the 5th, 15% on the 7th",
            "Customers act on a reminder with p = engagement x relevance x e^(-daysOverdue/12) x 0.6; "
                    + "specific messages have relevance 1.0, generic 'payment failed' 0.5",
            "Each failed retry triggers a bank 'debit failed' SMS; 1-5% of customers (by risk) cancel in response",
            "Recovery window: " + RECOVERY_WINDOW_DAYS + " days; smart retries 2-4 by risk band, fixed retries every 3 days x4");

    private final SmartRecoveryPolicy smart;
    private final FixedIntervalRecoveryPolicy fixed;
    private final RiskScoringService riskScoring;

    public SimulationService(SmartRecoveryPolicy smart, FixedIntervalRecoveryPolicy fixed,
                             RiskScoringService riskScoring) {
        this.smart = smart;
        this.fixed = fixed;
        this.riskScoring = riskScoring;
    }

    public SimulationReport run(SimulationRequest req) {
        int count = req.customersOrDefault();
        int months = req.monthsOrDefault();
        long seed = req.seedOrDefault();
        LocalDate start = req.startOrDefault();

        long t0 = System.nanoTime();
        List<SyntheticCustomer> customers = SyntheticCustomerGenerator.generate(count, seed, start);
        PolicyResult smartResult = new RecoverySimulation(smart, riskScoring, customers, seed, start, months,
                RECOVERY_WINDOW_DAYS).run();
        PolicyResult fixedResult = new RecoverySimulation(fixed, riskScoring, customers, seed, start, months,
                RECOVERY_WINDOW_DAYS).run();
        log.info("Simulation of {} customers x {} months took {} ms", count, months, (System.nanoTime() - t0) / 1_000_000);

        BigDecimal lift = smartResult.recoveryRatePercent().subtract(fixedResult.recoveryRatePercent());
        BigDecimal extra = smartResult.recoveredRevenue().subtract(fixedResult.recoveredRevenue());
        return new SimulationReport(count, months, seed, start, smartResult, fixedResult, lift, extra, ASSUMPTIONS);
    }

    /** Fixed-width text table for logs and the command-line runner. */
    public static String format(SimulationReport r) {
        PolicyResult s = r.smart();
        PolicyResult f = r.fixed();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%nSimulation: %d customers x %d months from %s (seed %d)%n",
                r.customers(), r.months(), r.startDate(), r.seed()));
        sb.append(String.format("%-34s %16s %16s%n", "Metric", "SMART", "FIXED"));
        sb.append("-".repeat(68)).append('\n');
        row(sb, "Invoices issued", s.invoicesIssued(), f.invoicesIssued());
        row(sb, "Invoices with a failed payment", s.failedInvoices(), f.failedInvoices());
        row(sb, "Recovered invoices", s.recoveredInvoices(), f.recoveredInvoices());
        row(sb, "Written-off invoices", s.writtenOffInvoices(), f.writtenOffInvoices());
        row(sb, "Failed revenue (INR)", s.failedRevenue(), f.failedRevenue());
        row(sb, "Recovered revenue (INR)", s.recoveredRevenue(), f.recoveredRevenue());
        row(sb, "Recovery rate (revenue %)", s.recoveryRatePercent(), f.recoveryRatePercent());
        row(sb, "Recovery rate (invoices %)", s.invoiceRecoveryRatePercent(), f.invoiceRecoveryRatePercent());
        row(sb, "Policy retries (gateway calls)", s.policyRetries(), f.policyRetries());
        row(sb, "Customer-initiated payments", s.customerInitiatedPayments(), f.customerInitiatedPayments());
        row(sb, "Reminders sent", s.remindersSent(), f.remindersSent());
        row(sb, "Avg days to recover", s.avgDaysToRecover(), f.avgDaysToRecover());
        row(sb, "Cancellations from retry fatigue", s.retryFatigueCancellations(), f.retryFatigueCancellations());
        row(sb, "Subscriptions cancelled", s.subscriptionsCancelled(), f.subscriptionsCancelled());
        row(sb, "Active subscriptions at end", s.activeSubscriptionsAtEnd(), f.activeSubscriptionsAtEnd());
        row(sb, "Total revenue collected (INR)", s.totalCollectedRevenue(), f.totalCollectedRevenue());
        sb.append("-".repeat(68)).append('\n');
        sb.append(String.format("Recovery-rate lift: %s percentage points; extra revenue recovered: INR %s%n",
                r.recoveryRateLiftPoints(), r.extraRevenueRecovered()));
        sb.append(String.format("%nBy first failure reason (recovered / failed = rate)%n"));
        for (PolicyResult.ReasonResult sr : s.byFailureReason()) {
            PolicyResult.ReasonResult fr = f.byFailureReason().stream()
                    .filter(x -> x.reason() == sr.reason()).findFirst().orElse(null);
            sb.append(String.format("%-22s SMART %4d/%-4d = %6s%%   FIXED %s%n", sr.reason(), sr.recovered(),
                    sr.failed(), sr.recoveryRatePercent(),
                    fr == null ? "-" : String.format("%4d/%-4d = %6s%%", fr.recovered(), fr.failed(),
                            fr.recoveryRatePercent())));
        }
        return sb.toString();
    }

    private static void row(StringBuilder sb, String label, Object smart, Object fixed) {
        sb.append(String.format("%-34s %16s %16s%n", label, smart, fixed));
    }
}
