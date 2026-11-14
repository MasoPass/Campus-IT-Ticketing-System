package com.campusdesk.model;

/** One timeline entry: a message someone wrote, or a system event (status change, assignment...). */
public record Comment(
        long id,
        long ticketId,
        long authorId,
        String authorName,
        Role authorRole,
        Kind kind,
        Visibility visibility,
        String body,
        String createdAt
) {
    public enum Kind { MESSAGE, EVENT }

    /** INTERNAL entries (technician notes, priority changes) are never shown to students. */
    public enum Visibility { PUBLIC, INTERNAL }
}
