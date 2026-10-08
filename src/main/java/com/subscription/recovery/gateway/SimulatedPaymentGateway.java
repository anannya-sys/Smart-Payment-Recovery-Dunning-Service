package com.subscription.recovery.gateway;

import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.domain.PaymentMethod;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;

/**
 * A realistic, reproducible stand-in for a UPI AutoPay / card gateway.
 *
 * <p>Each charge is evaluated against the customer's hidden {@link CustomerFinancialProfile}, in this order:
 * <ol>
 *   <li><b>Hard decline</b> (MANDATE_REVOKED for UPI, CARD_EXPIRED for cards): once an instrument breaks it stays
 *       broken until the customer provides a new one (new instrument version). Blind retries never work.</li>
 *   <li><b>BANK_DOWNTIME</b>: each bank has random 2-hour outage windows (~2% of windows). A retry a few hours
 *       later lands in a different window and almost always succeeds.</li>
 *   <li><b>INSUFFICIENT_BALANCE</b>: after each salary credit the account stays funded for a number of days that
 *       depends on the customer's stress level, then runs dry until the next salary (with a small chance of some
 *       other inflow on any given day). So balance is <i>persistent</i>: retrying tomorrow usually fails again,
 *       retrying right after payday usually works.</li>
 *       A small share of accounts are <i>abandoned</i> (never funded again): indistinguishable from a normal
 *       low balance at decline time, and unrecoverable by retries.</li>
 *   <li><b>LIMIT_EXCEEDED</b>: daily limit already used; resets the next day.</li>
 * </ol>
 * All randomness comes from {@link DeterministicRandom} with a fixed seed, so runs are reproducible.
 */
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final long SALT_HARD = 1;
    private static final long SALT_DOWNTIME = 2;
    private static final long SALT_BALANCE = 3;
    private static final long SALT_LIMIT = 4;
    private static final long SALT_INFLOW = 5;
    private static final long SALT_ABANDON = 6;
    /** Chance that a dry account received some other money (transfer, refund) on a given day. */
    private static final double DAILY_INFLOW_PROBABILITY = 0.05;
    private static final double BANK_DOWNTIME_PROBABILITY = 0.02;

    private final long seed;
    private final LongFunction<CustomerFinancialProfile> profiles;
    private final Map<Long, CustomerFinancialProfile> explicitProfiles = new ConcurrentHashMap<>();
    /** Instruments (customerId:version) that are permanently broken. */
    private final Set<String> brokenInstruments = ConcurrentHashMap.newKeySet();
    /** Instruments whose underlying bank account the customer has stopped funding. */
    private final Set<String> abandonedAccounts = ConcurrentHashMap.newKeySet();
    /** Customers who topped up their account after a reminder, and until when that money is there. */
    private final Map<Long, LocalDateTime> toppedUpUntil = new ConcurrentHashMap<>();

    /**
     * @param profiles fallback that derives a profile for customers not registered explicitly
     *                 (the live app uses {@link #derivedProfile(long, long)})
     */
    public SimulatedPaymentGateway(long seed, LongFunction<CustomerFinancialProfile> profiles) {
        this.seed = seed;
        this.profiles = profiles;
    }

    /** Gateway for the live app: profiles are derived from the customer id. */
    public static SimulatedPaymentGateway withDerivedProfiles(long seed) {
        return new SimulatedPaymentGateway(seed, id -> derivedProfile(seed, id));
    }

    /** Used by the simulation to give synthetic customers a specific financial profile. */
    public void registerProfile(long customerId, CustomerFinancialProfile profile) {
        explicitProfiles.put(customerId, profile);
    }

    /** Simulates a customer adding money after a "low balance" reminder. */
    public void recordTopUp(long customerId, LocalDateTime until) {
        toppedUpUntil.put(customerId, until);
    }

    @Override
    public PaymentResult charge(ChargeRequest req) {
        CustomerFinancialProfile p = explicitProfiles.computeIfAbsent(req.customerId(), profiles::apply);
        long day = req.at().toLocalDate().toEpochDay();
        String reference = "SIM-" + req.invoiceId() + "-" + req.attemptNumber();
        String instrument = req.customerId() + ":" + req.instrumentVersion();

        // 1. Hard declines: a broken instrument stays broken.
        FailureReason hardReason = req.method() == PaymentMethod.CARD
                ? FailureReason.CARD_EXPIRED : FailureReason.MANDATE_REVOKED;
        if (brokenInstruments.contains(instrument)) {
            return PaymentResult.failure(hardReason, reference);
        }
        // Instruments can only break at the scheduled (first) charge of a billing cycle.
        if (req.attemptNumber() == 1
                && DeterministicRandom.uniform(seed, req.customerId(), req.instrumentVersion(), day, SALT_HARD) < p.hardDeclineRate()) {
            brokenInstruments.add(instrument);
            return PaymentResult.failure(hardReason, reference);
        }

        // 2. Bank downtime, in 2-hour windows per bank.
        long twoHourWindow = req.at().toEpochSecond(ZoneOffset.UTC) / 7200;
        if (DeterministicRandom.uniform(seed, p.bankId(), twoHourWindow, SALT_DOWNTIME) < BANK_DOWNTIME_PROBABILITY) {
            return PaymentResult.failure(FailureReason.BANK_DOWNTIME, reference);
        }

        // 3. Balance depends on where we are in the customer's salary cycle (or the account was abandoned).
        LocalDateTime topUp = toppedUpUntil.get(req.customerId());
        boolean toppedUp = topUp != null && !req.at().isAfter(topUp);
        if (req.attemptNumber() == 1 && DeterministicRandom.uniform(seed, req.customerId(), req.instrumentVersion(),
                day, SALT_ABANDON) < p.abandonmentRate()) {
            abandonedAccounts.add(instrument);
        }
        boolean abandoned = abandonedAccounts.contains(instrument);
        if (!toppedUp && (abandoned || isAccountDry(req.customerId(), p, req.at().toLocalDate()))) {
            return PaymentResult.failure(FailureReason.INSUFFICIENT_BALANCE, reference);
        }

        // 4. Daily limit, resets every day.
        if (DeterministicRandom.uniform(seed, req.customerId(), day, SALT_LIMIT) < p.limitStress()) {
            return PaymentResult.failure(FailureReason.LIMIT_EXCEEDED, reference);
        }
        return PaymentResult.success(reference);
    }

    /**
     * The account is funded for {@code fundedDays} after each salary credit, then dry until the next one.
     * fundedDays is drawn once per (customer, salary cycle): a stressed customer runs out of money early.
     */
    boolean isAccountDry(long customerId, CustomerFinancialProfile p, LocalDate date) {
        LocalDate lastSalary = lastSalaryDate(date, p.salaryDay());
        double u = DeterministicRandom.uniform(seed, customerId, lastSalary.toEpochDay(), SALT_BALANCE);
        double fundedFraction = Math.max(0.05, 1.0 - p.balanceStress() * (0.5 + 1.5 * u));
        long daysSinceSalary = date.toEpochDay() - lastSalary.toEpochDay();
        if (daysSinceSalary < fundedFraction * 30) {
            return false;
        }
        return DeterministicRandom.uniform(seed, customerId, date.toEpochDay(), SALT_INFLOW) >= DAILY_INFLOW_PROBABILITY;
    }

    /** Most recent salary credit on or before {@code date}. */
    static LocalDate lastSalaryDate(LocalDate date, int salaryDay) {
        LocalDate thisMonth = date.withDayOfMonth(Math.min(salaryDay, date.lengthOfMonth()));
        if (!thisMonth.isAfter(date)) {
            return thisMonth;
        }
        LocalDate prev = date.minusMonths(1);
        return prev.withDayOfMonth(Math.min(salaryDay, prev.lengthOfMonth()));
    }

    /** Stable pseudo-random profile for a real (non-simulated) customer id. */
    public static CustomerFinancialProfile derivedProfile(long seed, long customerId) {
        double u = DeterministicRandom.uniform(seed, customerId, 100);
        int salaryDay = u < 0.55 ? 1 : (u < 0.85 ? 5 : 7);
        return new CustomerFinancialProfile(
                salaryDay,
                0.03 + 0.40 * DeterministicRandom.uniform(seed, customerId, 101),
                0.01 + 0.03 * DeterministicRandom.uniform(seed, customerId, 102),
                0.01 + 0.03 * DeterministicRandom.uniform(seed, customerId, 103),
                0.01 + 0.03 * DeterministicRandom.uniform(seed, customerId, 105),
                (int) (DeterministicRandom.uniform(seed, customerId, 104) * 8));
    }
}
