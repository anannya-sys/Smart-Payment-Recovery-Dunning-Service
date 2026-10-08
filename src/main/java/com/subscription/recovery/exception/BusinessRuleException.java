package com.subscription.recovery.exception;

/** Maps to HTTP 422: the request is well-formed but breaks a business rule (e.g. subscribing to an inactive plan). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
