package am.cybersim.security;

import am.cybersim.config.AppProperties;
import am.cybersim.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Issues signed access tokens. Validation is done by Spring Security's resource-server filter
 * using the {@code JwtDecoder} from {@link JwtConfig}.
 *
 * <p>Claims: {@code sub} = user id, {@code email}, {@code role}, {@code name}, standard {@code iss/iat/exp}.
 * The token deliberately contains no sensitive data — JWT payloads are only encoded, not encrypted.
 */
@Service
public class JwtTokenService {

    static final String CLAIM_ROLE = "role";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_NAME = "name";

    private final JwtEncoder encoder;
    private final AppProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, AppProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.jwt().expirationMinutes()));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_NAME, user.getDisplayName())
                .claim(CLAIM_ROLE, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
