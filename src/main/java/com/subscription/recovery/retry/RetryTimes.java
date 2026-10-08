package com.subscription.recovery.retry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Small date helpers shared by the strategies. */
final class RetryTimes {

    /** Retries that depend on the customer's money run mid-morning, after overnight salary credits settle. */
    static final LocalTime MORNING_SLOT = LocalTime.of(10, 0);

    private RetryTimes() {
    }

    static LocalDateTime morningOf(LocalDate date) {
        return date.atTime(MORNING_SLOT);
    }

    static boolean fitsBefore(LocalDateTime candidate, LocalDateTime deadline) {
        return deadline == null || !candidate.isAfter(deadline);
    }
}
