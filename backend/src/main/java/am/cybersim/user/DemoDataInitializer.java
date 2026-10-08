package am.cybersim.user;

import am.cybersim.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Creates one admin account for demonstrations when {@code APP_DEMO_DATA_ENABLED=true}.
 * Passwords come from environment variables; nothing is created if a password is missing,
 * so no default credentials ever exist.
 */
@Component
@Order(10)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final AppProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataInitializer(AppProperties properties, UserRepository users, PasswordEncoder passwordEncoder,
                               Clock clock) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Demo demo = properties.demo();
        if (demo == null || !demo.enabled()) {
            return;
        }
        createIfMissing(demo.adminEmail(), demo.adminPassword(), "Platform Admin", Role.ADMIN);
    }

    private void createIfMissing(String email, String password, String displayName, Role role) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("Demo {} account not created: e-mail or password environment variable is empty", role);
            return;
        }
        String normalized = User.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            return;
        }
        users.save(new User(normalized, displayName, passwordEncoder.encode(password), role, clock.instant()));
        log.info("Created demo {} account {}", role, normalized);
    }
}
