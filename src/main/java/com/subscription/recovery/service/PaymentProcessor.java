package com.subscription.recovery.service;

import com.subscription.recovery.config.RecoveryProperties;
import com.subscription.recovery.domain.AttemptResult;
import com.subscription.recovery.domain.Customer;
import com.subscription.recovery.domain.Invoice;
import com.subscription.recovery.domain.InvoiceStatus;
import com.subscription.recovery.domain.PaymentAttempt;
import com.subscription.recovery.domain.Subscription;
import com.subscription.recovery.domain.SubscriptionStatus;
import com.subscription.recovery.gateway.ChargeRequest;
import com.subscription.recovery.gateway.PaymentGateway;
import com.subscription.recovery.gateway.PaymentResult;
import com.subscription.recovery.notification.NotificationService;
import com.subscription.recovery.repository.InvoiceRepository;
import com.subscription.recovery.repository.PaymentAttemptRepository;
import com.subscription.recovery.retry.RecoveryPolicy;
import com.subscription.recovery.retry.ReminderType;
import com.subscription.recovery.retry.RetryContext;
import com.subscription.recovery.retry.RetryDecision;
import com.subscription.recovery.risk.RiskAssessment;
import com.subscription.recovery.risk.RiskScoringService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The heart of the recovery engine. Each public method handles <b>one invoice in one transaction</b>:
 * charge it, record the attempt, move the subscription through its state machine and ask the
 * {@link RecoveryPolicy} what to do next.
 *
 * <pre>
 *  charge ok  : invoice PAID / RECOVERED,   subscription PAST_DUE -> ACTIVE
 *  charge fail: invoice FAILED,              subscription ACTIVE   -> PAST_DUE,  policy decides:
 *                 RETRY          -> schedule next_retry_at
 *                 AWAIT_CUSTOMER -> remind, wait for new payment method until the deadline
 *                 GIVE_UP        -> invoice WRITTEN_OFF, subscription -> CANCELLED
 * </pre>
 */
