package am.cybersim.security;

import am.cybersim.common.ApiError;
import am.cybersim.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Spring Security configuration.
 *
 * <ul>
 *   <li>Stateless: no HTTP session, no CSRF token (the API is called with bearer tokens, not cookies).</li>
 *   <li>JWTs are validated by the OAuth2 resource-server filter; the {@code role} claim is mapped to
 *       {@code ROLE_ADMIN}.</li>
 *   <li>URL rules give a coarse first line of defence; data ownership is enforced in the services.</li>
 *   <li>401 and 403 responses use the same {@link ApiError} JSON format as all other errors.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/login",
            "/actuator/health/**", "/actuator/info",
            "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                            CorsConfigurationSource corsConfigurationSource,
                                            LearnerApiKeyFilter learnerApiKeyFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/learner/**").hasRole(LearnerApiKeyFilter.ROLE)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(learnerApiKeyFilter, BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(objectMapper, request, response, HttpStatus.UNAUTHORIZED,
                                        "UNAUTHORIZED", "Authentication required or token invalid"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(objectMapper, request, response, HttpStatus.FORBIDDEN,
                                        "FORBIDDEN", "You do not have permission to access this resource")))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(objectMapper, request, response, HttpStatus.UNAUTHORIZED,
                                        "UNAUTHORIZED", "Authentication required or token invalid"))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(objectMapper, request, response, HttpStatus.FORBIDDEN,
                                        "FORBIDDEN", "You do not have permission to access this resource")));
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtTokenService.CLAIM_ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Delegating encoder: stores "{bcrypt}..." so the algorithm can be upgraded later without a migration.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static void writeError(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                                   HttpStatus status, String code, String message) throws IOException {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message,
                request.getRequestURI(), List.of());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), body);
    }
}
