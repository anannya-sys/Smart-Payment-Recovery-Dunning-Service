-- =====================================================================
-- subscription-recovery-service: initial schema
-- Every index below exists to serve a specific query; see the comments.
-- =====================================================================

CREATE TABLE customers (
    id                 BIGSERIAL    PRIMARY KEY,
    name               VARCHAR(120) NOT NULL,
    email              VARCHAR(254) NOT NULL,
    signup_date        DATE         NOT NULL,
    past_failure_count INT          NOT NULL DEFAULT 0 CHECK (past_failure_count >= 0),
    -- Unique constraint = unique index: fast lookup by email and no duplicate accounts.
    CONSTRAINT uk_customers_email UNIQUE (email)
);

CREATE TABLE plans (
    id            BIGSERIAL     PRIMARY KEY,
    name          VARCHAR(80)   NOT NULL,
    monthly_price NUMERIC(12,2) NOT NULL CHECK (monthly_price > 0),
    billing_cycle VARCHAR(20)   NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_plans_name UNIQUE (name)
);

CREATE TABLE subscriptions (
    id                     BIGSERIAL   PRIMARY KEY,
    customer_id            BIGINT      NOT NULL REFERENCES customers (id),
    plan_id                BIGINT      NOT NULL REFERENCES plans (id),
    status                 VARCHAR(20) NOT NULL,
    next_billing_date      DATE        NOT NULL,
    payment_method         VARCHAR(20) NOT NULL,
    payment_method_version INT         NOT NULL DEFAULT 1,
    start_date             DATE        NOT NULL,
    version                BIGINT      NOT NULL DEFAULT 0
);

-- Billing job: "WHERE status = 'ACTIVE' AND next_billing_date <= :today".
-- Equality column first, range column second, so Postgres can seek straight to the due rows.
CREATE INDEX idx_subscriptions_status_next_billing ON subscriptions (status, next_billing_date);
-- "All subscriptions of a customer" and FK lookups. Postgres does NOT index foreign keys automatically.
CREATE INDEX idx_subscriptions_customer ON subscriptions (customer_id);

CREATE TABLE invoices (
    id                     BIGSERIAL     PRIMARY KEY,
    subscription_id        BIGINT        NOT NULL REFERENCES subscriptions (id),
    amount                 NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    period_start           DATE          NOT NULL,
    due_date               DATE          NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    initial_failure_reason VARCHAR(30),
    last_failure_reason    VARCHAR(30),
    attempt_count          INT           NOT NULL DEFAULT 0,
    next_retry_at          TIMESTAMP,
    next_reminder_at       TIMESTAMP,
    recovery_deadline      TIMESTAMP,
    paid_at                TIMESTAMP,
    version                BIGINT        NOT NULL DEFAULT 0,
    -- Idempotent billing: one invoice per subscription per period, even if the job runs twice
    -- or two app instances run it at the same time.
    CONSTRAINT uk_invoices_subscription_period UNIQUE (subscription_id, period_start)
);

-- Recovery job: "WHERE status = 'FAILED' AND next_retry_at <= :now".
-- PARTIAL index: only FAILED rows are indexed. Most invoices are PAID, so this stays tiny
-- however large the invoices table grows.
CREATE INDEX idx_invoices_retry_due ON invoices (next_retry_at) WHERE status = 'FAILED';
CREATE INDEX idx_invoices_reminder_due ON invoices (next_reminder_at) WHERE status = 'FAILED';
CREATE INDEX idx_invoices_deadline ON invoices (recovery_deadline) WHERE status = 'FAILED';
-- Analytics groups failed invoices by their first failure reason, filtered by due date.
CREATE INDEX idx_invoices_failure_reason_due ON invoices (initial_failure_reason, due_date)
    WHERE initial_failure_reason IS NOT NULL;

CREATE TABLE payment_attempts (
    id                BIGSERIAL   PRIMARY KEY,
    invoice_id        BIGINT      NOT NULL REFERENCES invoices (id),
    attempt_number    INT         NOT NULL,
    attempted_at      TIMESTAMP   NOT NULL,
    result            VARCHAR(20) NOT NULL,
    failure_reason    VARCHAR(30),
    gateway_reference VARCHAR(64),
    decision_note     VARCHAR(500),
    CONSTRAINT uk_attempts_invoice_number UNIQUE (invoice_id, attempt_number)
);
-- The unique (invoice_id, attempt_number) index also serves "attempts of an invoice, in order",
-- so no separate invoice_id index is needed.
