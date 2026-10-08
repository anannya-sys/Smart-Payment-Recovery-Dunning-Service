package com.subscription.recovery.service;

import com.subscription.recovery.domain.Customer;
import com.subscription.recovery.domain.Subscription;
import com.subscription.recovery.domain.SubscriptionStatus;
import com.subscription.recovery.dto.CustomerRequest;
import com.subscription.recovery.dto.CustomerResponse;
import com.subscription.recovery.dto.CustomerUpdateRequest;
import com.subscription.recovery.dto.DtoMapper;
import com.subscription.recovery.dto.PageResponse;
import com.subscription.recovery.dto.RiskAssessmentResponse;
import com.subscription.recovery.exception.ConflictException;
import com.subscription.recovery.exception.ResourceNotFoundException;
import com.subscription.recovery.repository.CustomerRepository;
import com.subscription.recovery.repository.SubscriptionRepository;
import com.subscription.recovery.risk.RiskAssessment;
import com.subscription.recovery.risk.RiskScoringService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer use cases. Transactions are declared here (service layer), never in controllers. */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;
    private final SubscriptionRepository subscriptions;
    private final RiskScoringService riskScoring;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, SubscriptionRepository subscriptions,
                           RiskScoringService riskScoring, Clock clock) {
        this.customers = customers;
        this.subscriptions = subscriptions;
        this.riskScoring = riskScoring;
        this.clock = clock;
    }

    @Transactional
    public CustomerResponse create(CustomerRequest req) {
        if (customers.existsByEmailIgnoreCase(req.email())) {
            throw new ConflictException("A customer with email " + req.email() + " already exists");
        }
        int failures = req.pastFailureCount() == null ? 0 : req.pastFailureCount();
        Customer saved = customers.save(new Customer(req.name().trim(), req.email().toLowerCase(),
                req.signupDate(), failures));
        return DtoMapper.toResponse(saved, today());
    }

    public CustomerResponse get(Long id) {
        return DtoMapper.toResponse(find(id), today());
    }

    public PageResponse<CustomerResponse> list(Pageable pageable) {
        LocalDate today = today();
        return PageResponse.of(customers.findAll(pageable), c -> DtoMapper.toResponse(c, today));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerUpdateRequest req) {
        Customer customer = find(id);
        if (customers.existsByEmailIgnoreCaseAndIdNot(req.email(), id)) {
            throw new ConflictException("A customer with email " + req.email() + " already exists");
        }
        // Dirty checking: no explicit save() needed, Hibernate flushes the change at commit.
        customer.updateDetails(req.name().trim(), req.email().toLowerCase());
        return DtoMapper.toResponse(customer, today());
    }

    /**
     * Churn-risk score. Price is taken from the customer's most expensive non-cancelled subscription,
     * because that is the debit most likely to fail.
     */
    public RiskAssessmentResponse risk(Long id) {
        Customer customer = find(id);
        BigDecimal price = subscriptions.findByCustomerId(id).stream()
                .filter(s -> s.getStatus() != SubscriptionStatus.CANCELLED)
                .map(Subscription::getPlan)
                .map(p -> p.getMonthlyPrice())
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        RiskAssessment r = riskScoring.assess(customer.tenureMonths(today()), customer.getPastFailureCount(), price);
        return new RiskAssessmentResponse(id, r.score(), r.band(), r.factors());
    }

    Customer find(Long id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
