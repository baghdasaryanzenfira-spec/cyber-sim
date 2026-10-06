package am.cybersim.progress;

import am.cybersim.ai.dto.AiDtos.RecommendationsView;
import am.cybersim.progress.ProgressDtos.ProgressView;
import am.cybersim.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
@Tag(name = "Progress")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping("/me")
    @Operation(summary = "My learning progress: totals, categories, scenarios, score history")
    public ProgressView me(AuthUser user) {
        return progressService.progress(user.id());
    }

    @GetMapping("/me/recommendations")
    @Operation(summary = "AI learning recommendations based on my history")
    public RecommendationsView recommendations(AuthUser user) {
        return progressService.recommendations(user.id());
    }
}
