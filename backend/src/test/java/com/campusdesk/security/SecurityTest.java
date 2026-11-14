package com.campusdesk.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityTest {

    /** Fewer iterations than production so the tests stay fast. */
    private final PasswordHasher hasher = new PasswordHasher(1_000);

    @Test
    void hashVerifiesOnlyTheRightPassword() {
        String stored = hasher.hash("correct horse battery");
        assertTrue(hasher.matches("correct horse battery", stored));
        assertFalse(hasher.matches("Correct horse battery", stored));
        assertFalse(hasher.matches("anything", "not-a-hash"));
    }

    @Test
    void samePasswordGetsADifferentSaltEachTime() {
        assertFalse(hasher.hash("same").equals(hasher.hash("same")));
    }

    @Test
    void weakerOldHashesAreFlaggedForUpgrade() {
        assertTrue(new PasswordHasher(5_000).needsRehash(hasher.hash("pw")));
        assertFalse(hasher.needsRehash(hasher.hash("pw")));
    }

    @Test
    void throttleLocksAfterFiveFailuresThenUnlocks() {
        FakeClock clock = new FakeClock(Instant.parse("2026-11-12T19:00:00Z"));
        LoginThrottle throttle = new LoginThrottle(clock);
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) throttle.recordFailure("Sam@Uni.ca");
        assertEquals(Duration.ofMinutes(10), throttle.retryAfter("sam@uni.ca"));   // case-insensitive
        clock.now = clock.now.plus(Duration.ofMinutes(11));
        assertEquals(Duration.ZERO, throttle.retryAfter("sam@uni.ca"));
    }

    @Test
    void successfulLoginClearsFailures() {
        LoginThrottle throttle = new LoginThrottle(Clock.systemUTC());
        for (int i = 0; i < 4; i++) throttle.recordFailure("a@b.ca");
        throttle.recordSuccess("a@b.ca");
        throttle.recordFailure("a@b.ca");
        assertEquals(Duration.ZERO, throttle.retryAfter("a@b.ca"));
    }

    /** A clock the test can move forward by hand. */
    static class FakeClock extends Clock {
        Instant now;

        FakeClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
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
}
