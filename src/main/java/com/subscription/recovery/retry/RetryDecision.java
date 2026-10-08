package com.subscription.recovery.retry;

import java.time.LocalDateTime;

/**
 * What to do after a failed attempt. Immutable; built through the static factories.
 *
 * @param action         RETRY (at {@code nextRetryAt}), AWAIT_CUSTOMER (no retry; wait for the customer to fix
 *                       their payment method until the deadline) or GIVE_UP (write off now)
 * @param reminderNow    message to send immediately, or null
 * @param followUpAt     when to send a follow-up reminder, or null
 * @param rationale      plain-English explanation, stored on the payment attempt for auditability
 */
public record RetryDecision(
        Action action,
        LocalDateTime nextRetryAt,
        ReminderType reminderNow,
        LocalDateTime followUpAt,
        String rationale) {

    public enum Action { RETRY, AWAIT_CUSTOMER, GIVE_UP }

    public static RetryDecision retryAt(LocalDateTime when, String rationale) {
        return new RetryDecision(Action.RETRY, when, null, null, rationale);
    }

    public static RetryDecision awaitCustomer(ReminderType reminder, LocalDateTime followUpAt, String rationale) {
        return new RetryDecision(Action.AWAIT_CUSTOMER, null, reminder, followUpAt, rationale);
    }

    public static RetryDecision giveUp(String rationale) {
        return new RetryDecision(Action.GIVE_UP, null, null, null, rationale);
    }

    public RetryDecision withReminderNow(ReminderType reminder) {
        return new RetryDecision(action, nextRetryAt, reminder, followUpAt, rationale);
    }

    public RetryDecision withFollowUp(LocalDateTime when) {
        return new RetryDecision(action, nextRetryAt, reminderNow, when, rationale);
    }

    public RetryDecision withRationale(String newRationale) {
        return new RetryDecision(action, nextRetryAt, reminderNow, followUpAt, newRationale);
    }
}
