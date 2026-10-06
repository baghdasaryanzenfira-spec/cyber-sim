package am.cybersim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A {@link Clock} bean instead of calling {@code Instant.now()} directly, so that time-dependent logic
 * (incident timeline timestamps, token expiry) can be tested with a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
