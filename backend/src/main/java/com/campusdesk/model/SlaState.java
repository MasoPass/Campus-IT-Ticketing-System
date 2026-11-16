package com.campusdesk.model;

public enum SlaState {
    // more than 25% of the time left
    ON_TRACK,
    // less than 25% left
    AT_RISK,
    OVERDUE,
    MET,
    MISSED
}
