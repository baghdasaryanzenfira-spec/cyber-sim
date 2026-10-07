package am.cybersim.ai;

import am.cybersim.ai.dto.AiDtos.AiSource;
import am.cybersim.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Translates a single piece of on-screen text on demand. Available to any signed-in user, because the reader who
 * needs a log line translated is the student, not the administrator.
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    public record TranslateRequest(
            @NotBlank @Size(max = TranslationService.MAX_SOURCE_CHARS) String text,
            @NotBlank @Size(max = 10) String language) {
    }

    public record TranslateResponse(String text, AiSource source) {
    }

    @PostMapping("/translate")
    @Operation(summary = "Translate one piece of displayed text; the result is not stored")
    public TranslateResponse translate(AuthUser user, @Valid @RequestBody TranslateRequest request) {
        var result = translationService.translate(user.id(), request.text(), request.language());
        return new TranslateResponse(result.text(), result.source());
    }
}
