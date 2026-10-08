package com.subscription.recovery.notification;

import com.subscription.recovery.retry.ReminderType;
import java.util.Map;

/**
 * Channel-independent message. Email, SMS or WhatsApp adapters render the same object their own way.
 *
 * @param metadata machine-readable extras (invoiceId, amount, ...) for templating, tracking and analytics
 */
public record NotificationMessage(
        String recipientEmail,
        String recipientName,
        ReminderType type,
        String subject,
        String body,
        Map<String, String> metadata) {
}
