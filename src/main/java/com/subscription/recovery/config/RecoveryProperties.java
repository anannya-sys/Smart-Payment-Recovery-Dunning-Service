package com.subscription.recovery.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed binding of the {@code recovery.*} settings (validated at startup, so a bad config fails fast).
 *
 * @param policy             which recovery policy the live service uses
 * @param recoveryWindowDays days after the first failure before we give up and write the invoice off
 */
@Validated
@ConfigurationProperties(prefix = "recovery")
public record RecoveryProperties(@NotNull PolicyType policy, @Min(1) int recoveryWindowDays) {

    public enum PolicyType { SMART, FIXED }
}
