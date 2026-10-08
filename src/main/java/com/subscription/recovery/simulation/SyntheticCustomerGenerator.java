package com.subscription.recovery.simulation;

import com.subscription.recovery.domain.PaymentMethod;
import com.subscription.recovery.gateway.CustomerFinancialProfile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a reproducible population of Indian subscription customers.
 *
 * <p>Every customer gets a hidden "financial stress" level z in [0,1]. Stress raises the chance of low balance,
 * of past failures (so the risk score is <i>informative</i>, as in reality) and lowers engagement with
 * reminders. Plan mix, payment-method mix (75% UPI AutoPay) and salary days (55% on the 1st, 30% on the 5th,
 * 15% on the 7th) are chosen to look like a typical Indian OTT / SaaS subscriber base.
 */
public final class SyntheticCustomerGenerator {

    private static final String[] PLAN_NAMES = {"Basic", "Standard", "Premium", "Pro"};
    private static final BigDecimal[] PLAN_PRICES = {new BigDecimal("149.00"), new BigDecimal("499.00"),
            new BigDecimal("999.00"), new BigDecimal("1999.00")};
    private static final double[] PLAN_WEIGHTS = {0.35, 0.35, 0.20, 0.10};

    private SyntheticCustomerGenerator() {
    }

    public static List<SyntheticCustomer> generate(int count, long seed, LocalDate start) {
        Random rnd = new Random(seed); // sequential generation from one seeded Random is reproducible
        List<SyntheticCustomer> customers = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            double stress = Math.pow(rnd.nextDouble(), 1.6); // skewed: most customers are low-stress

            int tenure = sampleTenure(rnd);
            LocalDate signup = start.minusMonths(tenure).minusDays(rnd.nextInt(28));
            int pastFailures = samplePoisson(rnd, 0.2 + 2.2 * stress);

            int plan = pickWeighted(rnd, PLAN_WEIGHTS);
            PaymentMethod method = rnd.nextDouble() < 0.75 ? PaymentMethod.UPI_AUTOPAY : PaymentMethod.CARD;

            double s = rnd.nextDouble();
            int salaryDay = s < 0.55 ? 1 : (s < 0.85 ? 5 : 7);
            double newCustomerPenalty = tenure < 3 ? 0.01 : 0.0;
            CustomerFinancialProfile profile = new CustomerFinancialProfile(
                    salaryDay,
                    0.03 + 0.55 * stress,                        // balance stress
                    0.01 + 0.03 * rnd.nextDouble(),              // limit stress
                    0.012 + 0.02 * stress + newCustomerPenalty,  // hard-decline rate per cycle
                    0.005 + 0.03 * stress + newCustomerPenalty,  // account-abandonment rate per cycle
                    rnd.nextInt(8));                             // bank

            double engagement = clamp(0.80 - 0.40 * stress - (tenure < 3 ? 0.15 : 0.0) + 0.1 * rnd.nextGaussian(),
                    0.05, 0.95);

            customers.add(new SyntheticCustomer(i, signup, pastFailures, PLAN_NAMES[plan], PLAN_PRICES[plan], method,
                    1 + rnd.nextInt(28), profile, engagement));
        }
        return customers;
    }

    /** 25% new (0-2 months), 30% 3-11, 25% 12-23, 20% 24-48. */
    private static int sampleTenure(Random rnd) {
        double u = rnd.nextDouble();
        if (u < 0.25) {
            return rnd.nextInt(3);
        }
        if (u < 0.55) {
            return 3 + rnd.nextInt(9);
        }
        return u < 0.80 ? 12 + rnd.nextInt(12) : 24 + rnd.nextInt(25);
    }

    private static int samplePoisson(Random rnd, double mean) {
        double l = Math.exp(-mean);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= rnd.nextDouble();
        } while (p > l);
        return k - 1;
    }

    private static int pickWeighted(Random rnd, double[] weights) {
        double u = rnd.nextDouble();
        double acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (u < acc) {
                return i;
            }
        }
        return weights.length - 1;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
