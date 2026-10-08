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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One bill for one billing period of a subscription.
 *
 * <p>Besides the amount and status, the invoice carries the <b>recovery state</b> the retry engine needs:
 * when to retry next ({@link #nextRetryAt}), when to send a follow-up reminder ({@link #nextReminderAt})
 * and the date after which we stop trying ({@link #recoveryDeadline}). Keeping this on the invoice row means
 * the recovery job is a simple indexed query ("FAILED invoices whose next retry is due") and survives restarts.
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** First day of the period this invoice covers. Unique per subscription => billing is idempotent. */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.PENDING;

    /** Reason for the very first failure; used for analytics ("which failures do we recover best?"). */
    @Enumerated(EnumType.STRING)
    @Column(name = "initial_failure_reason", length = 30)
    private FailureReason initialFailureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_failure_reason", length = 30)
    private FailureReason lastFailureReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "next_reminder_at")
    private LocalDateTime nextReminderAt;

    @Column(name = "recovery_deadline")
    private LocalDateTime recoveryDeadline;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Version
    private long version;

    protected Invoice() {
    }

    public Invoice(Subscription subscription, BigDecimal amount, LocalDate periodStart, LocalDate dueDate) {
        this.subscription = subscription;
        this.amount = amount;
        this.periodStart = periodStart;
        this.dueDate = dueDate;
    }

    /** Next attempt number (1 for the first charge, 2 for the first retry, ...). */
    public int nextAttemptNumber() {
        return attemptCount + 1;
    }

    /** Successful charge: PAID if it worked first time, RECOVERED if it took a retry. */
    public void markPaid(LocalDateTime when) {
        attemptCount++;
        status = (status == InvoiceStatus.FAILED) ? InvoiceStatus.RECOVERED : InvoiceStatus.PAID;
        paidAt = when;
        clearSchedule();
    }

    /** Failed charge. The first failure also starts the recovery window. */
    public void markFailed(FailureReason reason, LocalDateTime deadlineIfFirstFailure) {
        attemptCount++;
        if (initialFailureReason == null) {
            initialFailureReason = reason;
            recoveryDeadline = deadlineIfFirstFailure;
        }
        lastFailureReason = reason;
        status = InvoiceStatus.FAILED;
    }

    public void scheduleRetry(LocalDateTime when) {
        this.nextRetryAt = when;
    }

    public void scheduleReminder(LocalDateTime when) {
        this.nextReminderAt = when;
    }

    public void writeOff() {
        status = InvoiceStatus.WRITTEN_OFF;
        clearSchedule();
    }

    private void clearSchedule() {
        nextRetryAt = null;
        nextReminderAt = null;
    }

    public Long getId() {
        return id;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public FailureReason getInitialFailureReason() {
        return initialFailureReason;
    }

    public FailureReason getLastFailureReason() {
        return lastFailureReason;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public LocalDateTime getNextReminderAt() {
        return nextReminderAt;
    }

    public LocalDateTime getRecoveryDeadline() {
        return recoveryDeadline;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }
}
