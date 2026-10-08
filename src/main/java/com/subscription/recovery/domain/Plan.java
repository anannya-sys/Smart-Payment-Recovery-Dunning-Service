package com.subscription.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A subscription plan, e.g. "Premium" at INR 499/month.
 *
 * <p>Money is always {@link BigDecimal} (never double) to avoid floating-point rounding errors.
 */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(name = "monthly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Column(nullable = false)
    private boolean active = true;

    protected Plan() {
    }

    public Plan(String name, BigDecimal monthlyPrice, BillingCycle billingCycle) {
        this.name = name;
        this.monthlyPrice = monthlyPrice;
        this.billingCycle = billingCycle;
    }

    /** Amount charged per billing cycle (monthly price x months in the cycle). */
    public BigDecimal pricePerCycle() {
        return monthlyPrice.multiply(BigDecimal.valueOf(billingCycle.months()));
    }

    public void update(String name, BigDecimal monthlyPrice, BillingCycle billingCycle, boolean active) {
        this.name = name;
        this.monthlyPrice = monthlyPrice;
        this.billingCycle = billingCycle;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public boolean isActive() {
        return active;
    }
}
