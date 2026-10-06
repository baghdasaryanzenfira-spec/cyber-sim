package am.cybersim.auth.dto;

import am.cybersim.user.dto.UserDto;

import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserDto user) {

    public static AuthResponse bearer(String token, Instant expiresAt, UserDto user) {
        return new AuthResponse(token, "Bearer", expiresAt, user);
    }
}
