package am.cybersim.user;

/**
 * Fixed set of roles. Stored as a string column with a CHECK constraint. The authoring platform is
 * administrator-only; trainee accounts belong to the separate learner-facing module.
 */
public enum Role {
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
