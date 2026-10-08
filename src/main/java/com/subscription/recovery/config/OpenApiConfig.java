package com.subscription.recovery.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata shown at the top of Swagger UI (http://localhost:8080/swagger-ui.html). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI recoveryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Subscription Recovery Service API")
                .version("1.0.0")
                .description("Smart payment recovery (dunning) for subscription businesses in India: "
                        + "customers, plans, subscriptions, billing, smart retries, analytics and simulation."));
    }
}
