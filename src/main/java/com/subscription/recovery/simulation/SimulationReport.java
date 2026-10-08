package com.subscription.recovery.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Side-by-side comparison of the smart and fixed policies on the same synthetic customers and the same seed.
 *
 * @param recoveryRateLiftPoints smart recovery rate minus fixed recovery rate, in percentage points
 */
public record SimulationReport(
        int customers,
        int months,
        long seed,
        LocalDate startDate,
        PolicyResult smart,
        PolicyResult fixed,
        BigDecimal recoveryRateLiftPoints,
        BigDecimal extraRevenueRecovered,
        List<String> modelAssumptions) {
}
