package com.computerstore.computer_store_backend.identity.security;

import java.io.Serial;
import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Server-side lifetime belonging to one successful login. */
final class EmployeeSessionLifetime implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(8);

    private final Instant absoluteDeadline;
    private Instant lastActivity;
    private boolean expired;

    EmployeeSessionLifetime(Instant authenticatedAt) {
        absoluteDeadline = authenticatedAt.plus(ABSOLUTE_TIMEOUT);
        lastActivity = authenticatedAt;
    }

    synchronized boolean access(Clock clock, boolean recordActivity) {
        if (expired) {
            return false;
        }
        Instant now = clock.instant();
        if (!now.isBefore(absoluteDeadline) || !now.isBefore(lastActivity.plus(IDLE_TIMEOUT))) {
            expired = true;
            return false;
        }
        if (recordActivity && now.isAfter(lastActivity)) {
            lastActivity = now;
        }
        return true;
    }
}
