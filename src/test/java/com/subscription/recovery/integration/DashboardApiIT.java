package com.subscription.recovery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subscription.recovery.domain.BillingCycle;
import com.subscription.recovery.domain.Customer;
import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.domain.PaymentMethod;
import com.subscription.recovery.domain.Plan;
import com.subscription.recovery.domain.Subscription;
import com.subscription.recovery.repository.CustomerRepository;
import com.subscription.recovery.repository.PlanRepository;
import com.subscription.recovery.repository.SubscriptionRepository;
import com.subscription.recovery.service.InvoiceIssuer;
import com.subscription.recovery.service.PaymentProcessor;
import com.subscription.recovery.support.MutableClock;
import com.subscription.recovery.support.ScriptedPaymentGateway;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** The endpoints behind the web dashboard: invoice list, overview snapshot and the demo-data loader. */
@Import(DashboardApiIT.TestBeans.class)
class DashboardApiIT extends AbstractIntegrationTest {

    /** Years after the dates the other integration tests use, so their subscriptions are never billed here. */
    static final LocalDateTime START = LocalDateTime.of(2032, 1, 5, 9, 0);

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
    @Autowired InvoiceIssuer issuer;
    @Autowired PaymentProcessor processor;
    @Autowired CustomerRepository customers;
    @Autowired PlanRepository plans;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void reset() {
        clock.set(START);
        gateway.reset();
    }

    @Test
    void invoiceListFiltersByStatusAndNamesCustomerAndPlan() throws Exception {
        Subscription sub = newSubscription();
        gateway.willReturn(FailureReason.MANDATE_REVOKED);
        long invoiceId = billOnly(sub);

        JsonNode failed = findById(getJson("/api/invoices?status=FAILED&size=1000"), invoiceId);
        assertThat(failed).as("listed under FAILED").isNotNull();
        assertThat(failed.get("customerName").asText()).isEqualTo(sub.getCustomer().getName());
        assertThat(failed.get("planName").asText()).isEqualTo(sub.getPlan().getName());
        assertThat(failed.get("initialFailureReason").asText()).isEqualTo("MANDATE_REVOKED");
        assertThat(failed.get("amount").decimalValue()).isEqualByComparingTo("499.00");

        // Several statuses can be combined; a FAILED invoice must not show up under PAID or RECOVERED.
        assertThat(findById(getJson("/api/invoices?status=PAID&status=RECOVERED&size=1000"), invoiceId)).isNull();
    }

    @Test
    void overviewReflectsAFailedPayment() throws Exception {
        JsonNode before = getJson("/api/analytics/overview");
        Subscription sub = newSubscription();
        gateway.willReturn(FailureReason.INSUFFICIENT_BALANCE);
        billOnly(sub);
        JsonNode after = getJson("/api/analytics/overview");

        // Other tests share the database, so compare before/after instead of absolute numbers.
        assertThat(after.get("customers").asLong() - before.get("customers").asLong()).isEqualTo(1);
        assertThat(after.at("/subscriptionsByStatus/PAST_DUE").asLong()
                - before.at("/subscriptionsByStatus/PAST_DUE").asLong()).isEqualTo(1);
        assertThat(after.get("invoicesInRecovery").asLong() - before.get("invoicesInRecovery").asLong()).isEqualTo(1);
        assertThat(after.get("revenueAtRisk").decimalValue().subtract(before.get("revenueAtRisk").decimalValue()))
                .isEqualByComparingTo("499.00");
        // PAST_DUE still counts towards MRR: it is revenue recovery is trying to keep.
        assertThat(after.get("monthlyRecurringRevenue").decimalValue()
                .subtract(before.get("monthlyRecurringRevenue").decimalValue())).isEqualByComparingTo("499.00");
    }

    @Test
    void demoDataLoadsOnlyIntoAnEmptyDatabase() throws Exception {
        // The loader refuses to mix demo rows with existing data, so start this test from an empty database.
        jdbc.execute("truncate payment_attempts, invoices, subscriptions, customers, plans restart identity cascade");

        mvc.perform(post("/api/admin/jobs/demo-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plansCreated").value(5))
                .andExpect(jsonPath("$.customersCreated").value(48))
                .andExpect(jsonPath("$.subscriptionsCreated").value(48))
                .andExpect(jsonPath("$.billing.processed").value(48));
        assertThat(subscriptions.count()).isEqualTo(48);

        mvc.perform(post("/api/admin/jobs/demo-data"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Demo data can only be loaded into an empty database"));
    }

    /**
     * Invoice and charge just this subscription. A full billing run would also bill subscriptions other tests left
     * behind, and they could use up the gateway's scripted failure first.
     */
    private long billOnly(Subscription sub) {
        long invoiceId = issuer.issue(sub.getId(), LocalDate.now(clock)).orElseThrow();
        processor.attemptPayment(invoiceId);
        return invoiceId;
    }

    private Subscription newSubscription() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Customer c = customers.save(new Customer("Cust " + unique, unique + "@example.in",
                LocalDate.of(2030, 1, 1), 0));
        Plan p = plans.save(new Plan("Plan " + unique, new BigDecimal("499.00"), BillingCycle.MONTHLY));
        return subscriptions.save(new Subscription(c, p, PaymentMethod.UPI_AUTOPAY, LocalDate.now(clock)));
    }

    private JsonNode getJson(String url) throws Exception {
        return json.readTree(mvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** The row with this id in a paged response, or null. */
    private static JsonNode findById(JsonNode page, long id) {
        List<JsonNode> rows = new ArrayList<>();
        page.get("content").forEach(rows::add);
        return rows.stream().filter(r -> r.get("id").asLong() == id).findFirst().orElse(null);
    }
}
