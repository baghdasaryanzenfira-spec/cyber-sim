package am.cybersim.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registration input. There is intentionally no role field: new accounts are always students. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 2, max = 100) String displayName,
        @NotBlank @Size(min = 8, max = 100) String password) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", displayName=" + displayName + ", password=***]";
    }
}
