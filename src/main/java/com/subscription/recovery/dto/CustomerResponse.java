package com.subscription.recovery.dto;

import java.time.LocalDate;

public record CustomerResponse(
        Long id,
        String name,
        String email,
        LocalDate signupDate,
        int tenureMonths,
        int pastFailureCount) {
}
