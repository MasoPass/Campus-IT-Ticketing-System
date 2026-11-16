package com.campusdesk.config;

import com.campusdesk.security.LoginThrottle;
import com.campusdesk.security.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

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
