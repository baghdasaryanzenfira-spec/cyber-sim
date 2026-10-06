package am.cybersim.scenario.dto;

import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.ScenarioEnums.ActionPhase;
import am.cybersim.scenario.ScenarioEnums.Category;
import am.cybersim.scenario.ScenarioEnums.Difficulty;
import am.cybersim.scenario.ScenarioEnums.EventType;
import am.cybersim.scenario.ScenarioEnums.ResourceType;
import am.cybersim.scenario.ScenarioEnums.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * The complete, self-contained description of a scenario (ADR-5).
 *
 * <p>The same document is used for three entry points, all protected by the same validation
 * (Bean Validation here + cross-reference rules in {@code ScenarioDefinitionValidator}):
 * <ol>
 *   <li>seed files in {@code resources/scenarios/*.json},</li>
 *   <li>the admin scenario editor ({@code POST/PUT /api/admin/scenarios}),</li>
 *   <li>AI-generated scenario variations.</li>
 * </ol>
 */
public record ScenarioDefinition(
        @NotBlank @Pattern(regexp = KEY_PATTERN, message = "must be lower-case letters, digits and dashes")
        @Size(max = 100) String slug,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 500) String summary,
        @NotBlank @Size(max = 10_000) String description,
        @NotNull Difficulty difficulty,
        @NotNull Category category,
        @Min(1) @Max(600) int estimatedMinutes,
        @NotBlank @Size(max = 10_000) String incidentExplanation,
        @NotBlank @Size(max = 10_000) String recommendedSolution,
        @Min(0) @Max(50) int hintPenalty,
        @Min(0) @Max(50) int outOfOrderPenalty,
        boolean active,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 500) String> learningObjectives,
        @NotEmpty @Size(max = 50) List<@Valid @NotNull ResourceDef> resources,
        @NotEmpty @Size(max = 200) List<@Valid @NotNull EventDef> events,
        @NotEmpty @Size(max = 60) List<@Valid @NotNull ActionDef> actions,
        @Size(max = 20) List<@NotBlank @Size(max = 1000) String> hints) {

    public static final String KEY_PATTERN = "^[a-z0-9][a-z0-9-]{1,63}$";

    public ScenarioDefinition {
        hints = hints == null ? List.of() : hints;
    }

    public record ResourceDef(
            @NotBlank @Pattern(regexp = KEY_PATTERN) String key,
            @NotNull ResourceType type,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 40) String region,
            @NotBlank @Size(max = 40) String status,
            Map<String, Object> properties) {
    }

    public record EventDef(
            @NotBlank @Pattern(regexp = KEY_PATTERN) String key,
            @Min(0) @Max(604_800) int offsetSeconds,
            @NotNull EventType type,
            @NotBlank @Size(max = 60) String source,
            @NotNull Severity severity,
            String resourceKey,
            @NotBlank @Size(max = 4000) String message,
            Map<String, Object> details,
            boolean evidence,
            @Size(max = 2000) String evidenceNote,
            String revealedByActionKey) {
    }

    public record ActionDef(
            @NotBlank @Pattern(regexp = KEY_PATTERN) String key,
            @NotBlank @Size(max = 200) String label,
            @NotBlank @Size(max = 1000) String description,
            @NotNull ActionPhase phase,
            @NotNull ActionCategory category,
            String targetResourceKey,
            @NotNull ActionOutcome outcome,
            @Min(-100) @Max(100) int points,
            String prerequisiteActionKey,
            @Size(max = 40) String effectStatus,
            @NotBlank @Size(max = 4000) String resultMessage,
            @NotBlank @Size(max = 4000) String explanation) {
    }
}
