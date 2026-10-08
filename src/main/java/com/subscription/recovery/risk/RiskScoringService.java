package com.subscription.recovery.risk;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Rule-based churn-risk score (0-100). Deliberately simple and <b>explainable</b>: every point can be traced
 * to a rule, which matters when support or finance asks "why did this customer get fewer retries?".
 *
 * <pre>
 * score = tenurePoints + failurePoints + pricePoints        (capped at 100)
 *
 * tenurePoints : &lt; 3 months = 35 | 3-11 = 20 | 12-23 = 10 | 24+ = 0
 *                (new customers haven't formed a habit yet and churn the most)
 * failurePoints: 12 per past payment failure, max 40
 *                (past behaviour is the best predictor of future failures)
 * pricePoints  : &gt;= INR 1500 = 25 | &gt;= 750 = 15 | &gt;= 300 = 8 | else 0
 *                (a bigger debit is more likely to bounce and more likely to be questioned)
 * </pre>
 *
 * Bands: LOW &lt; 35, MEDIUM 35-64, HIGH &gt;= 65. A real system could later replace this with an ML model
 * behind the same method signature.
 */
@Service
public class RiskScoringService {

    static final int MAX_FAILURE_POINTS = 40;
    static final int POINTS_PER_FAILURE = 12;

    public RiskAssessment assess(int tenureMonths, int pastFailures, BigDecimal monthlyPrice) {
        List<String> factors = new ArrayList<>();
        int score = 0;

        int tenurePoints = tenurePoints(tenureMonths);
        if (tenurePoints > 0) {
            factors.add("Tenure " + tenureMonths + " month(s): +" + tenurePoints);
        }
        score += tenurePoints;

        int failurePoints = Math.min(MAX_FAILURE_POINTS, Math.max(0, pastFailures) * POINTS_PER_FAILURE);
        if (failurePoints > 0) {
            factors.add(pastFailures + " past payment failure(s): +" + failurePoints);
        }
        score += failurePoints;

        int pricePoints = pricePoints(monthlyPrice);
        if (pricePoints > 0) {
            factors.add("Plan price INR " + monthlyPrice.stripTrailingZeros().toPlainString() + "/month: +"
                    + pricePoints);
        }
        score += pricePoints;

        score = Math.min(100, score);
        if (factors.isEmpty()) {
            factors.add("Long-tenured customer with a clean payment history");
        }
        return new RiskAssessment(score, RiskBand.fromScore(score), List.copyOf(factors));
    }

    private static int tenurePoints(int months) {
        if (months < 3) {
            return 35;
        }
        if (months < 12) {
            return 20;
        }
        return months < 24 ? 10 : 0;
    }

    private static int pricePoints(BigDecimal monthlyPrice) {
        if (monthlyPrice == null) {
            return 0;
        }
        if (monthlyPrice.compareTo(BigDecimal.valueOf(1500)) >= 0) {
            return 25;
        }
        if (monthlyPrice.compareTo(BigDecimal.valueOf(750)) >= 0) {
            return 15;
        }
        return monthlyPrice.compareTo(BigDecimal.valueOf(300)) >= 0 ? 8 : 0;
    }
}
