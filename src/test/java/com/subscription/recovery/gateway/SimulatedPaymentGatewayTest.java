package com.subscription.recovery.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulatedPaymentGatewayTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 3, 20, 9, 0);

    @Test
    void sameSeedGivesIdenticalResults() {
        assertThat(runBatch(7)).isEqualTo(runBatch(7));
        assertThat(runBatch(7)).isNotEqualTo(runBatch(8));
    }

    @Test
    void revokedMandateStaysRevokedUntilCustomerProvidesANewOne() {
        SimulatedPaymentGateway gw = new SimulatedPaymentGateway(1, id -> null);
        gw.registerProfile(1, new CustomerFinancialProfile(1, 0, 0, 1.0, 0, 0)); // always breaks on first charge

        assertThat(gw.charge(req(1, 1, 1, T)).failureReason()).isEqualTo(FailureReason.MANDATE_REVOKED);
        assertThat(gw.charge(req(1, 1, 2, T.plusDays(3))).failureReason())
                .as("blind retry on the same mandate").isEqualTo(FailureReason.MANDATE_REVOKED);
        assertThat(gw.charge(req(1, 2, 3, T.plusDays(4))).success()).as("new mandate version").isTrue();
    }

    @Test
    void lowBalanceIsPersistentUntilTheNextSalaryCredit() {
        SimulatedPaymentGateway gw = new SimulatedPaymentGateway(3, id -> null);
        CustomerFinancialProfile broke = new CustomerFinancialProfile(1, 1.0, 0, 0, 0, 0); // runs dry within days
        int dryLateInMonth = 0;
        int dryRightAfterPayday = 0;
        for (long c = 1; c <= 200; c++) {
            if (gw.isAccountDry(c, broke, LocalDate.of(2026, 3, 25))) {
                dryLateInMonth++;
            }
            if (gw.isAccountDry(c, broke, LocalDate.of(2026, 4, 1))) {
                dryRightAfterPayday++;
            }
        }
        assertThat(dryLateInMonth).isGreaterThan(150);
        assertThat(dryRightAfterPayday).isZero();
        assertThat(SimulatedPaymentGateway.lastSalaryDate(LocalDate.of(2026, 3, 3), 5)).isEqualTo(LocalDate.of(2026, 2, 5));
    }

    private static List<String> runBatch(long seed) {
        SimulatedPaymentGateway gw = SimulatedPaymentGateway.withDerivedProfiles(seed);
        List<String> out = new ArrayList<>();
        for (long c = 1; c <= 300; c++) {
            PaymentResult r = gw.charge(req(c, 1, 1, T.plusHours(c)));
            out.add(r.success() ? "OK" : r.failureReason().name());
        }
        return out;
    }

    private static ChargeRequest req(long customer, int version, int attempt, LocalDateTime at) {
        return new ChargeRequest(customer, 100 + attempt, new BigDecimal("499"), PaymentMethod.UPI_AUTOPAY,
                version, attempt, at);
    }
}
