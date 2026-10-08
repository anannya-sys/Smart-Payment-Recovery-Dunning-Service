package com.subscription.recovery.service;

import com.subscription.recovery.domain.Customer;
import com.subscription.recovery.domain.Invoice;
import com.subscription.recovery.domain.InvoiceStatus;
import com.subscription.recovery.domain.Plan;
import com.subscription.recovery.domain.Subscription;
import com.subscription.recovery.domain.SubscriptionStatus;
import com.subscription.recovery.dto.DtoMapper;
import com.subscription.recovery.dto.InvoiceResponse;
import com.subscription.recovery.dto.PageResponse;
import com.subscription.recovery.dto.PaymentMethodUpdateRequest;
import com.subscription.recovery.dto.StatusChangeRequest;
import com.subscription.recovery.dto.SubscriptionRequest;
import com.subscription.recovery.dto.SubscriptionResponse;
import com.subscription.recovery.exception.BusinessRuleException;
import com.subscription.recovery.exception.ResourceNotFoundException;
import com.subscription.recovery.repository.InvoiceRepository;
import com.subscription.recovery.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptions;
    private final InvoiceRepository invoices;
    private final CustomerService customerService;
    private final PlanService planService;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository subscriptions, InvoiceRepository invoices,
                               CustomerService customerService, PlanService planService, Clock clock) {
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.customerService = customerService;
        this.planService = planService;
        this.clock = clock;
    }

    @Transactional
    public SubscriptionResponse create(SubscriptionRequest req) {
        Customer customer = customerService.find(req.customerId());
        Plan plan = planService.find(req.planId());
        if (!plan.isActive()) {
            throw new BusinessRuleException("Plan " + plan.getName() + " is not open for new subscriptions");
        }
        LocalDate start = req.startDate() == null ? LocalDate.now(clock) : req.startDate();
        Subscription saved = subscriptions.save(new Subscription(customer, plan, req.paymentMethod(), start));
        return DtoMapper.toResponse(saved);
    }

    public SubscriptionResponse get(Long id) {
        return DtoMapper.toResponse(find(id));
    }

    public PageResponse<SubscriptionResponse> list(SubscriptionStatus status, Pageable pageable) {
        var page = status == null ? subscriptions.findAllBy(pageable) : subscriptions.findByStatus(status, pageable);
        return PageResponse.of(page, DtoMapper::toResponse);
    }

    /**
     * Manual status change by an operator / the customer. Only PAUSED, ACTIVE (resume) and CANCELLED are
     * accepted; whether the move is legal is decided by the state machine on the entity.
     */
    @Transactional
    public SubscriptionResponse changeStatus(Long id, StatusChangeRequest req) {
        Subscription sub = find(id);
        SubscriptionStatus target = req.status();
        if (target == SubscriptionStatus.PAST_DUE) {
            throw new BusinessRuleException("PAST_DUE is set by the billing engine, not by API clients");
        }
        if (target == SubscriptionStatus.ACTIVE && sub.getStatus() == SubscriptionStatus.PAST_DUE) {
            throw new BusinessRuleException("A past-due subscription becomes ACTIVE only when its invoice is paid");
        }
        sub.transitionTo(target);
        log.info("Subscription {} moved to {} via API", id, target);
        return DtoMapper.toResponse(sub);
    }

    /**
     * The customer re-authorised their UPI mandate or added a new card. The new instrument gets a new version,
     * and every FAILED invoice is scheduled for an immediate retry: this is how hard declines recover.
     */
    @Transactional
    public SubscriptionResponse updatePaymentMethod(Long id, PaymentMethodUpdateRequest req) {
        Subscription sub = find(id);
        if (sub.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new BusinessRuleException("Subscription is cancelled");
        }
        sub.updatePaymentMethod(req.paymentMethod());
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        for (Invoice invoice : invoices.findBySubscriptionIdAndStatus(id, InvoiceStatus.FAILED)) {
            invoice.scheduleRetry(now);
        }
        return DtoMapper.toResponse(sub);
    }

    public List<InvoiceResponse> invoices(Long subscriptionId) {
        find(subscriptionId);
        return invoices.findBySubscriptionIdOrderByDueDateDesc(subscriptionId).stream()
                .map(i -> DtoMapper.toResponse(i, null))
                .toList();
    }

    private Subscription find(Long id) {
        return subscriptions.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id));
    }
}
