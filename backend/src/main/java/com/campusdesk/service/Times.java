package com.campusdesk.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Every timestamp in the database uses exactly this format (UTC, millisecond precision):
 *     2026-11-12T19:04:05.123Z
 * Because the format never varies, comparing the strings compares the times, so SQL can do
 * "resolution_due_at < :now" and ORDER BY without converting anything.
 * (Instant.toString() is NOT safe for this: it drops ".000" when milliseconds are zero.)
 */
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
