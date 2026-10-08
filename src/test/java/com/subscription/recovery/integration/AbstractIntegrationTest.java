package com.subscription.recovery.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests: boots the full Spring context against a <b>real PostgreSQL</b> started by
 * Testcontainers, so Flyway migrations, partial indexes and JPQL queries are tested exactly as in production.
 *
 * <p>Singleton container pattern: one container is started for the whole test run and shared by all subclasses
 * (much faster than one per class). {@code @ServiceConnection} wires its JDBC URL into Spring automatically.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
