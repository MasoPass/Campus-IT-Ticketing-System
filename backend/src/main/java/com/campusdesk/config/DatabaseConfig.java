package com.campusdesk.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds the connection pool by hand instead of using spring.datasource.* so we can
 * (1) create the data folder before SQLite tries to open the file, and
 * (2) set SQLite PRAGMAs on every connection.
 */
@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(CampusDeskProperties props) throws IOException {
        Path dataDir = props.dataDir().toAbsolutePath().normalize();
        Files.createDirectories(dataDir);
        Path dbFile = dataDir.resolve("campusdesk.db");

        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("campusdesk-sqlite");
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile);

        // SQLite allows many readers but only ONE writer at a time, so a big pool
        // gains nothing. A few connections let the UI read while another request writes.
        cfg.setMaximumPoolSize(4);

        //  foreign_keys - SQLite does NOT enforce FOREIGN KEY / ON DELETE CASCADE unless
        //                 this is switched on, per connection.
        //  journal_mode - WAL (write-ahead log) lets readers keep reading during a write.
        //  busy_timeout - wait up to 5s for a lock instead of failing with SQLITE_BUSY.
        cfg.addDataSourceProperty("foreign_keys", "true");
        cfg.addDataSourceProperty("journal_mode", "WAL");
        cfg.addDataSourceProperty("busy_timeout", "5000");
        cfg.addDataSourceProperty("synchronous", "NORMAL");
        cfg.setConnectionInitSql("PRAGMA foreign_keys = ON");

        return new HikariDataSource(cfg);
    }
}
