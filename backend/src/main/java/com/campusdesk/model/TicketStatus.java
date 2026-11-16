package com.campusdesk.model;

import java.util.EnumSet;
import java.util.Set;

public enum TicketStatus {
    NEW, ASSIGNED, IN_PROGRESS, WAITING_ON_USER, RESOLVED, CLOSED;

    // still counts against the SLA
    public static final Set<TicketStatus> OPEN = EnumSet.of(NEW, ASSIGNED, IN_PROGRESS, WAITING_ON_USER);

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    // WAITING_ON_USER -> "Waiting on user"
    public String label() {
        String s = name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
