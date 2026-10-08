package com.subscription.recovery.gateway;

import com.subscription.recovery.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Everything a payment gateway needs to attempt one debit.
 *
 * @param instrumentVersion identifies the mandate/card: a new version = customer re-authorised / added a card
 * @param attemptNumber     1 for the scheduled charge, 2+ for retries
 * @param at                when the charge is attempted (passed in, not read from a clock, for reproducibility)
 */
public record ChargeRequest(
        long customerId,
        long invoiceId,
        BigDecimal amount,
        PaymentMethod method,
        int instrumentVersion,
        int attemptNumber,
        LocalDateTime at) {
}
