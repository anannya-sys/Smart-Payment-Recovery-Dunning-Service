package com.subscription.recovery.risk;

import java.util.List;

/**
 * Result of scoring a customer.
 *
 * @param score   0 (very safe) to 100 (very likely to churn)
 * @param factors one line per rule that added points, e.g. "Tenure 2 months (< 3): +35"
 */
public record RiskAssessment(int score, RiskBand band, List<String> factors) {

    public boolean isHighRisk() {
        return band == RiskBand.HIGH;
    }
}
