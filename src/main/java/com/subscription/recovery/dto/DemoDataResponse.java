package com.subscription.recovery.dto;

/** Result of POST /api/admin/jobs/demo-data. */
public record DemoDataResponse(int plansCreated, int customersCreated, int subscriptionsCreated,
                               JobRunResponse billing) {
}
