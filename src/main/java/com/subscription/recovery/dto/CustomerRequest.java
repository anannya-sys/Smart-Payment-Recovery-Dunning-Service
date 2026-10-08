package com.subscription.recovery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body of POST /api/customers. */
public record CustomerRequest(
        @NotBlank @Size(max = 120) @Schema(example = "Priya Sharma") String name,
        @NotBlank @Email @Size(max = 254) @Schema(example = "priya@example.in") String email,
        @NotNull @PastOrPresent @Schema(example = "2025-01-15") LocalDate signupDate,
        @Min(0) @Max(1000) @Schema(description = "Failures before onboarding (e.g. migrated data)", example = "0")
        Integer pastFailureCount) {
}
