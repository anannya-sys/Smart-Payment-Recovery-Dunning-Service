# Interview Guide: subscription-recovery-service

## The 30-second pitch

> "I built a payment-recovery service for Indian subscription businesses. When a UPI AutoPay or card renewal fails,
> most systems just retry every few days. My service looks at **why** the payment failed and **who** the customer
> is. Bank downtime gets retried within hours, low balance is retried around salary dates, a revoked mandate gets
> no blind retries but an 'update your payment method' message, and risky customers get fewer retries and earlier
> reminders. I built a simulation that replays six months of billing for 1,000 synthetic customers. Smart retries
> recovered 86% of failed revenue versus 76% for a fixed schedule, with 31% fewer retries. Across ten random seeds
> the lift was 3.5 to 9 points. It's Spring Boot 3, JPA on PostgreSQL, Flyway, schedulers, the Strategy pattern,
> 61 tests including Testcontainers integration tests, and a web dashboard to run and watch it."

## How the system works, in plain English

**The business problem.** A subscription renews every month. On the renewal date we try to collect money. Sometimes
the bank says no. If we give up, the customer is cancelled even though they never meant to leave. That's called
*involuntary churn*, and recovering those payments is called *dunning*.

**A day in the life of one subscription.**

1. *09:00 IST, billing job.* The scheduler wakes up and asks the database for every ACTIVE subscription whose next
   billing date is today. For each one it creates an invoice, moves the billing date forward a month and asks the
   payment gateway to charge it.
2. *The charge fails* with, say, `INSUFFICIENT_BALANCE`. The invoice becomes `FAILED`, the subscription becomes
   `PAST_DUE`, and the customer's failure count goes up. A 28-day deadline starts.
3. *Risk scoring.* We score the customer 0-100 from tenure, past failures and plan price. New customer + many
   failures + expensive plan = high risk.
4. *Choosing a strategy.* The smart policy looks up the strategy for this failure reason. For low balance it picks
   the next 1st or 5th of the month at 10am, because that's when salaries land. It writes that time into the
   invoice's `next_retry_at` column and records *why* in the payment attempt's decision note.
5. *Every 15 minutes, recovery job.* It asks "which failed invoices have a retry due now?", "which reminders are
   due?", "which deadlines have passed?" and handles each one.
6. *Retry succeeds.* The invoice becomes `RECOVERED`, the subscription goes back to `ACTIVE`, and the customer gets
   a "thank you" message. If the deadline passes or retries run out first, the invoice is `WRITTEN_OFF` and the
   subscription is `CANCELLED`.

**Hard declines are different.** If the mandate was revoked or the card expired, retrying the same instrument can
never work. So we don't retry. We message the customer, follow up once, and when they update their payment method
through the API we retry immediately on the new instrument.

**The simulated gateway.** There is no real bank, so `SimulatedPaymentGateway` pretends to be one. Each customer
has hidden traits: salary day, how quickly they run out of money, which bank they use, and so on. The gateway
decides each charge from those traits using a seeded hash, so the same inputs always give the same answer.

**The simulation.** It creates 1,000 customers, then runs six months of billing twice, once per policy, on the same
customers and seed. Instead of ticking through every hour, it keeps a priority queue of upcoming events (bill,
retry, reminder, customer acts, deadline) and jumps straight to the next one. That's a *discrete-event simulation*,
and it runs in about a tenth of a second.

**Layers.** Controller (HTTP) → Service (business logic, transactions) → Repository (database) → Domain (entities).
DTOs are what the API sends and receives; entities never leave the service layer.

## What to emphasise

* **Engineering judgment:** idempotent billing via a unique constraint, one transaction per invoice, optimistic
  locking, partial indexes, an injected `Clock` for testability, Flyway instead of auto-DDL.
* **Honesty about the simulation:** it is a model, the assumptions are written down, and you checked several seeds.
  Interviewers trust candidates who state limitations before being asked.
* **Trade-offs:** smart recovery is slower on average (7.4 vs 4.6 days) because it waits for payday, but it
  recovers more money with fewer retries.

---

## 15 likely interview questions

### Spring Boot

**1. What does `@SpringBootApplication` do, and how did you organise the application?**

It combines `@Configuration`, `@EnableAutoConfiguration` and `@ComponentScan`. Auto-configuration looks at the
classpath (Spring MVC, JPA, the PostgreSQL driver, Flyway) and sets up sensible beans like the `DataSource`,
`EntityManagerFactory` and the embedded Tomcat. Component scan finds my `@RestController`, `@Service` and
`@Component` classes under `com.subscription.recovery`. I also added `@EnableScheduling` for the cron jobs and
`@ConfigurationPropertiesScan` to bind the typed `RecoveryProperties` record. The code is layered (controller,
service, repository, domain, dto) with separate packages for the gateway, risk scoring, retry strategies,
notifications and the simulation.

**2. Why constructor injection everywhere instead of `@Autowired` on fields?**

Dependencies become `final`, so the object is always fully built and immutable. The dependencies are visible in the
constructor signature, so too many of them is an obvious smell. And it makes unit tests trivial: in
`PaymentProcessorTest` I just call `new PaymentProcessor(mockRepo, mockGateway, ...)` with Mockito mocks, no Spring
context needed. Since Spring 4.3 a single constructor needs no `@Autowired`.

