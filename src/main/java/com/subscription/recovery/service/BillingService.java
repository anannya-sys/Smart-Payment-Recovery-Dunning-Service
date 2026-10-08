package com.subscription.recovery.service;

import com.subscription.recovery.domain.SubscriptionStatus;
import com.subscription.recovery.dto.JobRunResponse;
import com.subscription.recovery.repository.InvoiceRepository;
import com.subscription.recovery.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Daily billing run: invoice every ACTIVE subscription due today (or overdue, e.g. after downtime) and charge it.
 *
 * <p>Not {@code @Transactional} itself: each subscription is invoiced and charged in its own small transaction,
 * so one bad subscription can't roll back the whole run and locks are held only briefly.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final SubscriptionRepository subscriptions;
    private final InvoiceRepository invoices;
    private final InvoiceIssuer issuer;
    private final PaymentProcessor processor;
    private final Clock clock;

    public BillingService(SubscriptionRepository subscriptions, InvoiceRepository invoices, InvoiceIssuer issuer,
                          PaymentProcessor processor, Clock clock) {
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.issuer = issuer;
        this.processor = processor;
        this.clock = clock;
    }

    public JobRunResponse runBilling() {
        LocalDate today = LocalDate.now(clock);
        // Also pick up PENDING invoices a previous run created but crashed before charging.
        List<Long> toCharge = new ArrayList<>(invoices.findPendingIds());
        for (Long subId : subscriptions.findIdsDueForBilling(SubscriptionStatus.ACTIVE, today)) {
            try {
                Optional<Long> invoiceId = issuer.issue(subId, today);
                invoiceId.ifPresent(toCharge::add);
            } catch (RuntimeException e) {
                log.error("Could not invoice subscription {}", subId, e);
            }
        }
        int paid = 0;
        int failed = 0;
        for (Long invoiceId : toCharge) {
            try {
                if (processor.attemptPayment(invoiceId)) {
                    paid++;
                } else {
                    failed++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("Could not charge invoice {}", invoiceId, e);
            }
        }
        log.info("Billing run for {}: {} invoices charged, {} paid, {} failed", today, toCharge.size(), paid, failed);
        return new JobRunResponse("billing", toCharge.size(), paid, failed);
    }
}
