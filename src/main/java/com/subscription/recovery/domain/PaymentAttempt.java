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
import java.time.LocalDateTime;

/**
 * Immutable audit record of one charge attempt. Never updated after insert; together these rows are the
 * full payment history of an invoice, including <i>why</i> the engine chose what it did next.
 */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private FailureReason failureReason;

    @Column(name = "gateway_reference", length = 64)
    private String gatewayReference;

    /** Human-readable explanation of the decision taken after this attempt (retry when / remind / give up). */
    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(Invoice invoice, int attemptNumber, LocalDateTime attemptedAt, AttemptResult result,
                          FailureReason failureReason, String gatewayReference) {
        this.invoice = invoice;
        this.attemptNumber = attemptNumber;
        this.attemptedAt = attemptedAt;
        this.result = result;
        this.failureReason = failureReason;
        this.gatewayReference = gatewayReference;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public Long getId() {
        return id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }

    public AttemptResult getResult() {
        return result;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    public String getDecisionNote() {
        return decisionNote;
    }
}
