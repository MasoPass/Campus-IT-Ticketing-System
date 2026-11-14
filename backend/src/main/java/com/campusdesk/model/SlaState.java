package com.campusdesk.model;

/** Where a ticket stands against its resolution deadline. */
public enum SlaState {
    /** Open, more than 25% of the allowed time left. */
    ON_TRACK,
    /** Open, less than 25% of the allowed time left. */
    AT_RISK,
    /** Open and past its deadline. */
    OVERDUE,
    /** Resolved before the deadline. */
    MET,
    /** Resolved, but after the deadline. */
    MISSED
}
