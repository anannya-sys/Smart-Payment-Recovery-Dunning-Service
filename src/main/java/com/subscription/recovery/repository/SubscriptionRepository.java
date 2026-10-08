package com.subscription.recovery.repository;

import com.subscription.recovery.domain.Subscription;
import com.subscription.recovery.domain.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * IDs of subscriptions the billing job must bill. Served by idx_subscriptions_status_next_billing.
     * We fetch IDs (not entities) so each subscription can be billed in its own small transaction.
     */
    @Query("""
            select s.id from Subscription s
            where s.status = :status and s.nextBillingDate <= :today
            order by s.id""")
    List<Long> findIdsDueForBilling(@Param("status") SubscriptionStatus status, @Param("today") LocalDate today);

    /** Loads customer + plan in the same query (avoids the N+1 problem when mapping to DTOs). */
    @EntityGraph(attributePaths = {"customer", "plan"})
    Optional<Subscription> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"customer", "plan"})
    Page<Subscription> findByStatus(SubscriptionStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "plan"})
    Page<Subscription> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "plan"})
    List<Subscription> findByCustomerId(Long customerId);

    long countByStatus(SubscriptionStatus status);

    /**
     * Monthly recurring revenue: monthly price of every subscription that is still billed. PAST_DUE counts too,
     * because that revenue is what recovery is trying to keep.
     */
    @Query("""
            select coalesce(sum(p.monthlyPrice), 0) from Subscription s join s.plan p
            where s.status in :statuses""")
    BigDecimal sumMonthlyPriceByStatusIn(@Param("statuses") Collection<SubscriptionStatus> statuses);
}
