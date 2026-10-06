package am.cybersim.user.dto;

import am.cybersim.user.Role;
import am.cybersim.user.User;

import java.time.Instant;

/** Public view of a user — never contains the password hash. */
public record UserDto(Long id, String email, String displayName, Role role, boolean enabled,
                      Instant createdAt, Instant lastLoginAt) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.isEnabled(), user.getCreatedAt(), user.getLastLoginAt());
    }
}
