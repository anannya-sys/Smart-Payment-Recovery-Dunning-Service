package com.subscription.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A paying customer.
 *
 * <p>Tenure is <b>derived</b> from {@link #signupDate} instead of being stored, so it can never go stale.
 * {@link #pastFailureCount} is incremented every time one of the customer's invoices fails, and feeds the
 * churn-risk score.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "signup_date", nullable = false)
    private LocalDate signupDate;

    @Column(name = "past_failure_count", nullable = false)
    private int pastFailureCount;

    /** Required by JPA. */
    protected Customer() {
    }

    public Customer(String name, String email, LocalDate signupDate, int pastFailureCount) {
        this.name = name;
        this.email = email;
        this.signupDate = signupDate;
        this.pastFailureCount = pastFailureCount;
    }

    /** Whole months between signup and {@code today}. */
    public int tenureMonths(LocalDate today) {
        return (int) Math.max(0, ChronoUnit.MONTHS.between(signupDate, today));
    }

    public void recordPaymentFailure() {
        pastFailureCount++;
    }

    public void updateDetails(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getSignupDate() {
        return signupDate;
    }

    public int getPastFailureCount() {
        return pastFailureCount;
    }
}
