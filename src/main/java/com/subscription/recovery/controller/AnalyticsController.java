package com.subscription.recovery.controller;

import com.subscription.recovery.dto.AnalyticsResponse;
import com.subscription.recovery.dto.OverviewResponse;
import com.subscription.recovery.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final Clock clock;

    public AnalyticsController(AnalyticsService analytics, Clock clock) {
        this.analytics = analytics;
        this.clock = clock;
    }

    @GetMapping("/overview")
    @Operation(summary = "Current snapshot: customers, subscriptions by status, MRR, revenue at risk")
    public OverviewResponse overview() {
        return analytics.overview();
    }

    @GetMapping("/recovery")
    @Operation(summary = "Failed vs recovered revenue, recovery rate and breakdown by failure reason "
            + "(defaults to the last 6 months)")
    public AnalyticsResponse recovery(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusMonths(6) : from;
        return analytics.recovery(start, end);
    }
}
