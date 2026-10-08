package com.subscription.recovery.dto;

import com.subscription.recovery.domain.Customer;
import com.subscription.recovery.domain.Invoice;
import com.subscription.recovery.domain.PaymentAttempt;
import com.subscription.recovery.domain.Plan;
import com.subscription.recovery.domain.Subscription;
import java.time.LocalDate;
import java.util.List;

/**
 * Entity -> DTO conversion in one place. Entities never leave the service layer, so the API contract is
 * independent of the database schema and lazy-loading surprises can't leak into JSON serialisation.
 */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static CustomerResponse toResponse(Customer c, LocalDate today) {
        return new CustomerResponse(c.getId(), c.getName(), c.getEmail(), c.getSignupDate(),
                c.tenureMonths(today), c.getPastFailureCount());
    }

    public static PlanResponse toResponse(Plan p) {
        return new PlanResponse(p.getId(), p.getName(), p.getMonthlyPrice(), p.getBillingCycle(),
                p.pricePerCycle(), p.isActive());
    }

    public static SubscriptionResponse toResponse(Subscription s) {
        return new SubscriptionResponse(s.getId(), s.getCustomer().getId(), s.getCustomer().getName(),
                s.getPlan().getId(), s.getPlan().getName(), s.getStatus(), s.getNextBillingDate(),
                s.getPaymentMethod(), s.getStartDate());
    }

    public static InvoiceResponse toResponse(Invoice i, List<PaymentAttempt> attempts) {
        List<PaymentAttemptResponse> attemptDtos = attempts == null ? null
                : attempts.stream().map(DtoMapper::toResponse).toList();
        return new InvoiceResponse(i.getId(), i.getSubscription().getId(), i.getAmount(), i.getPeriodStart(),
                i.getDueDate(), i.getStatus(), i.getInitialFailureReason(), i.getLastFailureReason(),
                i.getAttemptCount(), i.getNextRetryAt(), i.getRecoveryDeadline(), i.getPaidAt(), attemptDtos);
    }

    public static InvoiceSummaryResponse toSummary(Invoice i) {
        Subscription s = i.getSubscription();
        return new InvoiceSummaryResponse(i.getId(), s.getId(), s.getCustomer().getId(), s.getCustomer().getName(),
                s.getPlan().getName(), i.getAmount(), i.getDueDate(), i.getStatus(), i.getInitialFailureReason(),
                i.getLastFailureReason(), i.getAttemptCount(), i.getNextRetryAt(), i.getRecoveryDeadline(),
                i.getPaidAt());
    }

    public static PaymentAttemptResponse toResponse(PaymentAttempt a) {
        return new PaymentAttemptResponse(a.getAttemptNumber(), a.getAttemptedAt(), a.getResult(),
                a.getFailureReason(), a.getGatewayReference(), a.getDecisionNote());
    }
}
