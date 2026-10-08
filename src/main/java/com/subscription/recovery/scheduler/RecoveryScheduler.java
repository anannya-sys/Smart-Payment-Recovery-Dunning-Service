package com.subscription.recovery.scheduler;

import com.subscription.recovery.service.RecoveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 15 minutes (configurable): process due retries, reminders and write-offs. */
@Component
public class RecoveryScheduler {

    private final RecoveryService recovery;

    public RecoveryScheduler(RecoveryService recovery) {
        this.recovery = recovery;
    }

    @Scheduled(cron = "${recovery.cron}", zone = "${app.time-zone:Asia/Kolkata}")
    public void processDueRecoveries() {
        recovery.runRecovery();
    }
}
