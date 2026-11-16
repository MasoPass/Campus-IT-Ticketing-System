package com.campusdesk.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

        // SQLite only has one writer, so a small pool is enough
        cfg.setMaximumPoolSize(4);

        // foreign keys are off by default in SQLite, plus WAL mode and a 5s busy timeout
        cfg.addDataSourceProperty("foreign_keys", "true");
        cfg.addDataSourceProperty("journal_mode", "WAL");
        cfg.addDataSourceProperty("busy_timeout", "5000");
        cfg.addDataSourceProperty("synchronous", "NORMAL");
        cfg.setConnectionInitSql("PRAGMA foreign_keys = ON");

        return new HikariDataSource(cfg);
    }
}
