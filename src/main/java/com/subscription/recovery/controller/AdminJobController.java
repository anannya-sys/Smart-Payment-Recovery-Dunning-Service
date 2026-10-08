package com.subscription.recovery.controller;

import com.subscription.recovery.dto.DemoDataResponse;
import com.subscription.recovery.dto.JobRunResponse;
import com.subscription.recovery.service.BillingService;
import com.subscription.recovery.service.DemoDataService;
import com.subscription.recovery.service.RecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Run the scheduled jobs on demand (demos, ops, backfills). In production these would sit behind admin auth.
 */
@RestController
@RequestMapping("/api/admin/jobs")
@Tag(name = "Admin jobs")
public class AdminJobController {

    private final BillingService billing;
    private final RecoveryService recovery;
    private final DemoDataService demoData;

    public AdminJobController(BillingService billing, RecoveryService recovery, DemoDataService demoData) {
        this.billing = billing;
        this.recovery = recovery;
        this.demoData = demoData;
    }

    @PostMapping("/billing")
    @Operation(summary = "Run today's billing job now")
    public JobRunResponse runBilling() {
        return billing.runBilling();
    }

    @PostMapping("/demo-data")
    @Operation(summary = "Load a demo business (plans, customers, subscriptions) into an empty database and bill it")
    public DemoDataResponse loadDemoData() {
        return demoData.load();
    }

    @PostMapping("/recovery")
    @Operation(summary = "Process due retries, reminders and write-offs now")
    public JobRunResponse runRecovery() {
        return recovery.runRecovery();
    }
}
