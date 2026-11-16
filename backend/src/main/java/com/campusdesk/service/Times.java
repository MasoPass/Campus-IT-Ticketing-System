package com.campusdesk.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

// fixed format so timestamps sort correctly as strings
public final class Times {
    private Times() {}

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    public static String format(Instant instant) {
        return instant == null ? null : FORMAT.format(instant);
    }

    public static Instant parse(String text) {
        return text == null ? null : Instant.parse(text);
    }
}
