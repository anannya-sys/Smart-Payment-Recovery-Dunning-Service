package com.subscription.recovery.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a single {@link Clock} for the whole app.
 *
 * <p>Code never calls {@code LocalDate.now()} directly; it asks this clock. Tests can then pass a fixed clock
 * and assert exact retry times ("retry at 10:00 on the 1st") deterministically.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
