package com.campusdesk.repo;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Writes to the tickets_fts full-text index (rowid = ticket id). FTS5 rows are replaced
 * by delete-then-insert. Kept in sync from Java (TicketService) rather than with triggers,
 * because Spring's schema runner splits schema.sql on ';', which breaks trigger bodies.
 */
@Repository
public class SearchIndexRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SearchIndexRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(long ticketId, String body) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", ticketId).addValue("body", body);
        jdbc.update("DELETE FROM tickets_fts WHERE rowid = :id", p);
        jdbc.update("INSERT INTO tickets_fts (rowid, body) VALUES (:id, :body)", p);
    }
}
