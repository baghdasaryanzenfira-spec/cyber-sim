package am.cybersim.security;

import am.cybersim.user.Role;

/**
 * The authenticated caller, resolved from the validated JWT and injected into controller methods
 * (see {@link AuthUserArgumentResolver}). Services receive the id instead of trusting any id from the request body.
 */
public record AuthUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
