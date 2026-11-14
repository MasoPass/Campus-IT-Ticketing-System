package com.campusdesk.repo;

import com.campusdesk.model.Role;
import com.campusdesk.model.User;
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
public class UserRepository {

    private static final RowMapper<User> MAPPER = (rs, i) -> new User(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("full_name"),
            Role.valueOf(rs.getString("role")),
            rs.getString("student_number"),
            rs.getString("password_hash"),
            rs.getInt("active") == 1,
            rs.getString("created_at"),
            rs.getString("last_login_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public UserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findById(long id) {
        return jdbc.query("SELECT * FROM users WHERE id = :id", new MapSqlParameterSource("id", id), MAPPER)
                .stream().findFirst();
    }

    /** Case-insensitive (the column is COLLATE NOCASE). */
    public Optional<User> findByEmail(String email) {
        return jdbc.query("SELECT * FROM users WHERE email = :email", new MapSqlParameterSource("email", email.trim()), MAPPER)
                .stream().findFirst();
    }

    public boolean studentNumberTaken(String number) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE student_number = :n",
                new MapSqlParameterSource("n", number), Integer.class);
        return n != null && n > 0;
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM users", new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    /** Staff first, then by name. Optional role filter. */
    public List<User> findAll(Role role) {
        String where = role == null ? "" : " WHERE role = :role";
        return jdbc.query("SELECT * FROM users" + where
                        + " ORDER BY CASE role WHEN 'ADMIN' THEN 0 WHEN 'TECHNICIAN' THEN 1 ELSE 2 END, full_name COLLATE NOCASE",
                new MapSqlParameterSource("role", role == null ? null : role.name()), MAPPER);
    }

    /** Active technicians and admins - the people a ticket can be assigned to. */
    public List<User> findAssignable() {
        return jdbc.query("""
                        SELECT * FROM users WHERE active = 1 AND role IN ('TECHNICIAN', 'ADMIN')
                        ORDER BY full_name COLLATE NOCASE
                        """, new MapSqlParameterSource(), MAPPER);
    }

    public long insert(String email, String fullName, Role role, String studentNumber, String passwordHash, String now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                        INSERT INTO users (email, full_name, role, student_number, password_hash, created_at)
                        VALUES (:email, :name, :role, :sn, :hash, :now)
                        """,
                new MapSqlParameterSource()
                        .addValue("email", email.trim()).addValue("name", fullName.trim())
                        .addValue("role", role.name()).addValue("sn", studentNumber)
                        .addValue("hash", passwordHash).addValue("now", now),
                keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public void update(long id, String fullName, Role role, String studentNumber, boolean active) {
        jdbc.update("""
                        UPDATE users SET full_name = :name, role = :role, student_number = :sn, active = :active
                        WHERE id = :id
                        """,
                new MapSqlParameterSource().addValue("id", id).addValue("name", fullName.trim())
                        .addValue("role", role.name()).addValue("sn", studentNumber).addValue("active", active ? 1 : 0));
    }

    public void updatePassword(long id, String passwordHash) {
        jdbc.update("UPDATE users SET password_hash = :h WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("h", passwordHash));
    }

    public void touchLogin(long id, String now) {
        jdbc.update("UPDATE users SET last_login_at = :now WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("now", now));
    }

    public long countActiveAdmins() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND active = 1",
                new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }
}
