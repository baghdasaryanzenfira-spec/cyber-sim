package am.cybersim.security;

import am.cybersim.common.ApiError;
import am.cybersim.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

/**
 * Authenticates the learner module on {@code /api/learner/**} with a static service key (ADR-13).
 *
 * <p>Why a key and not a JWT: the learner module is a system account with exactly one permission set, no
 * interactive login and no expiry ceremony — a shared secret in both services' environments is the simplest
 * mechanism that still keeps the surface closed (401 without it, 503 when the operator has not configured one).
 * The comparison is constant-time, and the key never appears in logs or error bodies.
 */
@Component
public class LearnerApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String ROLE = "LEARNER";

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public LearnerApiKeyFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/learner/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String configured = properties.learner() == null ? null : properties.learner().apiKey();
        if (configured == null || configured.isBlank()) {
            write(request, response, HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_DISABLED",
                    "The learner integration is disabled: LEARNER_API_KEY is not configured");
            return;
        }
        String presented = request.getHeader(HEADER);
        if (presented == null || !MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8))) {
            write(request, response, HttpStatus.UNAUTHORIZED, "INVALID_API_KEY",
                    "A valid " + HEADER + " header is required");
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken("learner-module", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                       String code, String message) throws IOException {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message,
                request.getRequestURI(), List.of());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
