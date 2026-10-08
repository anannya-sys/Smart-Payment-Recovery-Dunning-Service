package com.subscription.recovery.service;

import com.subscription.recovery.domain.Invoice;
import com.subscription.recovery.domain.Subscription;
import com.subscription.recovery.domain.SubscriptionStatus;
import com.subscription.recovery.repository.InvoiceRepository;
import com.subscription.recovery.repository.SubscriptionRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the invoice for a subscription's current period, in its own transaction.
 *
 * <p>Separate bean on purpose: Spring's {@code @Transactional} works through a proxy, so a method calling another
 * {@code @Transactional} method on {@code this} would silently skip the proxy and the new transaction.
 */
@Component
public class InvoiceIssuer {

    private final SubscriptionRepository subscriptions;
    private final InvoiceRepository invoices;

    public InvoiceIssuer(SubscriptionRepository subscriptions, InvoiceRepository invoices) {
        this.subscriptions = subscriptions;
        this.invoices = invoices;
    }

    /**
     * @return the new invoice id, or empty if the subscription is no longer billable or the period was already
     *         invoiced (idempotency: re-running the job the same day is harmless)
     */
    @Transactional
    public Optional<Long> issue(Long subscriptionId, LocalDate today) {
        Subscription sub = subscriptions.findWithDetailsById(subscriptionId).orElse(null);
        if (sub == null || sub.getStatus() != SubscriptionStatus.ACTIVE || sub.getNextBillingDate().isAfter(today)) {
            return Optional.empty();
        }
        LocalDate periodStart = sub.getNextBillingDate();
        if (invoices.existsBySubscriptionIdAndPeriodStart(subscriptionId, periodStart)) {
            return Optional.empty();
        }
        Invoice invoice = invoices.save(new Invoice(sub, sub.getPlan().pricePerCycle(), periodStart, today));
        sub.advanceBillingDate();
        return Optional.of(invoice.getId());
    }
}
