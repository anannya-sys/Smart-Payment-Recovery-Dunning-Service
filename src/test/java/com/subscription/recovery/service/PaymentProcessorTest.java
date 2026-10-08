package com.subscription.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.subscription.recovery.config.RecoveryProperties;
import com.subscription.recovery.domain.BillingCycle;
import com.subscription.recovery.domain.Customer;
import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.domain.Invoice;
import com.subscription.recovery.domain.InvoiceStatus;
import com.subscription.recovery.domain.PaymentMethod;
import com.subscription.recovery.domain.Plan;
import com.subscription.recovery.domain.Subscription;
import com.subscription.recovery.domain.SubscriptionStatus;
import com.subscription.recovery.gateway.PaymentGateway;
import com.subscription.recovery.gateway.PaymentResult;
import com.subscription.recovery.notification.NotificationService;
import com.subscription.recovery.repository.InvoiceRepository;
import com.subscription.recovery.repository.PaymentAttemptRepository;
import com.subscription.recovery.retry.BankDowntimeRetryStrategy;
import com.subscription.recovery.retry.LimitExceededRetryStrategy;
import com.subscription.recovery.retry.PaymentMethodUpdateStrategy;
import com.subscription.recovery.retry.ReminderType;
import com.subscription.recovery.retry.SalaryCycleRetryStrategy;
import com.subscription.recovery.retry.SmartRecoveryPolicy;
import com.subscription.recovery.risk.RiskScoringService;
import com.subscription.recovery.support.MutableClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests of the recovery engine's state transitions. Repositories, gateway and notifications are Mockito
 * mocks; the real risk scoring and smart policy are used so the test exercises real decisions.
 */
@ExtendWith(MockitoExtension.class)
class PaymentProcessorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 9, 0);

    @Mock InvoiceRepository invoices;
    @Mock PaymentAttemptRepository attempts;
    @Mock PaymentGateway gateway;
    @Mock NotificationService notifications;

    private PaymentProcessor processor;
    private Customer customer;
    private Subscription subscription;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        SmartRecoveryPolicy policy = new SmartRecoveryPolicy(List.of(new BankDowntimeRetryStrategy(),
                new SalaryCycleRetryStrategy(), new LimitExceededRetryStrategy(), new PaymentMethodUpdateStrategy()));
        processor = new PaymentProcessor(invoices, attempts, gateway, policy, new RiskScoringService(),
                notifications, new RecoveryProperties(RecoveryProperties.PolicyType.SMART, 28), new MutableClock(NOW));

        customer = new Customer("Ravi", "ravi@example.in", LocalDate.of(2023, 1, 1), 0);
        ReflectionTestUtils.setField(customer, "id", 1L);
        Plan plan = new Plan("Premium", new BigDecimal("499"), BillingCycle.MONTHLY);
        subscription = new Subscription(customer, plan, PaymentMethod.UPI_AUTOPAY, LocalDate.of(2026, 3, 14));
        ReflectionTestUtils.setField(subscription, "id", 10L);
        invoice = new Invoice(subscription, new BigDecimal("499"), LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 14));
        ReflectionTestUtils.setField(invoice, "id", 100L);
        when(invoices.findById(100L)).thenReturn(Optional.of(invoice));
    }

    @Test
    void firstFailureMovesSubscriptionToPastDueAndSchedulesSalaryDateRetry() {
        when(gateway.charge(any())).thenReturn(PaymentResult.failure(FailureReason.INSUFFICIENT_BALANCE, "r"));

        boolean paid = processor.attemptPayment(100L);

        assertThat(paid).isFalse();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(invoice.getNextRetryAt()).isEqualTo(LocalDateTime.of(2026, 4, 1, 10, 0));
        assertThat(invoice.getRecoveryDeadline()).isEqualTo(NOW.plusDays(28));
        assertThat(customer.getPastFailureCount()).isEqualTo(1);
        verify(attempts).save(any());
    }

    @Test
    void successfulRetryRecoversInvoiceAndReactivatesSubscription() {
        when(gateway.charge(any()))
                .thenReturn(PaymentResult.failure(FailureReason.BANK_DOWNTIME, "r1"))
                .thenReturn(PaymentResult.success("r2"));

        processor.attemptPayment(100L);
        boolean paid = processor.attemptPayment(100L);

        assertThat(paid).isTrue();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.RECOVERED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(invoice.getNextRetryAt()).isNull();
        verify(notifications).notify(customer, invoice, ReminderType.PAYMENT_RECOVERED);
    }

    @Test
    void hardDeclineAsksForNewPaymentMethodAndSchedulesNoRetry() {
        when(gateway.charge(any())).thenReturn(PaymentResult.failure(FailureReason.MANDATE_REVOKED, "r"));

        processor.attemptPayment(100L);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
        assertThat(invoice.getNextRetryAt()).isNull();
        assertThat(invoice.getNextReminderAt()).isEqualTo(NOW.plusDays(4));
        verify(notifications).notify(customer, invoice, ReminderType.UPDATE_PAYMENT_METHOD);
    }

    @Test
    void exhaustingRetriesWritesOffInvoiceAndCancelsSubscription() {
        when(gateway.charge(any())).thenReturn(PaymentResult.failure(FailureReason.BANK_DOWNTIME, "r"));

        // Low-risk customer: 1 scheduled charge + 4 retries, then give up.
        for (int i = 0; i < 5; i++) {
            processor.attemptPayment(100L);
        }

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.WRITTEN_OFF);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(notifications).notify(customer, invoice, ReminderType.SUBSCRIPTION_CANCELLED);
        assertThat(customer.getPastFailureCount()).as("counted once per invoice, not per attempt").isEqualTo(1);
    }

    @Test
    void settledInvoiceIsNeverChargedAgain() {
        when(gateway.charge(any())).thenReturn(PaymentResult.success("r"));
        processor.attemptPayment(100L);

        processor.attemptPayment(100L);

        verify(gateway, org.mockito.Mockito.times(1)).charge(any());
        verify(notifications, never()).notify(any(), any(), eq(ReminderType.PAYMENT_RECOVERED));
    }
}
