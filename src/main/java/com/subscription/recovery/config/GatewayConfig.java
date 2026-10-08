package com.subscription.recovery.config;

import com.subscription.recovery.gateway.PaymentGateway;
import com.subscription.recovery.gateway.SimulatedPaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the payment gateway. To go live, return a real provider's adapter here instead. */
@Configuration
public class GatewayConfig {

    @Bean
    public PaymentGateway paymentGateway(@Value("${gateway.seed:42}") long seed) {
        return SimulatedPaymentGateway.withDerivedProfiles(seed);
    }
}
