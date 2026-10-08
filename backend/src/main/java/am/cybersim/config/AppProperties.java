package am.cybersim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * All application-specific configuration, bound from {@code app.*} properties which in turn are
 * filled from environment variables (see {@code application.yml} and {@code .env.example}).
 * No secret has a default value here.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        Demo demo,
        Ai ai,
        Scenarios scenarios,
        Learner learner) {

    public record Jwt(
            String secret,
            @DefaultValue("60") long expirationMinutes,
            @DefaultValue("cybersim") String issuer) {
    }

    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    }

    public record Demo(
            @DefaultValue("false") boolean enabled,
            String adminEmail,
            String adminPassword) {
    }

    public record Ai(
            @DefaultValue("mock") String provider,
            String anthropicApiKey,
            @DefaultValue("claude-opus-5-5") String model,
            @DefaultValue("low") String effort,
            @DefaultValue("30") int timeoutSeconds,
            @DefaultValue("2048") long maxTokens,
            @DefaultValue("true") boolean serverSideFallback) {
    }

    public record Scenarios(@DefaultValue("true") boolean seedEnabled) {
    }

    /** Service credentials of the learner module; blank = the integration endpoints answer 503. */
    public record Learner(String apiKey) {
    }
}
