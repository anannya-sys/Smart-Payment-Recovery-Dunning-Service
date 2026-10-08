package com.subscription.recovery.service;

import com.subscription.recovery.domain.InvoiceStatus;
import com.subscription.recovery.domain.SubscriptionStatus;
import com.subscription.recovery.dto.AnalyticsResponse;
import com.subscription.recovery.dto.AnalyticsResponse.ReasonBreakdown;
import com.subscription.recovery.dto.OverviewResponse;
import com.subscription.recovery.repository.CustomerRepository;
import com.subscription.recovery.repository.FailureReasonAggregate;
import com.subscription.recovery.repository.InvoiceRepository;
import com.subscription.recovery.repository.PlanRepository;
import com.subscription.recovery.repository.SubscriptionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recovery KPIs. The heavy lifting (GROUP BY reason, SUMs) happens in PostgreSQL; Java only adds up the handful
 * of per-reason rows and computes percentages.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final InvoiceRepository invoices;
    private final SubscriptionRepository subscriptions;
    private final CustomerRepository customers;
    private final PlanRepository plans;

    public AnalyticsService(InvoiceRepository invoices, SubscriptionRepository subscriptions,
                            CustomerRepository customers, PlanRepository plans) {
        this.invoices = invoices;
        this.subscriptions = subscriptions;
        this.customers = customers;
        this.plans = plans;
    }

    /** Headline numbers for the dashboard. A handful of COUNT/SUM queries, each answered by the database. */
    public OverviewResponse overview() {
        Map<SubscriptionStatus, Long> byStatus = new EnumMap<>(SubscriptionStatus.class);
        for (SubscriptionStatus status : SubscriptionStatus.values()) {
            byStatus.put(status, subscriptions.countByStatus(status));
        }
        BigDecimal mrr = subscriptions.sumMonthlyPriceByStatusIn(
                EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE));
        return new OverviewResponse(customers.count(), plans.count(), byStatus, mrr,
                invoices.countByStatus(InvoiceStatus.FAILED), invoices.sumAmountByStatus(InvoiceStatus.FAILED),
                invoices.countByStatus(InvoiceStatus.PAID), invoices.countByStatus(InvoiceStatus.RECOVERED),
                invoices.countByStatus(InvoiceStatus.WRITTEN_OFF), invoices.sumAmountByStatus(InvoiceStatus.RECOVERED));
    }

    public AnalyticsResponse recovery(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        List<FailureReasonAggregate> rows = invoices.aggregateFailuresByReason(from, to);

        long failed = 0;
        long recovered = 0;
        long inRecovery = 0;
        BigDecimal failedAmount = BigDecimal.ZERO;
        BigDecimal recoveredAmount = BigDecimal.ZERO;
        List<ReasonBreakdown> breakdown = rows.stream().map(r -> new ReasonBreakdown(r.reason(), r.failedCount(),
                r.recoveredCount(), r.failedAmount(), r.recoveredAmount(),
                percent(r.recoveredAmount(), r.failedAmount()))).toList();
        for (FailureReasonAggregate r : rows) {
            failed += r.failedCount();
            recovered += r.recoveredCount();
            inRecovery += r.inRecoveryCount();
            failedAmount = failedAmount.add(r.failedAmount());
            recoveredAmount = recoveredAmount.add(r.recoveredAmount());
        }
        return new AnalyticsResponse(from, to, failed, recovered, inRecovery, failedAmount, recoveredAmount,
                percent(recoveredAmount, failedAmount), breakdown);
    }

    static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }
}
