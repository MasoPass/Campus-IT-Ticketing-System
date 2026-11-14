package com.campusdesk.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small JDBC / SQL helpers shared by the repositories. */
public final class Rows {
    private Rows() {}

    // JDBC's getLong() returns 0 for SQL NULL; these return a real null instead.

    static Long longOrNull(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    static Double doubleOrNull(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    /**
     * User text -> safe FTS5 query: "Wi-Fi drops!" -> "wi"* "fi"* "drops"*
     * Each word is quoted, so FTS operators typed by a user (OR, NEAR, -, :) are plain text.
     * The * makes it a prefix match. Returns null if there's nothing searchable.
     */
    public static String ftsQuery(String text) {
        if (text == null) return null;
        List<String> terms = new ArrayList<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) terms.add("\"" + word + "\"*");
        }
        return terms.isEmpty() ? null : String.join(" ", terms);
    }
}
