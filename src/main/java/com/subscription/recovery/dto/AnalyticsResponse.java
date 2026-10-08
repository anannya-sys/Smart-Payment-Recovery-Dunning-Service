package com.subscription.recovery.dto;

import com.subscription.recovery.domain.FailureReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Recovery KPIs for a date range.
 *
 * @param recoveryRatePercent recovered revenue / failed revenue x 100
 */
public record AnalyticsResponse(
        LocalDate from,
        LocalDate to,
        long failedInvoices,
        long recoveredInvoices,
        long invoicesStillInRecovery,
        BigDecimal totalFailedRevenue,
        BigDecimal recoveredRevenue,
        BigDecimal recoveryRatePercent,
        List<ReasonBreakdown> byFailureReason) {

    public record ReasonBreakdown(
            FailureReason reason,
            long failedInvoices,
            long recoveredInvoices,
            BigDecimal failedRevenue,
            BigDecimal recoveredRevenue,
            BigDecimal recoveryRatePercent) {
    }
}
