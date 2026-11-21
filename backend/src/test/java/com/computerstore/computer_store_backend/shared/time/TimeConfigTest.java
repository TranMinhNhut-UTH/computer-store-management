package com.computerstore.computer_store_backend.shared.time;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeConfigTest {

    @Test
    void clockUsesUtc() {
        assertEquals(ZoneOffset.UTC, new TimeConfig().utcClock().getZone());
    }
}
