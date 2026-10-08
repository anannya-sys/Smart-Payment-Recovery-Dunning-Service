package com.subscription.recovery.dto;

import com.subscription.recovery.risk.RiskBand;
import java.util.List;

/** Churn-risk score with the human-readable reasons behind it (explainability). */
public record RiskAssessmentResponse(Long customerId, int score, RiskBand band, List<String> factors) {
}
