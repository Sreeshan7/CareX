package com.carex.leave.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock that can be moved forward to trigger escalation deadlines deterministically. */
public class MutableClock extends Clock {
    /** Monday 2026-09-28 10:00 IST — the blueprint's reference date. */
    public static final Instant REFERENCE = Instant.parse("2026-09-28T04:30:00Z");
    private volatile Instant now = REFERENCE;

    public void set(Instant instant) { this.now = instant; }
    public void advance(Duration d) { this.now = now.plus(d); }
    public void reset() { this.now = REFERENCE; }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
}
