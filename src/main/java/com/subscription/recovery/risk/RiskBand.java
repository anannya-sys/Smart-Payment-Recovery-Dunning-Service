package com.subscription.recovery.risk;

/** Coarse bucket of the 0-100 churn-risk score; the retry engine reacts to the band, not the raw number. */
public enum RiskBand {
    LOW,     // 0-34
    MEDIUM,  // 35-64
    HIGH;    // 65-100

    public static RiskBand fromScore(int score) {
        if (score >= 65) {
            return HIGH;
        }
        return score >= 35 ? MEDIUM : LOW;
    }
}
