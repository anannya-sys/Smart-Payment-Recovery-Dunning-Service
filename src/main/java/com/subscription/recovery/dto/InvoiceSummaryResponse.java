package com.subscription.recovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.domain.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One row of the invoice list: the invoice plus who it belongs to, so a UI can show it without extra calls. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceSummaryResponse(
        Long id,
        Long subscriptionId,
        Long customerId,
        String customerName,
        String planName,
        BigDecimal amount,
        LocalDate dueDate,
        InvoiceStatus status,
        FailureReason initialFailureReason,
        FailureReason lastFailureReason,
        int attemptCount,
        LocalDateTime nextRetryAt,
        LocalDateTime recoveryDeadline,
        LocalDateTime paidAt) {
}
