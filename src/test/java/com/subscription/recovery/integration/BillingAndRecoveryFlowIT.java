package com.subscription.recovery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.subscription.recovery.domain.BillingCycle;
import com.subscription.recovery.domain.Customer;
import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.domain.Invoice;
import com.subscription.recovery.domain.InvoiceStatus;
import com.subscription.recovery.domain.PaymentMethod;
import com.subscription.recovery.domain.Plan;
import com.subscription.recovery.domain.Subscription;
import com.subscription.recovery.domain.SubscriptionStatus;
import com.subscription.recovery.repository.CustomerRepository;
import com.subscription.recovery.repository.InvoiceRepository;
import com.subscription.recovery.repository.PaymentAttemptRepository;
import com.subscription.recovery.repository.PlanRepository;
import com.subscription.recovery.repository.SubscriptionRepository;
import com.subscription.recovery.service.BillingService;
import com.subscription.recovery.service.RecoveryService;
import com.subscription.recovery.support.MutableClock;
import com.subscription.recovery.support.ScriptedPaymentGateway;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full billing -> failure -> retry -> recovery flows against PostgreSQL, with a controllable clock and a scripted
 * gateway so every step is deterministic.
 */
@Import(BillingAndRecoveryFlowIT.TestBeans.class)
class BillingAndRecoveryFlowIT extends AbstractIntegrationTest {

    static final LocalDateTime START = LocalDateTime.of(2026, 3, 14, 9, 0);

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }

        @Bean
        @Primary
        ScriptedPaymentGateway scriptedGateway() {
            return new ScriptedPaymentGateway();
        }
    }

    @Autowired MutableClock clock;
    @Autowired ScriptedPaymentGateway gateway;
    @Autowired BillingService billing;
    @Autowired RecoveryService recovery;
    @Autowired CustomerRepository customers;
    @Autowired PlanRepository plans;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired InvoiceRepository invoices;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired Clock injectedClock;
    @Autowired MockMvc mvc;

    @BeforeEach
    void reset() {
        clock.set(START);
        gateway.reset();
        // Earlier tests may have left due subscriptions behind; settle them so each test starts clean.
        billing.runBilling();
        gateway.reset();
    }

    @Test
    void appUsesTheInjectedClock() {
        assertThat(injectedClock).isSameAs(clock);
    }

    @Test
    void insufficientBalanceIsRetriedOnSalaryDateAndRecovered() {
        Subscription sub = newSubscription(PaymentMethod.UPI_AUTOPAY);
        gateway.willReturn(FailureReason.INSUFFICIENT_BALANCE, null);

        billing.runBilling();

        Invoice invoice = onlyInvoice(sub);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
        assertThat(invoice.getNextRetryAt()).isEqualTo(LocalDateTime.of(2026, 4, 1, 10, 0));
        assertThat(statusOf(sub)).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscriptions.findById(sub.getId()).orElseThrow().getNextBillingDate())
                .isEqualTo(LocalDate.of(2026, 4, 14));

        clock.set(LocalDateTime.of(2026, 3, 31, 10, 0));
        assertThat(recovery.runRecovery().processed()).as("not due yet").isZero();

        clock.set(LocalDateTime.of(2026, 4, 1, 10, 0));
        recovery.runRecovery();

        invoice = onlyInvoice(sub);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.RECOVERED);
        assertThat(statusOf(sub)).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(attempts.findByInvoiceIdOrderByAttemptNumber(invoice.getId())).hasSize(2)
                .first().satisfies(a -> assertThat(a.getDecisionNote()).contains("salary date"));
    }

    @Test
    void revokedMandateRecoversOnlyAfterCustomerUpdatesPaymentMethod() throws Exception {
        Subscription sub = newSubscription(PaymentMethod.UPI_AUTOPAY);
        gateway.willReturn(FailureReason.MANDATE_REVOKED);
        billing.runBilling();

        clock.set(START.plusDays(10));
        assertThat(recovery.runRecovery().processed()).as("no blind retries").isZero();

        mvc.perform(put("/api/subscriptions/" + sub.getId() + "/payment-method")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isOk());
        recovery.runRecovery();

        assertThat(onlyInvoice(sub).getStatus()).isEqualTo(InvoiceStatus.RECOVERED);
        assertThat(gateway.requests()).last().satisfies(r -> {
            assertThat(r.method()).isEqualTo(PaymentMethod.CARD);
            assertThat(r.instrumentVersion()).isEqualTo(2);
        });
    }

    @Test
    void invoiceIsWrittenOffAndSubscriptionCancelledAfterDeadline() {
        Subscription sub = newSubscription(PaymentMethod.CARD);
        gateway.willReturn(FailureReason.CARD_EXPIRED);
        billing.runBilling();

        clock.set(START.plusDays(29));
        recovery.runRecovery();

        assertThat(onlyInvoice(sub).getStatus()).isEqualTo(InvoiceStatus.WRITTEN_OFF);
        assertThat(statusOf(sub)).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void billingTwiceOnTheSameDayCreatesOneInvoice() {
        Subscription sub = newSubscription(PaymentMethod.UPI_AUTOPAY);
        billing.runBilling();
        billing.runBilling();
        assertThat(invoices.findBySubscriptionIdOrderByDueDateDesc(sub.getId())).hasSize(1);
    }

    @Test
    void analyticsAggregatesFailedAndRecoveredRevenueByReason() throws Exception {
        clock.set(LocalDateTime.of(2030, 1, 10, 9, 0)); // isolated date range for this test
        Subscription a = newSubscription(PaymentMethod.UPI_AUTOPAY);
        Subscription b = newSubscription(PaymentMethod.UPI_AUTOPAY);
        gateway.willReturn(FailureReason.BANK_DOWNTIME, FailureReason.BANK_DOWNTIME, null);
        billing.runBilling();                       // both fail with downtime
        clock.set(LocalDateTime.of(2030, 1, 10, 11, 0));
        recovery.runRecovery();                     // first retried: success; second: success (script empty)

        mvc.perform(get("/api/analytics/recovery?from=2030-01-01&to=2030-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedInvoices").value(2))
                .andExpect(jsonPath("$.recoveredInvoices").value(2))
                .andExpect(jsonPath("$.totalFailedRevenue").value(998.00))
                .andExpect(jsonPath("$.recoveryRatePercent").value(100.00))
                .andExpect(jsonPath("$.byFailureReason[0].reason").value("BANK_DOWNTIME"));
        assertThat(List.of(a, b)).allSatisfy(s -> assertThat(statusOf(s)).isEqualTo(SubscriptionStatus.ACTIVE));
    }

    private Subscription newSubscription(PaymentMethod method) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Customer c = customers.save(new Customer("Cust " + unique, unique + "@example.in",
                LocalDate.of(2023, 1, 1), 0));
        Plan p = plans.save(new Plan("Plan " + unique, new BigDecimal("499.00"), BillingCycle.MONTHLY));
        return subscriptions.save(new Subscription(c, p, method, LocalDate.now(clock)));
    }

    private Invoice onlyInvoice(Subscription sub) {
        List<Invoice> list = invoices.findBySubscriptionIdOrderByDueDateDesc(sub.getId());
        assertThat(list).hasSize(1);
        return list.get(0);
    }

    private SubscriptionStatus statusOf(Subscription sub) {
        return subscriptions.findById(sub.getId()).orElseThrow().getStatus();
    }
}
