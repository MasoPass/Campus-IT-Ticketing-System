package com.campusdesk.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Rows {
    private Rows() {}

    // getLong() gives 0 for NULL, these give null instead

    static Long longOrNull(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    static Double doubleOrNull(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    // "Wi-Fi drops!" -> "wi"* "fi"* "drops"*
    public static String ftsQuery(String text) {
        if (text == null) return null;
        List<String> terms = new ArrayList<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) terms.add("\"" + word + "\"*");
        }
        return terms.isEmpty() ? null : String.join(" ", terms);
    }
}
