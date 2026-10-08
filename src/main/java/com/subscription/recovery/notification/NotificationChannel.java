package com.subscription.recovery.notification;

/**
 * One way of reaching a customer. Add an {@code EmailChannel} (SES/SendGrid) or {@code SmsChannel} (MSG91,
 * Twilio) as another Spring bean and {@link NotificationService} will use it automatically.
 */
public interface NotificationChannel {

    String name();

    void send(NotificationMessage message);
}
