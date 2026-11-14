package com.campusdesk.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after MAX_FAILURES wrong passwords for one email within
 * WINDOW, further attempts are refused until the oldest failure falls out of the window.
 *
 * Data structure: a ConcurrentHashMap (thread-safe, many logins at once) from email to a
 * Deque of failure times. New failures go on the back; expired ones are popped off the
 * front. That's a "sliding window" - each check only touches the few recent entries.
 *
 * Kept in memory on purpose: restarting the app clears it, which is fine for a desktop app.
 */
public class LoginThrottle {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** How long until this email may try again, or Duration.ZERO if it's allowed now. */
    public Duration retryAfter(String email) {
        Deque<Instant> q = failures.get(key(email));
        if (q == null) return Duration.ZERO;
        synchronized (q) {
            prune(q);
            if (q.size() < MAX_FAILURES) return Duration.ZERO;
            return Duration.between(clock.instant(), q.peekFirst().plus(WINDOW));
        }
    }

    public void recordFailure(String email) {
        Deque<Instant> q = failures.computeIfAbsent(key(email), k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(clock.instant());
        }
    }

    public void recordSuccess(String email) {
        failures.remove(key(email));
    }

    private void prune(Deque<Instant> q) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
