package com.subscription.recovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the subscription payment-recovery (dunning) service.
 *
 * <p>{@code @EnableScheduling} turns on the billing and recovery cron jobs, and
 * {@code @ConfigurationPropertiesScan} binds the typed {@code recovery.*} / {@code billing.*} settings.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class SubscriptionRecoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionRecoveryApplication.class, args);
    }
}
