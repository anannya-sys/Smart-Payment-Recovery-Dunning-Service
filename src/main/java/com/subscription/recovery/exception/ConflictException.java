package com.subscription.recovery.exception;

/** Maps to HTTP 409, e.g. duplicate email or plan name. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
