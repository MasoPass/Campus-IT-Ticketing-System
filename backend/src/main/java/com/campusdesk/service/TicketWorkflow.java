package com.campusdesk.service;

import com.campusdesk.model.Role;
import com.campusdesk.model.TicketStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.campusdesk.model.TicketStatus.ASSIGNED;
import static com.campusdesk.model.TicketStatus.CLOSED;
import static com.campusdesk.model.TicketStatus.IN_PROGRESS;
import static com.campusdesk.model.TicketStatus.NEW;
import static com.campusdesk.model.TicketStatus.RESOLVED;
import static com.campusdesk.model.TicketStatus.WAITING_ON_USER;

/**
 * The ticket lifecycle as a FINITE STATE MACHINE: a fixed set of states and a table of
 * which moves are legal. Keeping the rules in one table (instead of if-statements spread
 * through the code) makes them easy to read, test and change.
 *
 *            ┌──────────── assign ───────────┐
 *   NEW ─────┴─► ASSIGNED ─► IN_PROGRESS ◄─► WAITING_ON_USER
 *                    │            │                 │
 *                    └────────────┴──► RESOLVED ◄───┘
 *                                       │    ▲
 *                              confirm  ▼    │ reopen
 *                                     CLOSED ┘ (admin)
 *
 * Who may make each move depends on the role (see allowedFor).
 * Pure Java: no Spring, no database.
 */
public final class TicketWorkflow {
    private TicketWorkflow() {}

    /** Every legal move, regardless of who makes it. EnumMap = array indexed by enum ordinal: tiny and fast. */
    private static final Map<TicketStatus, Set<TicketStatus>> GRAPH = new EnumMap<>(TicketStatus.class);

    static {
        GRAPH.put(NEW, EnumSet.of(ASSIGNED, IN_PROGRESS, CLOSED));
        GRAPH.put(ASSIGNED, EnumSet.of(IN_PROGRESS, WAITING_ON_USER, RESOLVED, CLOSED));
        GRAPH.put(IN_PROGRESS, EnumSet.of(WAITING_ON_USER, RESOLVED, CLOSED));
        GRAPH.put(WAITING_ON_USER, EnumSet.of(IN_PROGRESS, RESOLVED, CLOSED));
        GRAPH.put(RESOLVED, EnumSet.of(CLOSED, IN_PROGRESS));
        GRAPH.put(CLOSED, EnumSet.of(IN_PROGRESS));
    }

    /**
     * The statuses this person may move the ticket to right now.
     *
     * @param isRequester the user filed this ticket
     * @param isAssignee  the ticket is assigned to this user
     */
    public static Set<TicketStatus> allowedFor(TicketStatus from, Role role, boolean isRequester, boolean isAssignee) {
        Set<TicketStatus> legal = GRAPH.getOrDefault(from, Set.of());
        EnumSet<TicketStatus> out = EnumSet.noneOf(TicketStatus.class);
        for (TicketStatus to : legal) {
            if (permitted(from, to, role, isRequester, isAssignee)) out.add(to);
        }
        return Collections.unmodifiableSet(out);
    }

    public static boolean canMove(TicketStatus from, TicketStatus to, Role role, boolean isRequester, boolean isAssignee) {
        return allowedFor(from, role, isRequester, isAssignee).contains(to);
    }

    private static boolean permitted(TicketStatus from, TicketStatus to, Role role, boolean isRequester, boolean isAssignee) {
        // ASSIGNED is only entered by assigning someone (TicketService.assign), never picked directly.
        if (to == ASSIGNED) return false;

        switch (role) {
            case ADMIN:
                return true;   // any legal move
            case TECHNICIAN:
                // Technicians work the tickets assigned to them (or pick up a NEW one by starting it).
                // They can't close a ticket that was never resolved, and can't reopen a CLOSED one.
                // Starting a NEW ticket is how a technician picks it up (the service assigns it to them).
                if (from == CLOSED) return false;
                if (from == NEW) return to == IN_PROGRESS;
                if (!isAssignee) return false;
                return to != CLOSED || from == RESOLVED;
            case STUDENT:
                if (!isRequester) return false;
                // A student can confirm the fix, say it's still broken, or withdraw a ticket nobody's working on.
                if (from == RESOLVED) return to == CLOSED || to == IN_PROGRESS;
                return to == CLOSED && (from == NEW || from == ASSIGNED);
            default:
                return false;
        }
    }

    /** Moving into RESOLVED needs a written resolution; withdrawing/closing early does too (it explains why). */
    public static boolean requiresResolution(TicketStatus from, TicketStatus to) {
        return to == RESOLVED || (to == CLOSED && from != RESOLVED);
    }
}
