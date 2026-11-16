package com.campusdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "campusdesk")
public record CampusDeskProperties(Path dataDir, boolean allowStudentSignup) {
    public CampusDeskProperties {
        if (dataDir == null) dataDir = Path.of("./data");
    }
}
