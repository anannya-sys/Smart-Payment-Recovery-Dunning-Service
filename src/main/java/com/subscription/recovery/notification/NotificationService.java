package com.subscription.recovery.notification;

import com.subscription.recovery.domain.Customer;
import com.subscription.recovery.domain.Invoice;
import com.subscription.recovery.retry.ReminderType;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Builds customer messages from a {@link ReminderType} and fans them out to every {@link NotificationChannel}.
 *
 * <p>A failing channel is logged and skipped: a broken SMS provider must never roll back a payment transaction.
 * In production this would publish to a queue (outbox pattern) so messages are sent after the DB commit.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final List<NotificationChannel> channels;

    public NotificationService(List<NotificationChannel> channels) {
        this.channels = channels;
    }

    public NotificationMessage notify(Customer customer, Invoice invoice, ReminderType type) {
        NotificationMessage message = build(customer, invoice, type);
        for (NotificationChannel channel : channels) {
            try {
                channel.send(message);
            } catch (RuntimeException e) {
                log.warn("Channel {} failed to send {} for invoice {}", channel.name(), type, invoice.getId(), e);
            }
        }
        return message;
    }

    NotificationMessage build(Customer c, Invoice inv, ReminderType type) {
        String amount = "INR " + inv.getAmount().toPlainString();
        String subject;
        String body;
        switch (type) {
            case PAYMENT_FAILED -> {
                subject = "Your payment of " + amount + " failed";
                body = "Hi " + c.getName() + ", we couldn't collect " + amount + " for your subscription. "
                        + "We'll try again soon.";
            }
            case UPDATE_PAYMENT_METHOD -> {
                subject = "Action needed: update your payment method";
                body = "Hi " + c.getName() + ", your UPI AutoPay mandate or card is no longer valid, so we "
                        + "can't collect " + amount + ". Please re-authorise AutoPay or add a new card to keep "
                        + "your subscription active.";
            }
            case LOW_BALANCE_HEADS_UP -> {
                subject = "Heads-up: we'll retry your payment of " + amount;
                body = "Hi " + c.getName() + ", your last payment failed due to low balance. "
                        + (inv.getNextRetryAt() != null
                        ? "We'll retry on " + inv.getNextRetryAt().format(DATE) + ". " : "")
                        + "Please keep " + amount + " available in your account.";
            }
            case PAYMENT_RECOVERED -> {
                subject = "Payment received, thank you!";
                body = "Hi " + c.getName() + ", we've received " + amount + ". Your subscription is active.";
            }
            case SUBSCRIPTION_CANCELLED -> {
                subject = "Your subscription has been cancelled";
                body = "Hi " + c.getName() + ", we couldn't collect " + amount + " after several attempts, so "
                        + "your subscription was cancelled. You can resubscribe any time.";
            }
            default -> throw new IllegalArgumentException("Unknown reminder type " + type);
        }
        Map<String, String> metadata = Map.of(
                "invoiceId", String.valueOf(inv.getId()),
                "amount", inv.getAmount().toPlainString(),
                "reason", String.valueOf(inv.getLastFailureReason()));
        return new NotificationMessage(c.getEmail(), c.getName(), type, subject, body, metadata);
    }
}
