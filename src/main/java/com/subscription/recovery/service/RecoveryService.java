package com.subscription.recovery.service;

import com.subscription.recovery.dto.JobRunResponse;
import com.subscription.recovery.repository.InvoiceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Periodic recovery run. Three cheap indexed queries find the work that is due:
 * <ol>
 *   <li>retries whose time has come,</li>
 *   <li>follow-up reminders whose time has come,</li>
 *   <li>invoices whose recovery window has ended (written off, subscription cancelled).</li>
 * </ol>
 * Each item runs in its own transaction via {@link PaymentProcessor}; an error on one invoice is logged and
 * the run continues. Optimistic locking ({@code @Version}) protects against two instances retrying the same
 * invoice at once.
 */
@Service
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    private final InvoiceRepository invoices;
    private final PaymentProcessor processor;
    private final Clock clock;

    public RecoveryService(InvoiceRepository invoices, PaymentProcessor processor, Clock clock) {
        this.invoices = invoices;
        this.processor = processor;
        this.clock = clock;
    }

    public JobRunResponse runRecovery() {
        LocalDateTime now = LocalDateTime.now(clock);
        int processed = 0;
        int recovered = 0;
        int failed = 0;

        for (Long id : invoices.findIdsWithRetryDue(now)) {
            processed++;
            try {
                if (processor.attemptPayment(id)) {
                    recovered++;
                } else {
                    failed++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("Retry of invoice {} failed unexpectedly", id, e);
            }
        }
        for (Long id : invoices.findIdsWithReminderDue(now)) {
            safely(() -> processor.sendFollowUpReminder(id), "reminder", id);
        }
        for (Long id : invoices.findIdsPastRecoveryDeadline(now)) {
            safely(() -> processor.writeOffIfPastDeadline(id), "write-off", id);
        }
        if (processed > 0) {
            log.info("Recovery run: {} retries, {} recovered, {} failed again", processed, recovered, failed);
        }
        return new JobRunResponse("recovery", processed, recovered, failed);
    }

    private static void safely(Runnable action, String what, Long invoiceId) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("{} for invoice {} failed", what, invoiceId, e);
        }
    }
}
