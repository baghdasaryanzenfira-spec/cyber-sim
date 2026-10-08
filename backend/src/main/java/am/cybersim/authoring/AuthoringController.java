package am.cybersim.authoring;

import am.cybersim.ai.ScenarioVariationService;
import am.cybersim.ai.dto.AiDtos.AiSource;
import am.cybersim.authoring.AuthoringService.Evaluation;
import am.cybersim.authoring.AuthoringService.GeneratedScenario;
import am.cybersim.authoring.AuthoringService.VersionDetail;
import am.cybersim.authoring.AuthoringService.VersionSummary;
import am.cybersim.authoring.ScenarioAnalyzer.ValidationReport;
import am.cybersim.authoring.ScenarioGeneratorService.GenerateRequest;
import am.cybersim.authoring.ScenarioTestRunner.TestRunReport;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDtos.AdminScenarioDetail;
import am.cybersim.scenario.dto.ScenarioDtos.AdminScenarioSummary;
import am.cybersim.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administration API of the scenario authoring workflow. Access is restricted to {@code ROLE_ADMIN} by
 * {@code SecurityConfig} (URL rule on {@code /api/admin/**}). Scenario definitions are validated in
 * {@code ScenarioService} (the body is deliberately not annotated with {@code @Valid}, so that all problems are
 * reported together as one 422 response).
 */
@RestController
@RequestMapping("/api/admin/scenarios")
@Tag(name = "Scenario authoring")
public class AuthoringController {

    private final AuthoringService authoring;
    private final ScenarioVariationService variationService;

    public AuthoringController(AuthoringService authoring, ScenarioVariationService variationService) {
        this.authoring = authoring;
        this.variationService = variationService;
    }

    public record ArchiveRequest(boolean archived) {
    }

    public record PublishRequest(@Size(max = 500) String changeNote) {
    }

    public record VariationResponse(AdminScenarioDetail scenario, AiSource source) {
    }

    // ------------------------------------------------------------------ CRUD

    @GetMapping
    @Operation(summary = "All scenarios with their lifecycle status")
    public List<AdminScenarioSummary> list() {
        return authoring.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Scenario with its full editable definition")
    public AdminScenarioDetail get(@PathVariable Long id) {
        return authoring.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a scenario draft from a complete definition")
    public AdminScenarioDetail create(@RequestBody ScenarioDefinition definition) {
        return authoring.create(definition);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace the draft content (a published scenario becomes a draft again)")
    public AdminScenarioDetail update(@PathVariable Long id, @RequestBody ScenarioDefinition definition) {
        return authoring.update(id, definition);
    }

    @PatchMapping("/{id}/archive")
    @Operation(summary = "Archive or restore a scenario")
    public AdminScenarioDetail archive(@PathVariable Long id, @RequestBody ArchiveRequest request) {
        return authoring.setArchived(id, request.archived());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a never-published draft")
    public void delete(@PathVariable Long id) {
        authoring.delete(id);
    }

    // ------------------------------------------------------------------ generation

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a draft (timeline, commands, evidence, actions) from a scenario type")
    public GeneratedScenario generate(AuthUser admin, @Valid @RequestBody GenerateRequest request) {
        return authoring.generate(admin.id(), request);
    }

    @PostMapping("/{id}/variations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a validated AI variation of a scenario (stored as a new draft)")
    public VariationResponse generateVariation(AuthUser admin, @PathVariable Long id) {
        var result = variationService.generate(admin.id(), id);
        return new VariationResponse(result.scenario(), result.source());
    }

    // ------------------------------------------------------------------ analysis

    @GetMapping("/{id}/graph")
    @Operation(summary = "Dependency graph of the draft")
    public ScenarioGraph graph(@PathVariable Long id) {
        return authoring.graph(id);
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Graph-based validation report of the draft")
    public ValidationReport validate(@PathVariable Long id) {
        return authoring.evaluate(id).validation();
    }

    @PostMapping("/{id}/test-run")
    @Operation(summary = "Run the correct path and the dangerous path; null while validation has errors")
    public TestRunReport testRun(@PathVariable Long id) {
        return authoring.evaluate(id).tests();
    }

    @PostMapping("/{id}/evaluate")
    @Operation(summary = "Validation + test runner + quality score + publishing blockers in one call")
    public Evaluation evaluate(@PathVariable Long id) {
        return authoring.evaluate(id);
    }

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate an unsaved definition (live feedback while editing)")
    public Evaluation evaluateDefinition(@RequestBody ScenarioDefinition definition) {
        return authoring.evaluate(definition);
    }

    // ------------------------------------------------------------------ versions

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish the draft as a new immutable version (all quality gates must pass)")
    public AdminScenarioDetail publish(AuthUser admin, @PathVariable Long id,
                                       @Valid @RequestBody(required = false) PublishRequest request) {
        return authoring.publish(admin.id(), id, request == null ? null : request.changeNote());
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "Published versions, newest first")
    public List<VersionSummary> versions(@PathVariable Long id) {
        return authoring.versions(id);
    }

    @GetMapping("/{id}/versions/{number}")
    @Operation(summary = "One published version with its frozen definition and quality report")
    public VersionDetail version(@PathVariable Long id, @PathVariable int number) {
        return authoring.version(id, number);
    }

    @PostMapping("/{id}/versions/{number}/restore")
    @Operation(summary = "Load a published version back into the editable draft")
    public AdminScenarioDetail restore(@PathVariable Long id, @PathVariable int number) {
        return authoring.restoreVersion(id, number);
    }
}
