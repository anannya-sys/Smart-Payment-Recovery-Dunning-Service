package com.subscription.recovery.gateway;

/**
 * Hidden "ground truth" about a customer's finances that the simulated gateway uses to decide outcomes.
 * The recovery engine never sees this, exactly like a real merchant never sees a customer's bank balance.
 *
 * @param salaryDay        day of month salary is credited (1, 5 or 7 for most salaried Indians)
 * @param balanceStress    0..1, how often the account runs low before salary
 * @param limitStress      0..1, chance per day the UPI/card daily limit is already used up
 * @param hardDeclineRate  chance per billing cycle the mandate is revoked / card expires
 * @param abandonmentRate  chance per billing cycle the customer stops funding the linked account altogether
 *                         (common in India: salary account changes, old account left empty). Looks like a soft
 *                         INSUFFICIENT_BALANCE decline, but no retry will ever fix it
 * @param bankId           which bank the customer uses (banks have independent downtime windows)
 */
public record CustomerFinancialProfile(
        int salaryDay,
        double balanceStress,
        double limitStress,
        double hardDeclineRate,
        double abandonmentRate,
        int bankId) {
}
