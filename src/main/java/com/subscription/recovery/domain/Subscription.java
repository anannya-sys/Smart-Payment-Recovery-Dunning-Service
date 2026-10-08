package com.subscription.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;

/**
 * A customer's subscription to a plan.
 *
 * <p>Status changes go through {@link #transitionTo(SubscriptionStatus)}, which enforces the
 * {@link SubscriptionStatus} state machine. There is deliberately no public status setter.
 *
 * <p>{@code @Version} enables optimistic locking: if the billing job and an API call update the same row
 * concurrently, one of them fails instead of silently overwriting the other.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: don't load customer/plan unless needed (avoids extra joins on every subscription query).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "next_billing_date", nullable = false)
    private LocalDate nextBillingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    /**
     * Incremented whenever the customer re-authorises their mandate or adds a new card. The gateway treats
     * each version as a different payment instrument, so a revoked mandate stays revoked until this changes.
     */
    @Column(name = "payment_method_version", nullable = false)
    private int paymentMethodVersion = 1;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Version
    private long version;

    protected Subscription() {
    }

    public Subscription(Customer customer, Plan plan, PaymentMethod paymentMethod, LocalDate startDate) {
        this.customer = customer;
        this.plan = plan;
        this.paymentMethod = paymentMethod;
        this.startDate = startDate;
        this.nextBillingDate = startDate;
        this.status = SubscriptionStatus.ACTIVE;
    }

    /** Moves to {@code target} if the state machine allows it, otherwise throws. */
    public void transitionTo(SubscriptionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(status, target);
        }
        this.status = target;
    }

    /** Called once an invoice has been created for the current period. */
    public void advanceBillingDate() {
        this.nextBillingDate = nextBillingDate.plusMonths(plan.getBillingCycle().months());
    }

    /** Customer re-authorised the mandate / added a new card. */
    public void updatePaymentMethod(PaymentMethod newMethod) {
        this.paymentMethod = newMethod;
        this.paymentMethodVersion++;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Plan getPlan() {
        return plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public LocalDate getNextBillingDate() {
        return nextBillingDate;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public int getPaymentMethodVersion() {
        return paymentMethodVersion;
    }

    public LocalDate getStartDate() {
        return startDate;
    }
}
