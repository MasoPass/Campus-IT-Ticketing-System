package com.campusdesk.service;

import com.campusdesk.model.Priority;
import com.campusdesk.model.SlaPolicy;
import com.campusdesk.model.SlaState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlaCalculatorTest {

    private static final Instant CREATED = Instant.parse("2026-11-12T10:00:00Z");
    private static final SlaPolicy HIGH = new SlaPolicy(Priority.HIGH, 240, 1440); // 4h / 24h

    @Test
    void deadlinesAreCreatedPlusTargets() {
        var d = SlaCalculator.deadlines(CREATED, HIGH);
        assertEquals(Instant.parse("2026-11-12T14:00:00Z"), d.responseDue());
        assertEquals(Instant.parse("2026-11-13T10:00:00Z"), d.resolutionDue());
    }

    @Test
    void openTicketStates() {
        Instant due = SlaCalculator.deadlines(CREATED, HIGH).resolutionDue();
        assertEquals(SlaState.ON_TRACK, SlaCalculator.state(CREATED, due, null, CREATED.plus(Duration.ofHours(1))));
        assertEquals(SlaState.AT_RISK, SlaCalculator.state(CREATED, due, null, CREATED.plus(Duration.ofHours(20))));
        assertEquals(SlaState.OVERDUE, SlaCalculator.state(CREATED, due, null, CREATED.plus(Duration.ofHours(25))));
    }

    @Test
    void resolvedTicketStates() {
        Instant due = SlaCalculator.deadlines(CREATED, HIGH).resolutionDue();
        assertEquals(SlaState.MET, SlaCalculator.state(CREATED, due, CREATED.plus(Duration.ofHours(2)), null));
        assertEquals(SlaState.MISSED, SlaCalculator.state(CREATED, due, CREATED.plus(Duration.ofDays(2)), null));
    }

    @Test
    void policyMustBeSensible() {
        assertThrows(IllegalArgumentException.class, () -> new SlaPolicy(Priority.LOW, 100, 50));
        assertThrows(IllegalArgumentException.class, () -> new SlaPolicy(Priority.LOW, 0, 50));
    }

    @Test
    void timestampFormatAlwaysHasMilliseconds() {
        assertEquals("2026-11-12T10:00:00.000Z", Times.format(CREATED));
    }
}