@Component
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final InvoiceRepository invoices;
    private final PaymentAttemptRepository attempts;
    private final PaymentGateway gateway;
    private final RecoveryPolicy policy;
    private final RiskScoringService riskScoring;
    private final NotificationService notifications;
    private final RecoveryProperties props;
    private final Clock clock;

    public PaymentProcessor(InvoiceRepository invoices, PaymentAttemptRepository attempts, PaymentGateway gateway,
                            RecoveryPolicy policy, RiskScoringService riskScoring,
                            NotificationService notifications, RecoveryProperties props, Clock clock) {
        this.invoices = invoices;
        this.attempts = attempts;
        this.gateway = gateway;
        this.policy = policy;
        this.riskScoring = riskScoring;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    /** Charges the invoice (first attempt or a retry). Returns true if the payment succeeded. */
    @Transactional
    public boolean attemptPayment(Long invoiceId) {
        Invoice invoice = load(invoiceId);
        if (invoice.getStatus().isTerminal()) {
            return invoice.getStatus() != InvoiceStatus.WRITTEN_OFF; // already settled by someone else
        }
        Subscription sub = invoice.getSubscription();
        Customer customer = sub.getCustomer();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        int attemptNo = invoice.nextAttemptNumber();

        PaymentResult result = gateway.charge(new ChargeRequest(customer.getId(), invoice.getId(),
                invoice.getAmount(), sub.getPaymentMethod(), sub.getPaymentMethodVersion(), attemptNo, now));

        PaymentAttempt attempt = new PaymentAttempt(invoice, attemptNo, now,
                result.success() ? AttemptResult.SUCCESS : AttemptResult.FAILED,
                result.failureReason(), result.reference());

        if (result.success()) {
            boolean wasRetry = invoice.getStatus() == InvoiceStatus.FAILED;
            invoice.markPaid(now);
            if (sub.getStatus() == SubscriptionStatus.PAST_DUE) {
                sub.transitionTo(SubscriptionStatus.ACTIVE);
            }
            if (wasRetry) {
                attempt.setDecisionNote("Recovered on attempt " + attemptNo);
                notifications.notify(customer, invoice, ReminderType.PAYMENT_RECOVERED);
            }
            attempts.save(attempt);
            return true;
        }

        handleFailure(invoice, sub, customer, attempt, result, now);
        attempts.save(attempt);
        return false;
    }

    private void handleFailure(Invoice invoice, Subscription sub, Customer customer, PaymentAttempt attempt,
                               PaymentResult result, LocalDateTime now) {
        boolean firstFailure = invoice.getStatus() == InvoiceStatus.PENDING;
        invoice.markFailed(result.failureReason(), now.plusDays(props.recoveryWindowDays()));
        if (firstFailure) {
            customer.recordPaymentFailure();
        }
        if (sub.getStatus() == SubscriptionStatus.ACTIVE) {
            sub.transitionTo(SubscriptionStatus.PAST_DUE);
        }

        RiskAssessment risk = riskScoring.assess(customer.tenureMonths(now.toLocalDate()),
                customer.getPastFailureCount(), sub.getPlan().getMonthlyPrice());
        RetryDecision decision = policy.onFailure(new RetryContext(result.failureReason(),
                invoice.getAttemptCount(), 0, risk, now, invoice.getRecoveryDeadline()));
        attempt.setDecisionNote(policy.name() + " [risk " + risk.score() + " " + risk.band() + "]: "
                + decision.rationale());
        log.info("Invoice {} attempt {} failed ({}): {}", invoice.getId(), invoice.getAttemptCount(),
                result.failureReason(), decision.rationale());

        switch (decision.action()) {
            case RETRY -> invoice.scheduleRetry(decision.nextRetryAt());
            case AWAIT_CUSTOMER -> invoice.scheduleRetry(null);
            case GIVE_UP -> {
                writeOff(invoice, sub, customer);
                return;
            }
        }
        invoice.scheduleReminder(decision.followUpAt());
        if (decision.reminderNow() != null) {
            notifications.notify(customer, invoice, decision.reminderNow());
        }
    }

    /** Sends the scheduled follow-up reminder: the actionable one for hard declines, a heads-up otherwise. */
    @Transactional
    public void sendFollowUpReminder(Long invoiceId) {
        Invoice invoice = load(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.FAILED) {
            return;
        }
        ReminderType type = invoice.getLastFailureReason() != null && invoice.getLastFailureReason().isHardDecline()
                ? ReminderType.UPDATE_PAYMENT_METHOD : ReminderType.LOW_BALANCE_HEADS_UP;
        notifications.notify(invoice.getSubscription().getCustomer(), invoice, type);
        invoice.scheduleReminder(null);
    }

    /** Recovery window is over: stop trying. */
    @Transactional
    public void writeOffIfPastDeadline(Long invoiceId) {
        Invoice invoice = load(invoiceId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (invoice.getStatus() == InvoiceStatus.FAILED && invoice.getRecoveryDeadline() != null
                && !invoice.getRecoveryDeadline().isAfter(now)) {
            writeOff(invoice, invoice.getSubscription(), invoice.getSubscription().getCustomer());
        }
    }

    private void writeOff(Invoice invoice, Subscription sub, Customer customer) {
        invoice.writeOff();
        if (sub.getStatus() != SubscriptionStatus.CANCELLED) {
            sub.transitionTo(SubscriptionStatus.CANCELLED);
        }
        notifications.notify(customer, invoice, ReminderType.SUBSCRIPTION_CANCELLED);
        log.info("Invoice {} written off; subscription {} cancelled", invoice.getId(), sub.getId());
    }

    private Invoice load(Long id) {
        return invoices.findById(id).orElseThrow(() -> new IllegalStateException("Invoice " + id + " vanished"));
    }
}
