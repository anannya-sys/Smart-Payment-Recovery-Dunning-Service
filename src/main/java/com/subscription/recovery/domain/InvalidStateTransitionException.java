package com.subscription.recovery.domain;

/** Thrown when code (or an API caller) tries a transition the state machine does not allow. */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(SubscriptionStatus from, SubscriptionStatus to) {
        super("Cannot move subscription from " + from + " to " + to);
    }
}
