package com.campusdesk.repo;

import com.campusdesk.model.DeviceType;
import com.campusdesk.model.PageResult;
import com.campusdesk.model.Priority;
import com.campusdesk.model.Ticket;
import com.campusdesk.model.TicketQuery;
import com.campusdesk.model.TicketStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class TicketRepository {

    /** Shorthand used in several queries. */
    static final String OPEN = "t.status NOT IN ('RESOLVED', 'CLOSED')";

    /*
     * Tickets joined to the three names the UI shows. Category and requester are required
     * (INNER JOIN); the assignee may be missing, so it's a LEFT JOIN - otherwise unassigned
     * tickets would silently disappear from every list.
     */
    private static final String SELECT = """
            SELECT t.*,
                   c.name           AS category_name,
                   r.full_name      AS requester_name,
                   r.email          AS requester_email,
                   r.student_number AS requester_student_number,
                   a.full_name      AS assignee_name,
                   (SELECT COUNT(*) FROM ticket_comments m
                     WHERE m.ticket_id = t.id AND m.kind = 'MESSAGE' AND m.visibility = 'PUBLIC') AS message_count
            FROM tickets t
            JOIN categories c ON c.id = t.category_id
            JOIN users r      ON r.id = t.requester_id
            LEFT JOIN users a ON a.id = t.assignee_id
            """;

    private static final RowMapper<Ticket> MAPPER = (rs, i) -> new Ticket(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getLong("category_id"),
            rs.getString("category_name"),
            Priority.valueOf(rs.getString("priority")),
            TicketStatus.valueOf(rs.getString("status")),
            rs.getLong("requester_id"),
            rs.getString("requester_name"),
            rs.getString("requester_email"),
            rs.getString("requester_student_number"),
            Rows.longOrNull(rs, "assignee_id"),
            rs.getString("assignee_name"),
            DeviceType.valueOf(rs.getString("device_type")),
            rs.getString("device_details"),
            rs.getString("building"),
            rs.getString("room"),
            rs.getString("resolution"),
            rs.getString("created_at"),
            rs.getString("updated_at"),
            rs.getString("first_response_at"),
            rs.getString("resolved_at"),
            rs.getString("closed_at"),
            rs.getString("response_due_at"),
            rs.getString("resolution_due_at"),
            rs.getInt("message_count"),
            null);

    private final NamedParameterJdbcTemplate jdbc;

    public TicketRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record NewTicket(String title, String description, long categoryId, Priority priority, long requesterId,
                            DeviceType deviceType, String deviceDetails, String building, String room,
                            String now, String responseDue, String resolutionDue) {}

    public long insert(NewTicket t) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                        INSERT INTO tickets (title, description, category_id, priority, status, requester_id,
                                             device_type, device_details, building, room,
                                             created_at, updated_at, response_due_at, resolution_due_at)
                        VALUES (:title, :description, :category, :priority, 'NEW', :requester,
                                :deviceType, :deviceDetails, :building, :room,
                                :now, :now, :responseDue, :resolutionDue)
                        """,
                new MapSqlParameterSource()
                        .addValue("title", t.title()).addValue("description", t.description())
                        .addValue("category", t.categoryId()).addValue("priority", t.priority().name())
                        .addValue("requester", t.requesterId()).addValue("deviceType", t.deviceType().name())
                        .addValue("deviceDetails", t.deviceDetails()).addValue("building", t.building())
                        .addValue("room", t.room()).addValue("now", t.now())
                        .addValue("responseDue", t.responseDue()).addValue("resolutionDue", t.resolutionDue()),
                keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public Optional<Ticket> findById(long id) {
        return jdbc.query(SELECT + " WHERE t.id = :id", new MapSqlParameterSource("id", id), MAPPER)
                .stream().findFirst();
    }

    /**
     * Status change. resolved_at / closed_at are set when entering those states and cleared
     * when a ticket is reopened, so reports only count the final resolution.
     */
    public void updateStatus(long id, TicketStatus status, String resolution, String now) {
        jdbc.update("""
                        UPDATE tickets SET
                            status      = :status,
                            resolution  = COALESCE(:resolution, resolution),
                            resolved_at = CASE WHEN :status = 'RESOLVED' THEN :now
                                               WHEN :status = 'CLOSED'   THEN COALESCE(resolved_at, :now)
                                               ELSE NULL END,
                            closed_at   = CASE WHEN :status = 'CLOSED' THEN :now ELSE NULL END,
                            updated_at  = :now
                        WHERE id = :id
                        """,
                new MapSqlParameterSource().addValue("id", id).addValue("status", status.name())
                        .addValue("resolution", resolution).addValue("now", now));
    }

    public void updatePriority(long id, Priority priority, String responseDue, String resolutionDue, String now) {
        jdbc.update("""
                        UPDATE tickets SET priority = :p, response_due_at = :rd, resolution_due_at = :rsd, updated_at = :now
                        WHERE id = :id
                        """,
                new MapSqlParameterSource().addValue("id", id).addValue("p", priority.name())
                        .addValue("rd", responseDue).addValue("rsd", resolutionDue).addValue("now", now));
    }

    public void updateAssignee(long id, Long assigneeId, TicketStatus status, String now) {
        jdbc.update("UPDATE tickets SET assignee_id = :a, status = :s, updated_at = :now WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("a", assigneeId)
                        .addValue("s", status.name()).addValue("now", now));
    }

    public void updateCategory(long id, long categoryId, String now) {
        jdbc.update("UPDATE tickets SET category_id = :c, updated_at = :now WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("c", categoryId).addValue("now", now));
    }

    /**
     * Records the FIRST time IT responded. COALESCE keeps the existing value if there is
     * one, so calling this on every reply is safe and needs no "if" in Java.
     */
    public void markFirstResponse(long id, String now) {
        jdbc.update("UPDATE tickets SET first_response_at = COALESCE(first_response_at, :now), updated_at = :now WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("now", now));
    }

    public void touch(long id, String now) {
        jdbc.update("UPDATE tickets SET updated_at = :now WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("now", now));
    }

    /**
     * The ticket list. The WHERE clause is built from only the filters that are set; every
     * user value is a named parameter, never pasted into the SQL string (no SQL injection).
     */
    public PageResult<Ticket> search(TicketQuery q, String now) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("now", now);

        if (!q.statuses().isEmpty()) {
            where.append(" AND t.status IN (:statuses)");
            p.addValue("statuses", q.statuses().stream().map(Enum::name).toList());
        }
        if (!q.priorities().isEmpty()) {
            where.append(" AND t.priority IN (:priorities)");
            p.addValue("priorities", q.priorities().stream().map(Enum::name).toList());
        }
        if (q.categoryId() != null) {
            where.append(" AND t.category_id = :category");
            p.addValue("category", q.categoryId());
        }
        switch (q.assigneeMode()) {
            case UNASSIGNED -> where.append(" AND t.assignee_id IS NULL");
            case USER -> {
                where.append(" AND t.assignee_id = :assignee");
                p.addValue("assignee", q.assigneeId());
            }
            case ANY -> { }
        }
        if (q.requesterId() != null) {
            where.append(" AND t.requester_id = :requester");
            p.addValue("requester", q.requesterId());
        }
        if (q.overdueOnly()) {
            // Matches the partial index idx_tickets_open_due.
            where.append(" AND ").append(OPEN).append(" AND t.resolution_due_at < :now");
        }
        String fts = Rows.ftsQuery(q.text());
        if (fts != null) {
            where.append(" AND t.id IN (SELECT rowid FROM tickets_fts WHERE tickets_fts MATCH :fts)");
            p.addValue("fts", fts);
        }

        long total = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tickets t" + where, p, Long.class));

        // CASE turns the priority text into a sortable number. Closed work sinks to the bottom
        // of the "due" sort because its deadline no longer matters.
        String orderBy = switch (q.sort()) {
            case NEWEST -> " ORDER BY t.id DESC";
            case OLDEST -> " ORDER BY t.id ASC";
            case UPDATED -> " ORDER BY t.updated_at DESC, t.id DESC";
            case PRIORITY -> " ORDER BY CASE t.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,"
                    + " t.resolution_due_at";
            case DUE -> " ORDER BY CASE WHEN " + OPEN + " THEN 0 ELSE 1 END, t.resolution_due_at";
        };

        p.addValue("limit", q.size()).addValue("offset", (long) q.page() * q.size());
        List<Ticket> items = jdbc.query(SELECT + where + orderBy + " LIMIT :limit OFFSET :offset", p, MAPPER);
        return new PageResult<>(items, q.page(), q.size(), total);
    }

    /**
     * The sidebar badges in ONE query (conditional aggregation): open, unassigned, mine,
     * overdue and waiting-on-user counts, instead of five separate COUNT queries.
     */
    public Map<String, Object> queueCounts(long userId, String now) {
        return jdbc.queryForMap("""
                        SELECT
                          COUNT(CASE WHEN %1$s THEN 1 END)                                     AS open,
                          COUNT(CASE WHEN %1$s AND t.assignee_id IS NULL THEN 1 END)            AS unassigned,
                          COUNT(CASE WHEN %1$s AND t.assignee_id = :me THEN 1 END)              AS mine,
                          COUNT(CASE WHEN %1$s AND t.resolution_due_at < :now THEN 1 END)       AS overdue,
                          COUNT(CASE WHEN t.status = 'WAITING_ON_USER' THEN 1 END)              AS waiting
                        FROM tickets t
                        """.formatted(OPEN),
                new MapSqlParameterSource().addValue("me", userId).addValue("now", now));
    }
}
