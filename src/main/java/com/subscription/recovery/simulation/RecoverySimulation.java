package com.subscription.recovery.simulation;

import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.domain.InvoiceStatus;
import com.subscription.recovery.domain.SubscriptionStatus;
import com.subscription.recovery.gateway.ChargeRequest;
import com.subscription.recovery.gateway.DeterministicRandom;
import com.subscription.recovery.gateway.PaymentResult;
import com.subscription.recovery.gateway.SimulatedPaymentGateway;
import com.subscription.recovery.retry.RecoveryPolicy;
import com.subscription.recovery.retry.ReminderType;
import com.subscription.recovery.retry.RetryContext;
import com.subscription.recovery.retry.RetryDecision;
import com.subscription.recovery.risk.RiskAssessment;
import com.subscription.recovery.risk.RiskScoringService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * One run of a <b>discrete-event simulation</b>: a priority queue of timestamped events (bill, retry, reminder,
 * customer action, deadline) processed in time order. Jumping from event to event instead of ticking every hour
 * makes six months of billing for 1,000 customers take well under a second.
 *
 * <p>It reuses the production pieces: the same {@link RecoveryPolicy} (strategies), {@link RiskScoringService}
 * and {@link SimulatedPaymentGateway}. Only persistence is replaced by plain objects. A fresh gateway is created
 * per run so both policies face an identical, reproducible world.
 */
class RecoverySimulation {

    // Salts keep the random streams for different decisions independent.
    private static final long SALT_BILL_MINUTE = 11;
    private static final long SALT_ACT = 12;
    private static final long SALT_ACT_DELAY = 13;
    private static final long SALT_FATIGUE = 14;

    private enum EventType { BILL, RETRY, FOLLOW_UP, CUSTOMER_ACTION, DEADLINE }

    private record Event(LocalDateTime at, long seq, EventType type, SimSubscription sub, SimInvoice invoice,
                         int token) {
    }

    /** In-memory stand-in for the Subscription entity. */
    private static final class SimSubscription {
        final SyntheticCustomer customer;
        SubscriptionStatus status = SubscriptionStatus.ACTIVE;
        LocalDate nextBillingDate;
        int instrumentVersion = 1;
        int pastFailures;
        boolean billScheduled;

        SimSubscription(SyntheticCustomer customer, LocalDate firstBill) {
            this.customer = customer;
            this.nextBillingDate = firstBill;
            this.pastFailures = customer.pastFailures();
        }
    }

    /** In-memory stand-in for the Invoice entity. */
    private static final class SimInvoice {
        final long id;
        final SimSubscription sub;
        final BigDecimal amount;
        final LocalDate periodStart;
        InvoiceStatus status = InvoiceStatus.PENDING;
        int attempts;
        FailureReason firstReason;
        FailureReason lastReason;
        LocalDateTime firstFailedAt;
        LocalDateTime deadline;
        int token;          // bumped whenever the schedule changes, so stale events are ignored
        int remindersSent;
        boolean customerActed;

        SimInvoice(long id, SimSubscription sub, BigDecimal amount, LocalDate periodStart) {
            this.id = id;
            this.sub = sub;
            this.amount = amount;
            this.periodStart = periodStart;
        }
    }

    private final RecoveryPolicy policy;
    private final RiskScoringService riskScoring;
    private final List<SyntheticCustomer> customers;
    private final long seed;
    private final LocalDate start;
    private final LocalDate end;
    private final int recoveryWindowDays;
    private final SimulatedPaymentGateway gateway;

    private final PriorityQueue<Event> queue =
            new PriorityQueue<>(Comparator.comparing(Event::at).thenComparingLong(Event::seq));
    private final List<SimSubscription> subscriptions = new ArrayList<>();
    private final List<SimInvoice> invoices = new ArrayList<>();
    private long seq;

    // Counters
    private long policyRetries;
    private long customerPayments;
    private long reminders;
    private long fatigueCancellations;
    private double totalDaysToRecover;
    private BigDecimal collected = BigDecimal.ZERO;

    RecoverySimulation(RecoveryPolicy policy, RiskScoringService riskScoring, List<SyntheticCustomer> customers,
                       long seed, LocalDate start, int months, int recoveryWindowDays) {
        this.policy = policy;
        this.riskScoring = riskScoring;
        this.customers = customers;
        this.seed = seed;
        this.start = start;
        this.end = start.plusMonths(months);
        this.recoveryWindowDays = recoveryWindowDays;
        this.gateway = new SimulatedPaymentGateway(seed, id -> {
            throw new IllegalStateException("unknown simulated customer " + id);
        });
    }

