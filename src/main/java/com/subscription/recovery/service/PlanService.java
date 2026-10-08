package com.subscription.recovery.service;

import com.subscription.recovery.domain.Plan;
import com.subscription.recovery.dto.DtoMapper;
import com.subscription.recovery.dto.PlanRequest;
import com.subscription.recovery.dto.PlanResponse;
import com.subscription.recovery.exception.ConflictException;
import com.subscription.recovery.exception.ResourceNotFoundException;
import com.subscription.recovery.repository.PlanRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlanService {

    private final PlanRepository plans;

    public PlanService(PlanRepository plans) {
        this.plans = plans;
    }

    @Transactional
    public PlanResponse create(PlanRequest req) {
        if (plans.existsByNameIgnoreCase(req.name())) {
            throw new ConflictException("A plan named " + req.name() + " already exists");
        }
        Plan plan = new Plan(req.name().trim(), req.monthlyPrice(), req.billingCycle());
        if (Boolean.FALSE.equals(req.active())) {
            plan.update(plan.getName(), plan.getMonthlyPrice(), plan.getBillingCycle(), false);
        }
        return DtoMapper.toResponse(plans.save(plan));
    }

    public PlanResponse get(Long id) {
        return DtoMapper.toResponse(find(id));
    }

    public List<PlanResponse> list() {
        return plans.findAll(Sort.by("monthlyPrice")).stream().map(DtoMapper::toResponse).toList();
    }

    /** Price changes apply from the next invoice; already-issued invoices keep their amount. */
    @Transactional
    public PlanResponse update(Long id, PlanRequest req) {
        Plan plan = find(id);
        boolean active = req.active() == null ? plan.isActive() : req.active();
        plan.update(req.name().trim(), req.monthlyPrice(), req.billingCycle(), active);
        return DtoMapper.toResponse(plan);
    }

    Plan find(Long id) {
        return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("Plan", id));
    }
}
