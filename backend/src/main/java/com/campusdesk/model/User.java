package com.campusdesk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** A user row. The password hash is never sent to the browser (@JsonIgnore). */
public record User(
        long id,
        String email,
        String fullName,
        Role role,
        String studentNumber,
        @JsonIgnore String passwordHash,
        boolean active,
        String createdAt,
        String lastLoginAt
) {}
