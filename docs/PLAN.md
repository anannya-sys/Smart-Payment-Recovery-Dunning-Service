# subscription-recovery-service: build plan

## Stack
Java 17 (compiled with `--release 17`), Spring Boot 3.5.16, Maven, Spring Web, Spring Data JPA,
PostgreSQL 16, Flyway (schema + indexes as reviewed SQL), Bean Validation, springdoc-openapi 2.8 (Swagger UI),
JUnit 5 + Mockito + AssertJ, Testcontainers (PostgreSQL), Docker + docker-compose.

## Folder structure
```
subscription-recovery-service/
├── pom.xml
├── Dockerfile                      # multi-stage build (Maven -> slim JRE)
├── docker-compose.yml              # app + postgres
├── README.md
└── src
    ├── main/java/com/subscription/recovery
    │   ├── SubscriptionRecoveryApplication.java
    │   ├── config/          # Clock, OpenAPI, @ConfigurationProperties (RecoveryProperties, BillingProperties)
    │   ├── controller/      # REST controllers only: HTTP <-> DTO, no business logic
    │   ├── dto/             # request/response records + mappers (entities never leave the service layer)
    │   ├── domain/          # JPA entities + enums (Customer, Plan, Subscription, Invoice, PaymentAttempt,
    │   │                    #   SubscriptionStatus state machine, InvoiceStatus, FailureReason, PaymentMethod)
    │   ├── repository/      # Spring Data JPA repositories + analytics projections
    │   ├── service/         # CustomerService, PlanService, SubscriptionService, BillingService,
    │   │                    #   RecoveryService (retry engine orchestration), AnalyticsService
    │   ├── gateway/         # PaymentGateway interface + SimulatedPaymentGateway (seeded, reproducible)
    │   ├── risk/            # RiskScoringService (rule-based 0-100 score with explanation)
    │   ├── retry/           # Strategy pattern: RetryStrategy + BankDowntime / SalaryCycle / LimitExceeded /
    │   │                    #   PaymentMethodUpdate strategies, SmartRecoveryPolicy, FixedIntervalRecoveryPolicy
    │   ├── notification/    # NotificationService + NotificationChannel (LoggingNotificationChannel today)
    │   ├── scheduler/       # BillingScheduler, RecoveryScheduler (@Scheduled, thin wrappers over services)
    │   ├── simulation/      # synthetic customers + in-memory event loop comparing smart vs fixed
    │   └── exception/       # GlobalExceptionHandler (@RestControllerAdvice) + custom exceptions
    ├── main/resources
    │   ├── application.yml
    │   └── db/migration/V1__init.sql   # tables, constraints, indexes (with comments)
    └── test/java/...                    # unit tests (strategies, state machine, risk, gateway, services)
                                         # + Testcontainers integration tests (API + billing/recovery flow)
```

## Build steps (build + tests run after each)
1. Project skeleton, domain entities, Flyway schema, repositories.
2. DTOs, services, CRUD controllers, validation, global exception handling, Swagger.
3. Simulated payment gateway, risk scoring, retry strategies (Strategy pattern), notification service.
4. Billing job, recovery (retry) job, subscription state machine, analytics endpoint.
5. Simulation (1,000 customers x 6 months, smart vs fixed baseline) as endpoint + startup command.
6. Tests (unit + Testcontainers), Dockerfile, docker-compose, README.
7. Run the full simulation and record results.

## Key design decisions (short)
- **Strategy pattern** for retries: one class per failure reason; `SmartRecoveryPolicy` picks the strategy and
  applies risk adjustments (fewer retries, earlier reminder for high-risk customers). The fixed baseline is
  another policy behind the same interface, so the simulation compares them fairly.
- **Explicit state machine** on `SubscriptionStatus` (allowed transitions in one place; illegal moves throw).
- **Idempotent billing**: unique constraint on (subscription_id, period_start) so a re-run job can't double-bill.
- **Reproducible gateway**: outcomes come from a seeded hash of (customer, time, attempt), not call order.
- **Injected `Clock`** (Asia/Kolkata) so scheduling and date logic are testable.
- **Flyway** instead of `ddl-auto=update`, so the schema and indexes are versioned and reviewable.
