package com.subscription.recovery.scheduler;

import com.subscription.recovery.service.BillingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron trigger only; all logic lives in {@link BillingService} so it can also be run from the admin API and tests.
 * Runs in Asia/Kolkata regardless of the server's time zone.
 */
@Component
public class BillingScheduler {

    private final BillingService billing;

    public BillingScheduler(BillingService billing) {
        this.billing = billing;
    }

    @Scheduled(cron = "${billing.cron}", zone = "${app.time-zone:Asia/Kolkata}")
    public void billDueSubscriptions() {
        billing.runBilling();
    }
}
