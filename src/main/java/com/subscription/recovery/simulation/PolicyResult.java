package com.subscription.recovery.simulation;

import com.subscription.recovery.domain.FailureReason;
import java.math.BigDecimal;
import java.util.List;

/**
 * Outcome of simulating one recovery policy.
 *
 * @param recoveryRatePercent        recovered revenue / failed revenue (the headline KPI)
 * @param invoiceRecoveryRatePercent recovered invoices / failed invoices
 * @param policyRetries              retries scheduled by the policy (each one costs a gateway call and may
 *                                   trigger a "debit failed" SMS to the customer)
 * @param customerInitiatedPayments  payments the customer made after acting on a reminder
 * @param retryFatigueCancellations  customers who cancelled after repeated failed debits
 * @param totalCollectedRevenue      all revenue collected over the period (first-time + recovered)
 */
public record PolicyResult(
        String policy,
        long invoicesIssued,
        long failedInvoices,
        long recoveredInvoices,
        long writtenOffInvoices,
        BigDecimal failedRevenue,
        BigDecimal recoveredRevenue,
        BigDecimal recoveryRatePercent,
        BigDecimal invoiceRecoveryRatePercent,
        long policyRetries,
        long customerInitiatedPayments,
        long remindersSent,
        double avgDaysToRecover,
        long retryFatigueCancellations,
        long subscriptionsCancelled,
        long activeSubscriptionsAtEnd,
        BigDecimal totalCollectedRevenue,
        List<ReasonResult> byFailureReason) {

    public record ReasonResult(FailureReason reason, long failed, long recovered, BigDecimal recoveryRatePercent) {
    }
}
