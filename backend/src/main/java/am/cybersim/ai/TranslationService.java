package am.cybersim.ai;

import am.cybersim.ai.AiGateway.AiResult;
import am.cybersim.ai.dto.AiDtos.AiSource;
import am.cybersim.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * On-demand translation of one piece of displayed text (ADR-12).
 *
 * <p>Nothing is stored. The English text stays the single source of truth in the database, and the translation
 * exists only in the response that produced it, shown beside the original. That keeps telemetry authentic — a log
 * line is still the log line a cloud platform emitted — while giving a reader who needs it a translation on
 * request, and it means scenarios authored later need no translation step at all.
 */
@Service
public class TranslationService {

    /** Target languages the UI offers. English is the authoring language, so it is never a target. */
    private static final Map<String, String> SUPPORTED = Map.of("hy", "Armenian");

    static final int MAX_SOURCE_CHARS = 4000;

    private final AiGateway gateway;
    private final AiOutputValidator validator;

    public TranslationService(AiGateway gateway, AiOutputValidator validator) {
        this.gateway = gateway;
        this.validator = validator;
    }

    public record TranslationResult(String text, AiSource source) {
    }

    public TranslationResult translate(Long userId, String text, String language) {
        String languageName = SUPPORTED.get(language == null ? "" : language.toLowerCase(Locale.ROOT));
        if (languageName == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LANGUAGE",
                    "Cannot translate into '" + language + "'. Supported: " + SUPPORTED.keySet());
        }
        String source = text == null ? "" : text.strip();
        if (source.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_TEXT", "There is nothing to translate.");
        }
        if (source.length() > MAX_SOURCE_CHARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TEXT_TOO_LONG",
                    "Text is longer than " + MAX_SOURCE_CHARS + " characters.");
        }

        AiResult<String> result = gateway.execute(new AiPayload.Translation(source, languageName),
                new AiGateway.CallContext(userId, null, null, abbreviate(source)),
                translated -> validator.validateTranslation(translated, source));
        return new TranslationResult(result.value(), result.source());
    }

    private static String abbreviate(String text) {
        return text.length() <= 120 ? text : text.substring(0, 117) + "...";
    }
}
