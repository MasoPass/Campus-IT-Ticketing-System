package com.campusdesk.security;

import com.campusdesk.error.ApiException;
import com.campusdesk.model.Role;
import com.campusdesk.model.User;

import java.util.Arrays;

/**
 * The logged-in user for the current request. Controllers just declare a
 * {@code CurrentUser me} parameter and CurrentUserArgumentResolver fills it in.
 */
public record CurrentUser(long id, String fullName, String email, Role role) {

    public static CurrentUser of(User u) {
        return new CurrentUser(u.id(), u.fullName(), u.email(), u.role());
    }

    public boolean isStaff() {
        return role.isStaff();
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Throws 403 unless the user has one of these roles. */
    public void require(Role... allowed) {
        if (Arrays.asList(allowed).contains(role)) return;
        throw ApiException.forbidden("You don't have permission to do that");
    }

    public void requireStaff() {
        require(Role.TECHNICIAN, Role.ADMIN);
    }
}
