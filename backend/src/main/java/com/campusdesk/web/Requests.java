package com.campusdesk.web;

import com.campusdesk.model.Priority;
import com.campusdesk.model.Role;
import com.campusdesk.model.TicketStatus;
import jakarta.validation.constraints.NotNull;

/** JSON request bodies. Detailed rules (lengths, formats) live in Validation.java. */
public final class Requests {
    private Requests() {}

    public record Login(String email, String password) {}

    public record Setup(String fullName, String email, String password) {}

    public record Register(String fullName, String email, String studentNumber, String password) {}

    public record ChangePassword(String currentPassword, String newPassword) {}

    public record StatusChange(@NotNull TicketStatus status, String resolution) {}

    public record PriorityChange(@NotNull Priority priority) {}

    /** assigneeId null = unassign. */
    public record Assign(Long assigneeId) {}

    public record CategoryChange(@NotNull Long categoryId) {}

    public record NewComment(String body, boolean internal) {}

    public record NewUser(String fullName, String email, @NotNull Role role, String studentNumber, String password) {}

    public record UpdateUser(String fullName, @NotNull Role role, String studentNumber, boolean active) {}

    public record ResetPassword(String password) {}

    public record CategoryBody(String name, Boolean active) {}

    public record SlaBody(int responseMinutes, int resolutionMinutes) {}
}
