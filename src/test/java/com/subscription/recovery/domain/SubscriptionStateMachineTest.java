package com.subscription.recovery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SubscriptionStateMachineTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "ACTIVE,PAST_DUE,true", "ACTIVE,PAUSED,true", "ACTIVE,CANCELLED,true",
            "PAST_DUE,ACTIVE,true", "PAST_DUE,CANCELLED,true", "PAST_DUE,PAUSED,false",
            "PAUSED,ACTIVE,true", "PAUSED,PAST_DUE,false",
            "CANCELLED,ACTIVE,false", "CANCELLED,PAST_DUE,false"})
    void transitionTable(SubscriptionStatus from, SubscriptionStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void illegalTransitionThrowsAndLeavesStatusUnchanged() {
        Subscription sub = newSubscription();
        sub.transitionTo(SubscriptionStatus.CANCELLED);

        assertThatThrownBy(() -> sub.transitionTo(SubscriptionStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void invoiceThatFailsThenSucceedsIsRecoveredNotPaid() {
        Invoice invoice = new Invoice(newSubscription(), new BigDecimal("499"), LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 1));
        LocalDateTime t = LocalDateTime.of(2026, 3, 1, 9, 0);

        invoice.markFailed(FailureReason.INSUFFICIENT_BALANCE, t.plusDays(28));
        invoice.scheduleRetry(t.plusDays(4));
        invoice.markFailed(FailureReason.LIMIT_EXCEEDED, t.plusDays(99));
        invoice.markPaid(t.plusDays(5));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.RECOVERED);
        assertThat(invoice.getAttemptCount()).isEqualTo(3);
        assertThat(invoice.getInitialFailureReason()).isEqualTo(FailureReason.INSUFFICIENT_BALANCE);
        assertThat(invoice.getRecoveryDeadline()).as("deadline set by first failure only").isEqualTo(t.plusDays(28));
        assertThat(invoice.getNextRetryAt()).isNull();
    }

    private static Subscription newSubscription() {
        Customer c = new Customer("Test", "t@example.in", LocalDate.of(2025, 1, 1), 0);
        Plan p = new Plan("Basic", new BigDecimal("499"), BillingCycle.MONTHLY);
        return new Subscription(c, p, PaymentMethod.UPI_AUTOPAY, LocalDate.of(2026, 3, 1));
    }
}
