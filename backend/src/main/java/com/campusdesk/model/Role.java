package com.campusdesk.model;

public enum Role {
    /** Submits tickets and follows their own tickets. */
    STUDENT,
    /** Works tickets: status, comments, internal notes, resolution. Can take unassigned tickets. */
    TECHNICIAN,
    /** Everything a technician can do, plus assign, prioritise, manage users and settings. */
    ADMIN;

    public boolean isStaff() {
        return this == TECHNICIAN || this == ADMIN;
    }
}
