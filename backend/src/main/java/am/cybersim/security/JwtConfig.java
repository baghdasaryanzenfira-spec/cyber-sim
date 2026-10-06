package am.cybersim.security;

import am.cybersim.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Creates the HMAC-SHA256 key and the JWT encoder/decoder.
 *
 * <p>Security considerations:
 * <ul>
 *   <li>The secret comes only from {@code JWT_SECRET}; it must be at least 32 bytes (256 bits) for HS256.</li>
 *   <li>If no secret is configured (local development), a random key is generated at start-up.
 *       Tokens then become invalid after a restart, which is acceptable for development and avoids
 *       committing any default secret.</li>
 * </ul>
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtSigningKey(AppProperties properties) {
        String secret = properties.jwt().secret();
        byte[] keyBytes;
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET is not set - generating a random signing key. "
                    + "Tokens will not survive a restart. Set JWT_SECRET in production.");
            keyBytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(keyBytes);
        } else {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long");
            }
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, AppProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }
}
