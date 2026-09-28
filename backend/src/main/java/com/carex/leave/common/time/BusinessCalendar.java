package com.carex.leave.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Single source of "now" and "today" (implementation.md §28.6 T10). Never call LocalDate.now() elsewhere.
 * The clock is injectable so tests can move time (escalation).
 */
public class BusinessCalendar {
    private final Clock clock;
    private final ZoneId zone;

    public BusinessCalendar(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public Instant now() { return clock.instant(); }
    public LocalDate today() { return LocalDate.ofInstant(clock.instant(), zone); }
    public ZoneId zone() { return zone; }
}
