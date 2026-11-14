package com.campusdesk.config;

import com.campusdesk.security.LoginThrottle;
import com.campusdesk.security.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Plain objects the rest of the app gets injected.
 *
 * The Clock is the interesting one: services ask the injected Clock for "now" instead of
 * calling Instant.now() directly. In tests you can pass a fixed or fake clock, which makes
 * time-based logic (SLA deadlines, overdue, lockouts) testable without waiting real time.
 */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordHasher passwordHasher() {
        return new PasswordHasher();
    }

    @Bean
    public LoginThrottle loginThrottle(Clock clock) {
        return new LoginThrottle(clock);
    }
}