    PolicyResult run() {
        for (SyntheticCustomer c : customers) {
            gateway.registerProfile(c.id(), c.profile());
            LocalDate firstBill = start.withDayOfMonth(c.billingDay());
            if (firstBill.isBefore(start)) {
                firstBill = firstBill.plusMonths(1);
            }
            SimSubscription sub = new SimSubscription(c, firstBill);
            subscriptions.add(sub);
            scheduleBill(sub, null);
        }
        while (!queue.isEmpty()) {
            Event e = queue.poll();
            switch (e.type()) {
                case BILL -> bill(e.sub(), e.at());
                case RETRY -> {
                    if (isCurrent(e)) {
                        policyRetries++;
                        attempt(e.invoice(), e.at(), false);
                    }
                }
                case FOLLOW_UP -> {
                    if (isCurrent(e)) {
                        ReminderType type = e.invoice().lastReason.isHardDecline()
                                ? ReminderType.UPDATE_PAYMENT_METHOD : ReminderType.LOW_BALANCE_HEADS_UP;
                        remind(e.invoice(), type, e.at());
                    }
                }
                case CUSTOMER_ACTION -> customerActs(e.invoice(), e.at());
                case DEADLINE -> {
                    if (e.invoice().status == InvoiceStatus.FAILED) {
                        writeOff(e.invoice());
                    }
                }
            }
        }
        return summarise();
    }

    // ---- event handlers -----------------------------------------------------------------------------------

    private void bill(SimSubscription sub, LocalDateTime at) {
        sub.billScheduled = false;
        if (sub.status != SubscriptionStatus.ACTIVE) {
            return; // PAST_DUE: billing resumes once the open invoice is recovered. CANCELLED: never again.
        }
        SimInvoice inv = new SimInvoice(invoices.size() + 1L, sub, sub.customer.monthlyPrice(), sub.nextBillingDate);
        invoices.add(inv);
        sub.nextBillingDate = sub.nextBillingDate.plusMonths(1);
        attempt(inv, at, false);
        scheduleBill(sub, null);
    }

    private void attempt(SimInvoice inv, LocalDateTime at, boolean customerInitiated) {
        SimSubscription sub = inv.sub;
        inv.attempts++;
        PaymentResult result = gateway.charge(new ChargeRequest(sub.customer.id(), inv.id, inv.amount,
                sub.customer.method(), sub.instrumentVersion, inv.attempts, at));
        if (result.success()) {
            if (inv.status == InvoiceStatus.FAILED) {
                inv.status = InvoiceStatus.RECOVERED;
                totalDaysToRecover += Duration.between(inv.firstFailedAt, at).toMinutes() / 1440.0;
                sub.status = SubscriptionStatus.ACTIVE;
                scheduleBill(sub, at.plusHours(1)); // catch up on a period skipped while past due
            } else {
                inv.status = InvoiceStatus.PAID;
            }
            collected = collected.add(inv.amount);
            inv.token++;
            return;
        }

        boolean firstFailure = inv.status == InvoiceStatus.PENDING;
        inv.lastReason = result.failureReason();
        inv.status = InvoiceStatus.FAILED;
        if (firstFailure) {
            inv.firstReason = result.failureReason();
            inv.firstFailedAt = at;
            inv.deadline = at.plusDays(recoveryWindowDays);
            sub.pastFailures++;
            sub.status = SubscriptionStatus.PAST_DUE;
            push(inv.deadline, EventType.DEADLINE, sub, inv, 0);
        }
        RiskAssessment risk = riskScoring.assess(tenureMonths(sub, at), sub.pastFailures,
                sub.customer.monthlyPrice());

        // Retry fatigue: every failed *retry* sends the customer a "debit failed" SMS from their bank;
        // some customers respond by cancelling. Riskier customers are more likely to.
        if (!firstFailure && !customerInitiated) {
            double pCancel = 0.01 + 0.04 * risk.score() / 100.0;
            if (DeterministicRandom.uniform(seed, sub.customer.id(), inv.periodStart.toEpochDay(), inv.attempts,
                    SALT_FATIGUE) < pCancel) {
                fatigueCancellations++;
                writeOff(inv);
                return;
            }
        }

        RetryDecision d = policy.onFailure(new RetryContext(result.failureReason(), inv.attempts, 0, risk, at,
                inv.deadline));
        inv.token++;
        switch (d.action()) {
            case RETRY -> push(d.nextRetryAt(), EventType.RETRY, sub, inv, inv.token);
            case AWAIT_CUSTOMER -> { /* wait for the customer or the deadline */ }
            case GIVE_UP -> {
                writeOff(inv);
                return;
            }
        }
        if (d.followUpAt() != null) {
            push(d.followUpAt(), EventType.FOLLOW_UP, sub, inv, inv.token);
        }
        if (d.reminderNow() != null) {
            remind(inv, d.reminderNow(), at);
        }
    }

    /**
     * Customer behaviour model: a reminder makes the customer act (pay now / update mandate) with probability
     * engagement x relevance x urgency. A specific message ("update your mandate") is more effective than a
     * generic "payment failed", and every reminder is less effective the longer the invoice has been overdue.
     */
    private void remind(SimInvoice inv, ReminderType type, LocalDateTime at) {
        reminders++;
        inv.remindersSent++;
        if (inv.customerActed) {
            return;
        }
        double relevance = relevance(type, inv.lastReason);
        double daysOverdue = Duration.between(inv.firstFailedAt, at).toHours() / 24.0;
        double pAct = inv.sub.customer.engagement() * relevance * Math.exp(-daysOverdue / 12.0) * 0.6;
        long key = inv.periodStart.toEpochDay();
        if (DeterministicRandom.uniform(seed, inv.sub.customer.id(), key, inv.remindersSent, SALT_ACT) < pAct) {
            inv.customerActed = true;
            double delayHours = 2 + 34 * DeterministicRandom.uniform(seed, inv.sub.customer.id(), key, SALT_ACT_DELAY);
            push(at.plusMinutes((long) (delayHours * 60)), EventType.CUSTOMER_ACTION, inv.sub, inv, 0);
        }
    }