**3. How do you handle validation and errors?**

Request DTOs are Java records with Bean Validation annotations (`@NotBlank`, `@Email`, `@PastOrPresent`,
`@DecimalMin`), and controllers take `@Valid @RequestBody`. A single `@RestControllerAdvice`
(`GlobalExceptionHandler`) maps exceptions to HTTP codes with one JSON shape (`ApiError`): validation → 400 with
every bad field, not found → 404, duplicate email or illegal state transition → 409, business rule (e.g. inactive
plan) → 422, anything unexpected → 500 with the stack trace logged but not leaked. Controllers contain no try/catch.

**4. Why DTOs instead of returning entities?**

Three reasons. *Decoupling:* I can change a column without breaking API clients. *Safety:* entities have lazy
relations; serialising them outside a transaction throws `LazyInitializationException` or triggers surprise
queries, and with `open-in-view: false` that's exactly what would happen. *Security:* clients can't mass-assign
fields like `status` or `pastFailureCount`. The mapping lives in one `DtoMapper` class.

### JPA / Hibernate / database

**5. Explain the N+1 problem and how you avoided it.**

If I load 20 subscriptions and then touch `subscription.getCustomer()` on each, Hibernate runs 1 query for the list
plus 20 more for customers: N+1. My relations are `FetchType.LAZY` (so nothing loads unless needed), and the
repository methods that feed DTOs use `@EntityGraph(attributePaths = {"customer", "plan"})`, which fetches them in
the same query with a join. For analytics I don't load entities at all: a JPQL `GROUP BY` with a constructor
expression returns one small `FailureReasonAggregate` record per failure reason.

**6. How do transactions work here, and what's the self-invocation trap?**

`@Transactional` is implemented with a proxy around the bean. If a method calls another `@Transactional` method on
`this`, the call skips the proxy, so no new transaction starts. I wanted each invoice processed in its own
transaction, so one failure doesn't roll back the whole batch and row locks are short. So `BillingService` (not
transactional) loops over IDs and calls separate beans, `InvoiceIssuer.issue()` and
`PaymentProcessor.attemptPayment()`, each `@Transactional`. Read-only services use `@Transactional(readOnly = true)`,
which lets Hibernate skip dirty checking.

**7. What happens if the billing job runs twice, or on two servers at once?**

Billing is idempotent at three levels. The query only picks `ACTIVE` subscriptions with `next_billing_date <= today`,
and issuing an invoice moves that date forward. Before inserting I check `existsBySubscriptionIdAndPeriodStart`. And
the real guarantee is a database `UNIQUE (subscription_id, period_start)` constraint: even if two instances race past
the check, the second insert fails. Entities also carry `@Version` for optimistic locking, so two writers can't
silently overwrite each other. In production I'd add ShedLock so only one instance runs the job at all.

**8. Which indexes did you create and why?**

Each index matches a query. `(status, next_billing_date)` for the billing job: the equality column comes first, so
Postgres can seek straight to `ACTIVE` and range-scan the dates. The retry job uses a **partial index**,
`(next_retry_at) WHERE status = 'FAILED'`. Most invoices are paid, so this index only holds the few failed ones and
stays small however big the table gets. Analytics has a partial index on `(initial_failure_reason, due_date)`.
Postgres doesn't index foreign keys automatically, so I added one on `subscriptions.customer_id`. The unique
constraints double as indexes. I used Flyway so these are reviewed SQL, not whatever `ddl-auto` generates.

### Scheduling

**9. How do the scheduled jobs work, and what are their limitations?**

`@EnableScheduling` plus `@Scheduled(cron = "${billing.cron}", zone = "Asia/Kolkata")`. Billing runs daily at 09:00
IST; recovery runs every 15 minutes. The schedulers are thin: they just call `BillingService` and `RecoveryService`,
which are also exposed through admin endpoints and called directly in tests. In tests the cron is set to `"-"`,
which disables it. The design is stateless: all scheduling state (`next_retry_at`, `recovery_deadline`) is in the
database, so a restart loses nothing. Limitations: Spring's default scheduler has one thread, so a slow job delays
others, and every app instance runs every job. Fixes are a configured thread pool, ShedLock or Quartz for
cluster-wide locking, and for very large volumes a queue (SQS/Kafka) with workers.

**10. How do you test time-dependent logic like "retry on the 1st at 10am"?**

No code calls `LocalDateTime.now()` directly; everything asks an injected `java.time.Clock` bean. Strategy unit tests
are pure functions: give a failure time, assert the exact retry time. Integration tests replace the clock with a
`MutableClock`: run billing on 14 March, assert the retry is scheduled for 1 April 10:00, move the clock to 31 March
(nothing happens), then to 1 April (invoice recovered). No `Thread.sleep`, fully deterministic.

### Design patterns

**11. Where did you use the Strategy pattern, and why not a `switch`?**

