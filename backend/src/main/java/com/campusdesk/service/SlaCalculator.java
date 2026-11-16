package com.campusdesk.service;

import com.campusdesk.model.SlaPolicy;
import com.campusdesk.model.SlaState;

import java.time.Duration;
import java.time.Instant;

public final class SlaCalculator {
    private SlaCalculator() {}

    // under 25% time left = "at risk"
    public static final double AT_RISK_FRACTION = 0.25;

    public record Deadlines(Instant responseDue, Instant resolutionDue) {}

    public static Deadlines deadlines(Instant createdAt, SlaPolicy policy) {
        return new Deadlines(
                createdAt.plus(Duration.ofMinutes(policy.responseMinutes())),
                createdAt.plus(Duration.ofMinutes(policy.resolutionMinutes())));
    }

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
