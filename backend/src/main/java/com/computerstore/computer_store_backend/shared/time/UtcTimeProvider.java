package com.computerstore.computer_store_backend.shared.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Supplies UTC values for DATETIME(6); LocalDateTime itself has no time zone. */
@Component
public class UtcTimeProvider {

    private final Clock clock;

    public UtcTimeProvider(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Returns the clock's current instant in UTC, truncated to microseconds. */
    public LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }
}
