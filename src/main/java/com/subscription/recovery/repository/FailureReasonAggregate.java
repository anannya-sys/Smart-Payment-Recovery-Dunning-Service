package com.subscription.recovery.repository;

import com.subscription.recovery.domain.FailureReason;
import java.math.BigDecimal;

/**
 * Read-only projection returned by the analytics query (JPQL constructor expression).
 *
 * @param failedCount     invoices whose first charge failed with this reason
 * @param failedAmount    total value of those invoices
 * @param recoveredCount  how many were later recovered
 * @param recoveredAmount value recovered
 * @param inRecoveryCount still being retried (neither recovered nor written off yet)
 */
public record FailureReasonAggregate(
        FailureReason reason,
        long failedCount,
        BigDecimal failedAmount,
        long recoveredCount,
        BigDecimal recoveredAmount,
        long inRecoveryCount) {
}
