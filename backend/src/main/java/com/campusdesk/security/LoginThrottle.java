package com.campusdesk.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// locks an email for 10 min after 5 wrong passwords
public class LoginThrottle {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

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
