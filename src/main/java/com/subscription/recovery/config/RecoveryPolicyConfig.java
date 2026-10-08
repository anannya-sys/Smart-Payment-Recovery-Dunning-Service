package com.subscription.recovery.config;

import com.subscription.recovery.retry.FixedIntervalRecoveryPolicy;
import com.subscription.recovery.retry.RecoveryPolicy;
import com.subscription.recovery.retry.SmartRecoveryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Chooses which recovery policy the live service uses ({@code recovery.policy=SMART|FIXED}). */
@Configuration
public class RecoveryPolicyConfig {

    @Bean
    @Primary
    public RecoveryPolicy activeRecoveryPolicy(RecoveryProperties props, SmartRecoveryPolicy smart,
                                               FixedIntervalRecoveryPolicy fixed) {
        return props.policy() == RecoveryProperties.PolicyType.SMART ? smart : fixed;
    }
}
