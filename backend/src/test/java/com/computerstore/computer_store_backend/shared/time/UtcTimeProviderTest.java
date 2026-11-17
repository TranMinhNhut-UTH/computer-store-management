package com.computerstore.computer_store_backend.shared.time;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtcTimeProviderTest {

    @Test
    void sameInstantProducesSameUtcValueRegardlessOfClockZone() {
        Instant instant = Instant.parse("2026-11-17T12:34:56.123456Z");
        UtcTimeProvider utc = new UtcTimeProvider(Clock.fixed(instant, ZoneOffset.UTC));
        UtcTimeProvider local = new UtcTimeProvider(Clock.fixed(instant, ZoneId.of("Asia/Ho_Chi_Minh")));

        assertEquals(LocalDateTime.of(2026, 11, 17, 12, 34, 56, 123456000), utc.now());
        assertEquals(utc.now(), local.now());
        assertEquals(utc.now(), utc.now());
    }

    @Test
    void nanosecondsAreTruncatedRatherThanRounded() {
        UtcTimeProvider time = new UtcTimeProvider(Clock.fixed(
                Instant.parse("2026-11-17T12:34:56.123456999Z"), ZoneOffset.UTC));

        assertEquals(LocalDateTime.of(2026, 11, 17, 12, 34, 56, 123456000), time.now());
    }

    @Test
    @ResourceLock("java.util.TimeZone.default")
    void systemDefaultTimeZoneDoesNotAffectUtcValue() {
        TimeZone original = TimeZone.getDefault();
        try {
            Clock fixed = Clock.fixed(Instant.parse("2026-11-17T23:30:00Z"), ZoneOffset.UTC);
            LocalDateTime expected = LocalDateTime.of(2026, 11, 17, 23, 30);
            for (String zone : new String[]{"Pacific/Kiritimati", "America/Los_Angeles"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertEquals(expected, new UtcTimeProvider(fixed).now());
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void utcMidnightBoundaryDoesNotRoundIntoNextDay() {
        UtcTimeProvider before = new UtcTimeProvider(Clock.fixed(
                Instant.parse("2026-12-31T23:59:59.999999999Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        UtcTimeProvider after = new UtcTimeProvider(Clock.fixed(
                Instant.parse("2027-01-01T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

        assertEquals(LocalDateTime.of(2026, 12, 31, 23, 59, 59, 999999000), before.now());
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), after.now());
    }
}
