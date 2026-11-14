package com.campusdesk.model;

/** Response and resolution targets (minutes) for one priority. */
public record SlaPolicy(Priority priority, int responseMinutes, int resolutionMinutes) {
    public SlaPolicy {
        if (responseMinutes <= 0 || resolutionMinutes < responseMinutes) {
            throw new IllegalArgumentException("Resolution time must be at least the response time, and both above 0");
        }
    }
}
