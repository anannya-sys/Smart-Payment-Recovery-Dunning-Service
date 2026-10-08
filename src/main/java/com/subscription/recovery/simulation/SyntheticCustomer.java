package com.subscription.recovery.simulation;

import com.subscription.recovery.domain.PaymentMethod;
import com.subscription.recovery.gateway.CustomerFinancialProfile;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A generated customer. Observable fields (signup, failures, plan) feed the risk score; the hidden
 * {@code profile} and {@code engagement} drive what actually happens, just like in real life.
 *
 * @param billingDay  day of month the subscription renews (1-28)
 * @param engagement  0..1, how likely the customer is to act on a reminder
 */
public record SyntheticCustomer(
        long id,
        LocalDate signupDate,
        int pastFailures,
        String planName,
        BigDecimal monthlyPrice,
        PaymentMethod method,
        int billingDay,
        CustomerFinancialProfile profile,
        double engagement) {
}