`RetryStrategy` is the interface, with `BankDowntimeRetryStrategy`, `SalaryCycleRetryStrategy`,
`LimitExceededRetryStrategy` and `PaymentMethodUpdateStrategy` as implementations. `SmartRecoveryPolicy` is the
context: Spring injects all strategies as a `List`, and it indexes them in an `EnumMap<FailureReason, RetryStrategy>`.
Compared with a big `switch`, each rule set is small, separately testable and can change without touching the
others (Open/Closed principle). The constructor also fails fast if a `FailureReason` has no strategy, so a new enum
value can't silently go unhandled at 3am.

**12. What other patterns or principles are in the code?**

* *Ports and adapters / Dependency Inversion:* the service depends on the `PaymentGateway` interface; the simulator
  is one adapter, and Razorpay would be another.
* *State machine:* `SubscriptionStatus` holds the table of allowed transitions; `Subscription.transitionTo()`
  enforces it and there's no public setter.
* *Template/policy interface:* `RecoveryPolicy` with smart and fixed implementations, which is what makes the A/B
  simulation possible.
* *Observer-like fan-out:* `NotificationService` sends to every `NotificationChannel` bean, so adding SMS is a new
  class.
* *Factory methods* on `RetryDecision` (`retryAt`, `awaitCustomer`, `giveUp`) and immutable records for value
  objects.

### The retry logic

**13. Walk me through the retry rules and the reasoning behind each.**

* *Bank downtime:* retry after 2h, 4h, 8h. Outages are short and not the customer's fault, so no message.
* *Insufficient balance:* the balance only changes when salary arrives, so retrying tomorrow just fails again and
  sends the customer another "debit failed" SMS. Retry on the next 1st or 5th at 10am. From the second failure,
  send a heads-up the day before.
* *Limit exceeded:* UPI and card limits reset at midnight, so retry the next morning.
* *Mandate revoked / card expired:* hard declines. Retrying can never work, so ask the customer to update the payment
  method, follow up once, and retry the moment they do.
* *Risk overlay:* retry budget is 4/3/2 for low/medium/high risk. High-risk customers get a reminder on the first
  soft failure and a sooner hard-decline follow-up, because every extra failed debit pushes a wavering customer to
  cancel.

Every decision is stored as a sentence on the payment attempt, so support can answer "why did you charge me on the
1st?".

**14. How is the risk score calculated, and why not machine learning?**

`score = tenure points (35/20/10/0) + 12 per past failure (max 40) + price points (25/15/8/0)`, capped at 100. Bands
are LOW < 35, MEDIUM 35-64, HIGH ≥ 65. It's rule-based on purpose. With no historical data there is nothing to train
on. It is explainable: the API returns the factors, e.g. "3 past payment failures: +36". And it's easy to test.
Because it sits behind `RiskScoringService`, an ML model could replace it later, and the simulation gives a way to
compare the two before going live.

**15. How do you know smart retries are actually better? What are the simulation's weaknesses?**

The simulation runs both policies on the same 1,000 customers with the same seed, so the policy is the only thing
that changes. For seed 42: 86.1% vs 76.3% of failed revenue recovered, 1,778 vs 2,573 retries, and 807 vs 701
active subscriptions after six months. One seed could be lucky, so I ran seeds 1-10: smart won every time by 3.5
to 9 points, mean 6.6. The weakness is that it is a **model**. Salary days, how quickly accounts run dry, how
customers respond to reminders and "retry fatigue" cancellations are assumptions I chose. They're listed in the
report and code, but they aren't measured from real data. Smart is also slower on average (7.4 vs 4.6 days),
because it waits for payday. In a real company I'd validate with a live A/B test: route, say, 10% of failures to
the new policy and compare recovery rates with a significance test.

---

## Bonus: quick answers to follow-ups

* **Why BigDecimal for money?** `0.1 + 0.2 != 0.3` in floating point. Money must be exact; the columns are
  `NUMERIC(12,2)`.
* **Why Testcontainers instead of H2?** H2 can't run my PostgreSQL migration as written (partial indexes with
  `WHERE`), and its SQL behaviour differs in small ways. Testing on the same database as production means the Flyway
  script, indexes and analytics query are exercised for real.
* **Why `PENDING` invoices?** The invoice is saved before calling the gateway. If the app crashes in between, the
  next billing run finds `PENDING` invoices and charges them, so nothing is lost or double-charged.
* **Why is the dashboard plain JavaScript and not React?** The project is about the backend. A dashboard with no
  build step ships inside the same jar, needs no Node toolchain in the Docker build, and talks to the API exactly
  like any other client, which shows the API is complete. If the UI grew (logins, many forms, shared state), I
  would move it to React or Angular as a separate app.
* **How does the dashboard avoid slow pages?** The invoice list loads customer and plan in the same query with an
  `@EntityGraph` (no N+1 queries), every list is paged, and the overview numbers are `COUNT`/`SUM` queries
  answered by PostgreSQL rather than loading rows into Java.
* **What would you add for production?** ShedLock, a transactional outbox for notifications, a real gateway with
  webhooks and idempotency keys, NPCI pre-debit notifications 24h before UPI debits, Spring Security for admin
  endpoints, and Micrometer metrics.
