package am.cybersim.integration;

import am.cybersim.integration.dto.IntegrationDtos.AdminAttemptDetail;
import am.cybersim.integration.dto.IntegrationDtos.AdminAttemptRow;
import am.cybersim.integration.dto.IntegrationDtos.PageView;
import am.cybersim.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrator's view of submitted student exams, including the per-attempt AI review (ADR-13). */
@RestController
@RequestMapping("/api/admin/exams")
@Tag(name = "Exams (administration)")
public class ExamAdminController {

    private final AttemptService attemptService;
    private final ReviewService reviewService;

    public ExamAdminController(AttemptService attemptService, ReviewService reviewService) {
        this.attemptService = attemptService;
        this.reviewService = reviewService;
    }

    @GetMapping
    @Operation(summary = "Submitted attempts, newest first (optionally filtered by scenario, paged)")
    public PageView<AdminAttemptRow> list(@RequestParam(required = false) Long scenarioId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return attemptService.list(scenarioId, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One attempt: submitted actions, verified result and the AI review if present")
    public AdminAttemptDetail detail(@PathVariable Long id) {
        return attemptService.detail(id);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Run the AI review for this attempt; replaces an earlier review")
    public AdminAttemptDetail review(AuthUser admin, @PathVariable Long id) {
        return reviewService.review(admin.id(), id);
    }
}
