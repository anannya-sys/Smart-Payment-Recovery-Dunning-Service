package com.subscription.recovery.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RiskScoringServiceTest {

    private final RiskScoringService scoring = new RiskScoringService();

    @Test
    void loyalCustomerWithCleanHistoryAndCheapPlanIsLowRisk() {
        RiskAssessment r = scoring.assess(36, 0, new BigDecimal("199"));
        assertThat(r.score()).isZero();
        assertThat(r.band()).isEqualTo(RiskBand.LOW);
    }

    @Test
    void newCustomerWithFailuresOnExpensivePlanIsHighRiskAndExplained() {
        RiskAssessment r = scoring.assess(1, 2, new BigDecimal("1999"));
        // 35 (tenure < 3) + 24 (2 failures x 12) + 25 (price >= 1500)
        assertThat(r.score()).isEqualTo(84);
        assertThat(r.band()).isEqualTo(RiskBand.HIGH);
        assertThat(r.factors()).hasSize(3).anySatisfy(f -> assertThat(f).contains("past payment failure"));
    }

    @Test
    void scoreIsCappedAt100AndFailurePointsAt40() {
        assertThat(scoring.assess(0, 50, new BigDecimal("5000")).score()).isEqualTo(100);
        assertThat(scoring.assess(30, 50, new BigDecimal("100")).score()).isEqualTo(40);
    }

    @ParameterizedTest(name = "score {0} -> {1}")
    @CsvSource({"0,LOW", "34,LOW", "35,MEDIUM", "64,MEDIUM", "65,HIGH", "100,HIGH"})
    void bandBoundaries(int score, RiskBand expected) {
        assertThat(RiskBand.fromScore(score)).isEqualTo(expected);
    }
}
