package com.campusdesk.model;

// a message or a system event on the timeline
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

    // never shown to students
    public enum Visibility { PUBLIC, INTERNAL }
}
