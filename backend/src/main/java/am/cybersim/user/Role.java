package am.cybersim.user;

/**
 * Fixed set of roles. Stored as a string column with a CHECK constraint (see 05-database-design §3.1).
 */
public enum Role {
    STUDENT,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
