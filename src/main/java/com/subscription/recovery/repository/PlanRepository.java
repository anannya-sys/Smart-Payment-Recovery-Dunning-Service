package com.subscription.recovery.repository;

import com.subscription.recovery.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    boolean existsByNameIgnoreCase(String name);
}
