package com.campusdesk.repo;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Reporting queries for the dashboard. Each returns plain rows (List of Map) because they
 * go straight to JSON - no need for a record per chart.
 */
@Repository
public class DashboardRepository {

    private static final String OPEN = TicketRepository.OPEN;

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Headline numbers in one pass. */
    public Map<String, Object> summary(String now, String since) {
        return jdbc.queryForMap("""
                        SELECT
                          COUNT(CASE WHEN %1$s THEN 1 END)                                    AS open,
                          COUNT(CASE WHEN %1$s AND t.assignee_id IS NULL THEN 1 END)           AS unassigned,
                          COUNT(CASE WHEN %1$s AND t.resolution_due_at < :now THEN 1 END)      AS overdue,
                          COUNT(CASE WHEN t.created_at >= :since THEN 1 END)                   AS created30,
                          COUNT(CASE WHEN t.resolved_at >= :since THEN 1 END)                  AS resolved30,
                          -- julianday() turns a timestamp into a day number, so the
                          -- difference x 24 is the resolution time in hours.
                          ROUND(AVG(CASE WHEN t.resolved_at >= :since
                                    THEN (julianday(t.resolved_at) - julianday(t.created_at)) * 24 END), 1)
                                                                                               AS avg_resolution_hours,
                          ROUND(AVG(CASE WHEN t.first_response_at >= :since
                                    THEN (julianday(t.first_response_at) - julianday(t.created_at)) * 24 END), 1)
                                                                                               AS avg_response_hours,
                          ROUND(100.0 * COUNT(CASE WHEN t.resolved_at >= :since AND t.resolved_at <= t.resolution_due_at THEN 1 END)
                                / NULLIF(COUNT(CASE WHEN t.resolved_at >= :since THEN 1 END), 0), 1)
                                                                                               AS sla_met_percent
                        FROM tickets t
                        """.formatted(OPEN),
                new MapSqlParameterSource().addValue("now", now).addValue("since", since));
    }

    /** Open tickets per status (NEW, ASSIGNED, ...). */
    public List<Map<String, Object>> openByStatus() {
        return jdbc.queryForList("SELECT t.status AS label, COUNT(*) AS count FROM tickets t WHERE "
                + OPEN + " GROUP BY t.status", new MapSqlParameterSource());
    }

    /** Open tickets per priority, most urgent first. */
    public List<Map<String, Object>> openByPriority() {
        return jdbc.queryForList("SELECT t.priority AS label, COUNT(*) AS count FROM tickets t WHERE " + OPEN
                + " GROUP BY t.priority"
                + " ORDER BY CASE t.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END",
                new MapSqlParameterSource());
    }

    /** Tickets per category over the last 30 days (LEFT JOIN keeps categories with zero). */
    public List<Map<String, Object>> byCategory(String since) {
        return jdbc.queryForList("""
                        SELECT c.name AS label, COUNT(t.id) AS count
                        FROM categories c
                        LEFT JOIN tickets t ON t.category_id = c.id AND t.created_at >= :since
                        WHERE c.active = 1
                        GROUP BY c.id
                        ORDER BY count DESC, c.sort_order
                        """, new MapSqlParameterSource("since", since));
    }

    /**
     * Tickets created per day for the last 14 days.
     *
     * A plain GROUP BY date(created_at) would SKIP days with no tickets, leaving gaps in the
     * chart. So a RECURSIVE CTE first generates every date in the range (start from 13 days
     * ago, keep adding one day until today), then LEFT JOINs the tickets onto that calendar,
     * which gives every day a row, with 0 where nothing was filed.
     */
    public List<Map<String, Object>> createdPerDay(String now) {
        return jdbc.queryForList("""
                        WITH RECURSIVE days(day) AS (
                            SELECT date(:now, '-13 days')
                            UNION ALL
                            SELECT date(day, '+1 day') FROM days WHERE day < date(:now)
                        )
                        SELECT d.day AS label,
                               COUNT(t.id) AS created,
                               (SELECT COUNT(*) FROM tickets r WHERE date(r.resolved_at) = d.day) AS resolved
                        FROM days d
                        LEFT JOIN tickets t ON date(t.created_at) = d.day
                        GROUP BY d.day
                        ORDER BY d.day
                        """, new MapSqlParameterSource("now", now));
    }

    /**
     * Per-technician workload. RANK() is a WINDOW FUNCTION: unlike GROUP BY it doesn't
     * collapse rows, it adds a column computed across all rows - here, each person's rank
     * by tickets resolved in the last 30 days (ties share a rank).
     */
    public List<Map<String, Object>> technicianWorkload(String now, String since) {
        return jdbc.queryForList("""
                        SELECT u.id, u.full_name AS name, u.role,
                               COUNT(CASE WHEN %1$s THEN 1 END)                               AS open,
                               COUNT(CASE WHEN %1$s AND t.resolution_due_at < :now THEN 1 END) AS overdue,
                               COUNT(CASE WHEN t.resolved_at >= :since THEN 1 END)             AS resolved30,
                               RANK() OVER (ORDER BY COUNT(CASE WHEN t.resolved_at >= :since THEN 1 END) DESC) AS rank
                        FROM users u
                        LEFT JOIN tickets t ON t.assignee_id = u.id
                        WHERE u.role IN ('TECHNICIAN', 'ADMIN') AND u.active = 1
                        GROUP BY u.id
                        ORDER BY rank, open DESC, u.full_name
                        """.formatted(OPEN),
                new MapSqlParameterSource().addValue("now", now).addValue("since", since));
    }
}
