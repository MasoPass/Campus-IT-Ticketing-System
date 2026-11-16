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

public final class TicketWorkflow {
    private TicketWorkflow() {}

    // every legal move
    private static final Map<TicketStatus, Set<TicketStatus>> GRAPH = new EnumMap<>(TicketStatus.class);

    static {
        GRAPH.put(NEW, EnumSet.of(ASSIGNED, IN_PROGRESS, CLOSED));
        GRAPH.put(ASSIGNED, EnumSet.of(IN_PROGRESS, WAITING_ON_USER, RESOLVED, CLOSED));
        GRAPH.put(IN_PROGRESS, EnumSet.of(WAITING_ON_USER, RESOLVED, CLOSED));
        GRAPH.put(WAITING_ON_USER, EnumSet.of(IN_PROGRESS, RESOLVED, CLOSED));
        GRAPH.put(RESOLVED, EnumSet.of(CLOSED, IN_PROGRESS));
        GRAPH.put(CLOSED, EnumSet.of(IN_PROGRESS));
    }

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
        // ASSIGNED only happens by assigning someone
        if (to == ASSIGNED) return false;

        switch (role) {
            case ADMIN:
                return true;
            case TECHNICIAN:
                // techs work their own tickets or start a NEW one
                if (from == CLOSED) return false;
                if (from == NEW) return to == IN_PROGRESS;
                if (!isAssignee) return false;
                return to != CLOSED || from == RESOLVED;
            case STUDENT:
                if (!isRequester) return false;
                // confirm, reopen, or withdraw before anyone starts
                if (from == RESOLVED) return to == CLOSED || to == IN_PROGRESS;
                return to == CLOSED && (from == NEW || from == ASSIGNED);
            default:
                return false;
        }
    }

    // resolving or closing early needs a resolution
    public static boolean requiresResolution(TicketStatus from, TicketStatus to) {
        return to == RESOLVED || (to == CLOSED && from != RESOLVED);
    }
}
