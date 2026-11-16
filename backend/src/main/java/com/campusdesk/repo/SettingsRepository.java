package com.campusdesk.repo;

import com.campusdesk.model.Category;
import com.campusdesk.model.Priority;
import com.campusdesk.model.SlaPolicy;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class SettingsRepository {

    private static final RowMapper<Category> CATEGORY = (rs, i) -> new Category(
            rs.getLong("id"), rs.getString("name"), rs.getInt("sort_order"), rs.getInt("active") == 1);

    private static final RowMapper<SlaPolicy> SLA = (rs, i) -> new SlaPolicy(
            Priority.valueOf(rs.getString("priority")), rs.getInt("response_minutes"), rs.getInt("resolution_minutes"));

    private final NamedParameterJdbcTemplate jdbc;

    public SettingsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Category> categories(boolean includeInactive) {
        return jdbc.query("SELECT * FROM categories" + (includeInactive ? "" : " WHERE active = 1")
                + " ORDER BY sort_order, name COLLATE NOCASE", new MapSqlParameterSource(), CATEGORY);
    }

    public Optional<Category> findCategory(long id) {
        return jdbc.query("SELECT * FROM categories WHERE id = :id", new MapSqlParameterSource("id", id), CATEGORY)
                .stream().findFirst();
    }

    public long insertCategory(String name) {
        KeyHolder keys = new GeneratedKeyHolder();
        // new categories go before "Other"
        jdbc.update("INSERT INTO categories (name, sort_order) VALUES (:name, 90)",
                new MapSqlParameterSource("name", name.trim()), keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public void updateCategory(long id, String name, boolean active) {
        jdbc.update("UPDATE categories SET name = :name, active = :active WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name.trim()).addValue("active", active ? 1 : 0));
    }

    public List<SlaPolicy> slaPolicies() {
        return jdbc.query("""
                SELECT * FROM sla_policies
                ORDER BY CASE priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END
                """, new MapSqlParameterSource(), SLA);
    }

    public SlaPolicy slaFor(Priority p) {
        return jdbc.queryForObject("SELECT * FROM sla_policies WHERE priority = :p",
                new MapSqlParameterSource("p", p.name()), SLA);
    }

    public void updateSla(SlaPolicy policy) {
        jdbc.update("UPDATE sla_policies SET response_minutes = :r, resolution_minutes = :res WHERE priority = :p",
                new MapSqlParameterSource().addValue("p", policy.priority().name())
                        .addValue("r", policy.responseMinutes()).addValue("res", policy.resolutionMinutes()));
    }

    // for the building autocomplete
    public List<String> knownBuildings() {
        return jdbc.queryForList("""
                        SELECT building FROM tickets
                        GROUP BY building COLLATE NOCASE
                        ORDER BY COUNT(*) DESC, building
                        LIMIT 50
                        """, new MapSqlParameterSource(), String.class);
    }
}
