package com.campusdesk.model;

public enum Role {
    STUDENT,
    TECHNICIAN,
    ADMIN;

    public boolean isStaff() {
        return this == TECHNICIAN || this == ADMIN;
    }
}
