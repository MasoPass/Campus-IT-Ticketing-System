package com.campusdesk.service;

import com.campusdesk.error.ApiException;

import java.util.Locale;
import java.util.regex.Pattern;

/** Input rules shared by sign-up, user management and tickets. */
public final class Validation {
    private Validation() {}

    public static final int MIN_PASSWORD = 8;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern STUDENT_NUMBER = Pattern.compile("^[A-Za-z0-9-]{3,20}$");

    public static String email(String raw) {
        String e = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(e).matches() || e.length() > 200) throw ApiException.badRequest("Enter a valid email address");
        return e;
    }

    public static String password(String raw) {
        if (raw == null || raw.length() < MIN_PASSWORD) {
            throw ApiException.badRequest("Password must be at least " + MIN_PASSWORD + " characters");
        }
        if (raw.length() > 200) throw ApiException.badRequest("Password is too long");
        if (raw.isBlank()) throw ApiException.badRequest("Password can't be only spaces");
        return raw;
    }

    public static String name(String raw) {
        String n = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (n.isEmpty() || n.length() > 100) throw ApiException.badRequest("Enter a name (up to 100 characters)");
        return n;
    }

    public static String studentNumber(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!STUDENT_NUMBER.matcher(s).matches()) {
            throw ApiException.badRequest("Student number should be 3-20 letters, digits or dashes");
        }
        return s;
    }

    /** Trims; null/blank -> null when optional, error when required. */
    public static String text(String raw, String field, int max, boolean required) {
        String t = raw == null ? "" : raw.strip();
        if (t.isEmpty()) {
            if (required) throw ApiException.badRequest(field + " is required");
            return null;
        }
        if (t.length() > max) throw ApiException.badRequest(field + " is too long (max " + max + " characters)");
        return t;
    }
}