    static double relevance(ReminderType type, FailureReason reason) {
        return switch (type) {
            case UPDATE_PAYMENT_METHOD -> reason.isHardDecline() ? 1.0 : 0.3;
            case LOW_BALANCE_HEADS_UP -> reason == FailureReason.INSUFFICIENT_BALANCE ? 1.0
                    : reason == FailureReason.LIMIT_EXCEEDED ? 0.6 : 0.3;
            case PAYMENT_FAILED -> 0.5;
            default -> 0.0;
        };
    }

    /** The customer fixes the problem (new mandate/card, or adds money) and pays from the reminder link. */
    private void customerActs(SimInvoice inv, LocalDateTime at) {
        if (inv.status != InvoiceStatus.FAILED) {
            return;
        }
        if (inv.lastReason.isHardDecline()) {
            inv.sub.instrumentVersion++;
        } else {
            gateway.recordTopUp(inv.sub.customer.id(), at.plusDays(1));
        }
        customerPayments++;
        attempt(inv, at, true);
    }

    private void writeOff(SimInvoice inv) {
        inv.status = InvoiceStatus.WRITTEN_OFF;
        inv.token++;
        inv.sub.status = SubscriptionStatus.CANCELLED;
    }

    // ---- scheduling helpers -------------------------------------------------------------------------------

    private void scheduleBill(SimSubscription sub, LocalDateTime notBefore) {
        if (sub.billScheduled || sub.status == SubscriptionStatus.CANCELLED || !sub.nextBillingDate.isBefore(end)) {
            return;
        }
        // Billing job runs at 09:00 IST; spread charges over 3 hours as a real batch would be.
        long minutes = (long) (180 * DeterministicRandom.uniform(seed, sub.customer.id(), SALT_BILL_MINUTE));
        LocalDateTime at = sub.nextBillingDate.atTime(9, 0).plusMinutes(minutes);
        if (notBefore != null && at.isBefore(notBefore)) {
            at = notBefore;
        }
        sub.billScheduled = true;
        push(at, EventType.BILL, sub, null, 0);
    }

    private void push(LocalDateTime at, EventType type, SimSubscription sub, SimInvoice inv, int token) {
        queue.add(new Event(at, seq++, type, sub, inv, token));
    }

    private static boolean isCurrent(Event e) {
        return e.invoice().status == InvoiceStatus.FAILED && e.invoice().token == e.token();
    }

    private static int tenureMonths(SimSubscription sub, LocalDateTime at) {
        return (int) ChronoUnit.MONTHS.between(sub.customer.signupDate(), at.toLocalDate());
    }

    // ---- results ------------------------------------------------------------------------------------------

    private PolicyResult summarise() {
        long failed = 0;
        long recovered = 0;
        long writtenOff = 0;
        BigDecimal failedRevenue = BigDecimal.ZERO;
        BigDecimal recoveredRevenue = BigDecimal.ZERO;
        Map<FailureReason, long[]> byReason = new EnumMap<>(FailureReason.class);

        for (SimInvoice inv : invoices) {
            if (inv.firstReason == null) {
                continue;
            }
            failed++;
            failedRevenue = failedRevenue.add(inv.amount);
            long[] counts = byReason.computeIfAbsent(inv.firstReason, r -> new long[2]);
            counts[0]++;
            if (inv.status == InvoiceStatus.RECOVERED) {
                recovered++;
                counts[1]++;
                recoveredRevenue = recoveredRevenue.add(inv.amount);
            } else if (inv.status == InvoiceStatus.WRITTEN_OFF) {
                writtenOff++;
            }
        }
        List<PolicyResult.ReasonResult> reasons = byReason.entrySet().stream()
                .map(e -> new PolicyResult.ReasonResult(e.getKey(), e.getValue()[0], e.getValue()[1],
                        percent(e.getValue()[1], e.getValue()[0])))
                .toList();
        long cancelled = subscriptions.stream().filter(s -> s.status == SubscriptionStatus.CANCELLED).count();
        return new PolicyResult(policy.name(), invoices.size(), failed, recovered, writtenOff, failedRevenue,
                recoveredRevenue, percent(recoveredRevenue, failedRevenue), percent(recovered, failed),
                policyRetries, customerPayments, reminders,
                recovered == 0 ? 0 : Math.round(totalDaysToRecover / recovered * 10) / 10.0,
                fatigueCancellations, cancelled, subscriptions.size() - cancelled, collected, reasons);
    }

    static BigDecimal percent(long part, long whole) {
        return percent(BigDecimal.valueOf(part), BigDecimal.valueOf(whole));
    }

    static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }
}
