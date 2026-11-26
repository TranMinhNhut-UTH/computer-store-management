package com.computerstore.computer_store_backend.identity.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class EmployeeSessionLifetimeTest {
    @ParameterizedTest
    @ValueSource(longs = {1800, 28_800})
    void concurrentRequestsAtEitherDeadlineCannotRenewTheSession(long deadlineSeconds) throws Exception {
        var login = Instant.parse("2026-11-25T00:00:00Z");
        var lifetime = new EmployeeSessionLifetime(login);
        for (long seconds = 1200; seconds < deadlineSeconds && deadlineSeconds > 1800; seconds += 1200) {
            assertTrue(lifetime.access(Clock.fixed(login.plusSeconds(seconds), ZoneOffset.UTC), true));
        }
        var beforeDeadline = Clock.fixed(login.plusSeconds(deadlineSeconds - 1), ZoneOffset.UTC);
        assertTrue(lifetime.access(beforeDeadline, false));

        var deadline = Clock.fixed(login.plusSeconds(deadlineSeconds), ZoneOffset.UTC);
        var barrier = new CyclicBarrier(3);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return lifetime.access(deadline, true);
            });
            var second = workers.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return lifetime.access(deadline, true);
            });
            barrier.await(5, TimeUnit.SECONDS);
            assertFalse(first.get(5, TimeUnit.SECONDS));
            assertFalse(second.get(5, TimeUnit.SECONDS));
        }
        assertFalse(lifetime.access(beforeDeadline, true));
    }
}
