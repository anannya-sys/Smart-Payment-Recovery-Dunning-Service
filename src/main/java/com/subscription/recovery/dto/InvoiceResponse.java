package com.subscription.recovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.domain.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceResponse(
        Long id,
        Long subscriptionId,
        BigDecimal amount,
        LocalDate periodStart,
        LocalDate dueDate,
        InvoiceStatus status,
        FailureReason initialFailureReason,
        FailureReason lastFailureReason,
        int attemptCount,
        LocalDateTime nextRetryAt,
        LocalDateTime recoveryDeadline,
        LocalDateTime paidAt,
        List<PaymentAttemptResponse> attempts) {
}
