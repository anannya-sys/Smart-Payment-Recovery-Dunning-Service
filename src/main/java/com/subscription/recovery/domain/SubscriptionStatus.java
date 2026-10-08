package com.subscription.recovery.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Subscription state machine. All legal transitions are declared in one table so the rules are easy
 * to read, test and change.
 *
 * <pre>
 *            payment fails                     max retries / deadline
 *   ACTIVE ---------------> PAST_DUE ------------------------------> CANCELLED
 *     ^  \                    |
 *     |   \ pause             | invoice recovered
 *     |    v                  v
 *     |   PAUSED            ACTIVE
 *     +--- resume
 * </pre>
 *
 * CANCELLED is terminal. A paused subscription is not billed.
 */
public enum SubscriptionStatus {
    ACTIVE,
    PAST_DUE,
    PAUSED,
    CANCELLED;

    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> ALLOWED = Map.of(
            ACTIVE, EnumSet.of(PAST_DUE, PAUSED, CANCELLED),
            PAST_DUE, EnumSet.of(ACTIVE, CANCELLED),
            PAUSED, EnumSet.of(ACTIVE, CANCELLED),
            CANCELLED, EnumSet.noneOf(SubscriptionStatus.class));

    public boolean canTransitionTo(SubscriptionStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
