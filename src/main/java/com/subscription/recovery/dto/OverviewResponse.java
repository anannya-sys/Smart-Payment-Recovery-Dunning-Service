package com.subscription.recovery.dto;

import com.subscription.recovery.domain.SubscriptionStatus;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Current snapshot of the business for the dashboard's headline cards.
 *
 * @param monthlyRecurringRevenue monthly price of all ACTIVE and PAST_DUE subscriptions
 * @param revenueAtRisk           total of FAILED invoices that recovery is still working on
 */
public record OverviewResponse(
        long customers,
        long plans,
        Map<SubscriptionStatus, Long> subscriptionsByStatus,
        BigDecimal monthlyRecurringRevenue,
        long invoicesInRecovery,
        BigDecimal revenueAtRisk,
        long invoicesPaid,
        long invoicesRecovered,
        long invoicesWrittenOff,
        BigDecimal recoveredRevenueAllTime) {
}
