package com.subscription.recovery.repository;

import com.subscription.recovery.domain.Invoice;
import com.subscription.recovery.domain.InvoiceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    boolean existsBySubscriptionIdAndPeriodStart(Long subscriptionId, LocalDate periodStart);

    List<Invoice> findBySubscriptionIdOrderByDueDateDesc(Long subscriptionId);

    List<Invoice> findBySubscriptionIdAndStatus(Long subscriptionId, InvoiceStatus status);

    /** Invoice list for the dashboard; loads subscription, customer and plan in the same query (no N+1). */
    @EntityGraph(attributePaths = {"subscription", "subscription.customer", "subscription.plan"})
    Page<Invoice> findByStatusIn(Collection<InvoiceStatus> statuses, Pageable pageable);

    long countByStatus(InvoiceStatus status);

    @Query("select coalesce(sum(i.amount), 0) from Invoice i where i.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") InvoiceStatus status);

    /** FAILED invoices whose retry is due. Served by the partial index idx_invoices_retry_due. */
    @Query("select i.id from Invoice i where i.status = 'FAILED' and i.nextRetryAt <= :now order by i.nextRetryAt")
    List<Long> findIdsWithRetryDue(@Param("now") LocalDateTime now);

    @Query("select i.id from Invoice i where i.status = 'FAILED' and i.nextReminderAt <= :now")
    List<Long> findIdsWithReminderDue(@Param("now") LocalDateTime now);

    @Query("select i.id from Invoice i where i.status = 'FAILED' and i.recoveryDeadline <= :now")
    List<Long> findIdsPastRecoveryDeadline(@Param("now") LocalDateTime now);

    /** PENDING invoices left behind by a crash between "create invoice" and "charge". */
    @Query("select i.id from Invoice i where i.status = 'PENDING'")
    List<Long> findPendingIds();

    /**
     * Analytics: one row per initial failure reason, aggregated in the database (not in Java), so we never
     * load thousands of invoices into memory just to sum them.
     */
    @Query("""
            select new com.subscription.recovery.repository.FailureReasonAggregate(
                i.initialFailureReason,
                count(i),
                sum(i.amount),
                sum(case when i.status = com.subscription.recovery.domain.InvoiceStatus.RECOVERED then 1 else 0 end),
                coalesce(sum(case when i.status = com.subscription.recovery.domain.InvoiceStatus.RECOVERED
                                  then i.amount else 0 end), 0),
                sum(case when i.status = com.subscription.recovery.domain.InvoiceStatus.FAILED then 1 else 0 end))
            from Invoice i
            where i.initialFailureReason is not null and i.dueDate between :from and :to
            group by i.initialFailureReason
            order by sum(i.amount) desc""")
    List<FailureReasonAggregate> aggregateFailuresByReason(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
