package com.subscription.recovery.dto;

import com.subscription.recovery.domain.AttemptResult;
import com.subscription.recovery.domain.FailureReason;
import java.time.LocalDateTime;

public record PaymentAttemptResponse(
        int attemptNumber,
        LocalDateTime attemptedAt,
        AttemptResult result,
        FailureReason failureReason,
        String gatewayReference,
        String decisionNote) {
}
