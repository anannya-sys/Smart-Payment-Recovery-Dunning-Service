package com.subscription.recovery.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default channel: writes a structured, greppable log line instead of sending a real email/SMS. */
@Component
public class LoggingNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationChannel.class);

    @Override
    public String name() {
        return "log";
    }

    @Override
    public void send(NotificationMessage m) {
        log.info("NOTIFY channel=log type={} to={} subject=\"{}\" body=\"{}\" meta={}",
                m.type(), m.recipientEmail(), m.subject(), m.body(), m.metadata());
    }
}
