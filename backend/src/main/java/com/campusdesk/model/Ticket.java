package com.campusdesk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Ticket(
        long id,
        String title,
        String description,
        long categoryId,
        String categoryName,
        Priority priority,
        TicketStatus status,
        long requesterId,
        String requesterName,
        String requesterEmail,
        String requesterStudentNumber,
        Long assigneeId,
        String assigneeName,
        DeviceType deviceType,
        String deviceDetails,
        String building,
        String room,
        String resolution,
        String createdAt,
        String updatedAt,
        String firstResponseAt,
        String resolvedAt,
        String closedAt,
        String responseDueAt,
        String resolutionDueAt,
        int messageCount,
        SlaState sla
) {
    public Ticket withSla(SlaState state) {
        return new Ticket(id, title, description, categoryId, categoryName, priority, status, requesterId,
                requesterName, requesterEmail, requesterStudentNumber, assigneeId, assigneeName, deviceType,
                deviceDetails, building, room, resolution, createdAt, updatedAt, firstResponseAt, resolvedAt,
                closedAt, responseDueAt, resolutionDueAt, messageCount, state);
    }

    // HD-00042
    @JsonProperty("number")
    public String number() {
        return String.format("HD-%05d", id);
    }
}
