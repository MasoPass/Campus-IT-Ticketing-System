package com.campusdesk.service;

import com.campusdesk.model.SlaPolicy;
import com.campusdesk.model.SlaState;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure SLA maths (no Spring, no database), so it's easy to unit test.
 *
 * Simplification worth mentioning in an interview: deadlines use calendar time, 24/7.
 * A real help desk often counts business hours only, and pauses the clock while the
 * ticket is "Waiting on user". Both would slot in here without touching anything else.
 */
public final class SlaCalculator {
    private SlaCalculator() {}

    /** Below this fraction of the allowed time remaining, an open ticket is "at risk". */
    public static final double AT_RISK_FRACTION = 0.25;

    public record Deadlines(Instant responseDue, Instant resolutionDue) {}

    public static Deadlines deadlines(Instant createdAt, SlaPolicy policy) {
        return new Deadlines(
                createdAt.plus(Duration.ofMinutes(policy.responseMinutes())),
                createdAt.plus(Duration.ofMinutes(policy.resolutionMinutes())));
    }

    /**
     * @param resolvedAt null while the ticket is still open
     */
    public static SlaState state(Instant createdAt, Instant resolutionDue, Instant resolvedAt, Instant now) {
        if (resolvedAt != null) {
            return resolvedAt.isAfter(resolutionDue) ? SlaState.MISSED : SlaState.MET;
        }
        if (now.isAfter(resolutionDue)) return SlaState.OVERDUE;
        long total = Duration.between(createdAt, resolutionDue).toMillis();
        long left = Duration.between(now, resolutionDue).toMillis();
        return total > 0 && (double) left / total < AT_RISK_FRACTION ? SlaState.AT_RISK : SlaState.ON_TRACK;
    }
}
