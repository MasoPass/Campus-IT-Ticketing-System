package com.campusdesk.repo;

import com.campusdesk.model.Comment;
import com.campusdesk.model.Role;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CommentRepository {

    private static final RowMapper<Comment> MAPPER = (rs, i) -> new Comment(
            rs.getLong("id"),
            rs.getLong("ticket_id"),
            rs.getLong("author_id"),
            rs.getString("author_name"),
            Role.valueOf(rs.getString("author_role")),
            Comment.Kind.valueOf(rs.getString("kind")),
            Comment.Visibility.valueOf(rs.getString("visibility")),
            rs.getString("body"),
            rs.getString("created_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public CommentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long ticketId, long authorId, Comment.Kind kind, Comment.Visibility visibility,
                       String body, String now) {
        jdbc.update("""
                        INSERT INTO ticket_comments (ticket_id, author_id, kind, visibility, body, created_at)
                        VALUES (:t, :a, :k, :v, :b, :now)
                        """,
                new MapSqlParameterSource().addValue("t", ticketId).addValue("a", authorId)
                        .addValue("k", kind.name()).addValue("v", visibility.name())
                        .addValue("b", body).addValue("now", now));
    }

    /**
     * Timeline for a ticket, oldest first. Students pass includeInternal=false, so internal
     * notes are filtered out IN THE DATABASE QUERY - they never even reach the Java side
     * for a student request, which is safer than filtering later.
     */
    public List<Comment> forTicket(long ticketId, boolean includeInternal) {
        return jdbc.query("""
                        SELECT m.*, u.full_name AS author_name, u.role AS author_role
                        FROM ticket_comments m
                        JOIN users u ON u.id = m.author_id
                        WHERE m.ticket_id = :t
                        """ + (includeInternal ? "" : " AND m.visibility = 'PUBLIC'") + " ORDER BY m.id",
                new MapSqlParameterSource("t", ticketId), MAPPER);
    }

    /** Text of all messages (not events), for the search index. */
    public List<String> messageBodies(long ticketId) {
        return jdbc.queryForList("SELECT body FROM ticket_comments WHERE ticket_id = :t AND kind = 'MESSAGE' ORDER BY id",
                new MapSqlParameterSource("t", ticketId), String.class);
    }
}
