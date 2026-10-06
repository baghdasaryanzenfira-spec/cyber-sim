package am.cybersim.common;

import java.time.Instant;
import java.util.List;

/**
 * The single error body returned by every endpoint, so the frontend can handle errors uniformly.
 *
 * @param code machine-readable error code (e.g. {@code INVALID_STATE_TRANSITION}); the frontend may
 *             branch on it, while {@code message} is meant for humans
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}
