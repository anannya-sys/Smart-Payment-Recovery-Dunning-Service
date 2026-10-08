package com.subscription.recovery.service;

import com.subscription.recovery.domain.BillingCycle;
import com.subscription.recovery.domain.PaymentMethod;
import com.subscription.recovery.dto.CustomerRequest;
import com.subscription.recovery.dto.DemoDataResponse;
import com.subscription.recovery.dto.PlanRequest;
import com.subscription.recovery.dto.PlanResponse;
import com.subscription.recovery.dto.SubscriptionRequest;
import com.subscription.recovery.exception.ConflictException;
import com.subscription.recovery.repository.CustomerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.springframework.stereotype.Service;

/**
 * Fills an empty database with a realistic demo business (plans, customers, subscriptions) and runs today's
 * billing, so the dashboard has failed invoices to recover straight away.
 *
 * <p>Goes through the normal services, so demo data obeys exactly the same validation and business rules as API
 * clients. A fixed {@link Random} seed makes the dataset the same on every machine.
 */
@Service
public class DemoDataService {

    private static final String[] FIRST_NAMES = {"Aarav", "Priya", "Rohan", "Ananya", "Vikram", "Sneha", "Arjun",
            "Kavya", "Rahul", "Meera", "Aditya", "Isha", "Karan", "Divya", "Siddharth", "Pooja", "Nikhil", "Riya",
            "Manish", "Shreya", "Varun", "Neha", "Harsh", "Tanvi"};
    private static final String[] LAST_NAMES = {"Sharma", "Iyer", "Patel", "Reddy", "Gupta", "Nair", "Singh",
            "Mehta", "Rao", "Banerjee", "Kulkarni", "Joshi"};
    private static final int CUSTOMERS = 48;

    private final CustomerRepository customerRepository;
    private final CustomerService customers;
    private final PlanService plans;
    private final SubscriptionService subscriptions;
    private final BillingService billing;
    private final Clock clock;

    public DemoDataService(CustomerRepository customerRepository, CustomerService customers, PlanService plans,
                           SubscriptionService subscriptions, BillingService billing, Clock clock) {
        this.customerRepository = customerRepository;
        this.customers = customers;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.billing = billing;
        this.clock = clock;
    }

    public DemoDataResponse load() {
        // Only into an empty database: never mix demo rows with someone's real data.
        if (customerRepository.count() > 0) {
            throw new ConflictException("Demo data can only be loaded into an empty database");
        }
        Random random = new Random(7);
        LocalDate today = LocalDate.now(clock);

        List<PlanResponse> createdPlans = new ArrayList<>();
        createdPlans.add(plans.create(new PlanRequest("Basic", new BigDecimal("149.00"), BillingCycle.MONTHLY, true)));
        createdPlans.add(plans.create(new PlanRequest("Standard", new BigDecimal("299.00"), BillingCycle.MONTHLY, true)));
        createdPlans.add(plans.create(new PlanRequest("Premium", new BigDecimal("499.00"), BillingCycle.MONTHLY, true)));
        createdPlans.add(plans.create(new PlanRequest("Family", new BigDecimal("799.00"), BillingCycle.QUARTERLY, true)));
        createdPlans.add(plans.create(new PlanRequest("Premium Annual", new BigDecimal("399.00"), BillingCycle.YEARLY,
                true)));

        int subscriptionsCreated = 0;
        for (int i = 0; i < CUSTOMERS; i++) {
            String first = FIRST_NAMES[i % FIRST_NAMES.length];
            String last = LAST_NAMES[(i * 7 + i / FIRST_NAMES.length) % LAST_NAMES.length];
            String email = (first + "." + last + (i + 1)).toLowerCase(Locale.ROOT) + "@example.in";
            // Mix of brand-new and long-standing customers, a few with a history of failed payments.
            LocalDate signup = today.minusDays(random.nextInt(900));
            int pastFailures = random.nextDouble() < 0.7 ? 0 : 1 + random.nextInt(4);
            Long customerId = customers.create(new CustomerRequest(first + " " + last, email, signup, pastFailures))
                    .id();

            PlanResponse plan = createdPlans.get(random.nextInt(createdPlans.size()));
            PaymentMethod method = random.nextDouble() < 0.75 ? PaymentMethod.UPI_AUTOPAY : PaymentMethod.CARD;
            subscriptions.create(new SubscriptionRequest(customerId, plan.id(), method, today));
            subscriptionsCreated++;
        }
        return new DemoDataResponse(createdPlans.size(), CUSTOMERS, subscriptionsCreated, billing.runBilling());
    }
}
