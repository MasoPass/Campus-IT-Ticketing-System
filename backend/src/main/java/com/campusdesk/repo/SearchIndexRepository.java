package com.campusdesk.repo;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

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
