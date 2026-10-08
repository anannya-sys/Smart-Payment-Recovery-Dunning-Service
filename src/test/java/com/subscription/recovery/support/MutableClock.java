package com.subscription.recovery.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** A clock tests can move forward ("fast-forward to the retry time") without sleeping. */
public class MutableClock extends Clock {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private Instant now;

    public MutableClock(LocalDateTime start) {
        this.now = start.atZone(IST).toInstant();
    }

    public void set(LocalDateTime time) {
        this.now = time.atZone(IST).toInstant();
    }

    public void advance(Duration d) {
        this.now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return IST;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
