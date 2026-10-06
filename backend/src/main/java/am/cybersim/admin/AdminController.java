package am.cybersim.admin;

import am.cybersim.admin.AdminDtos.AdminUserDetail;
import am.cybersim.admin.AdminDtos.AdminUserRow;
import am.cybersim.admin.AdminDtos.AttemptDetail;
import am.cybersim.admin.AdminDtos.AttemptRow;
import am.cybersim.admin.AdminDtos.PageView;
import am.cybersim.admin.AdminDtos.UserStatusUpdate;
import am.cybersim.admin.AdminDtos.VariationResponse;
import am.cybersim.ai.ScenarioVariationService;
import am.cybersim.analytics.AnalyticsDtos.Mistakes;
import am.cybersim.analytics.AnalyticsDtos.Overview;
import am.cybersim.analytics.AnalyticsService;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDtos.AdminScenarioDetail;
import am.cybersim.scenario.dto.ScenarioDtos.AdminScenarioSummary;
import am.cybersim.scenario.dto.ScenarioDtos.StatusUpdate;
import am.cybersim.security.AuthUser;
import am.cybersim.simulation.SimulationStatus;
import am.cybersim.user.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administration API. Access is restricted to {@code ROLE_ADMIN} by {@code SecurityConfig} (URL rule on
 * {@code /api/admin/**}). Scenario definitions are validated in {@code ScenarioService} (the request body is
 * deliberately not annotated with {@code @Valid}, so that all problems are reported together as one 422 response).
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administration")
public class AdminController {

    private final AdminService adminService;
    private final ScenarioVariationService variationService;
    private final AnalyticsService analyticsService;

    public AdminController(AdminService adminService, ScenarioVariationService variationService,
                           AnalyticsService analyticsService) {
        this.adminService = adminService;
        this.variationService = variationService;
        this.analyticsService = analyticsService;
    }

    // ------------------------------------------------------------------ users

    @GetMapping("/users")
    @Operation(summary = "All users with attempt statistics")
    public List<AdminUserRow> users() {
        return adminService.users();
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "User detail with learning progress")
    public AdminUserDetail user(@PathVariable Long id) {
        return adminService.user(id);
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Enable or disable a user account")
    public UserDto setUserStatus(AuthUser admin, @PathVariable Long id, @RequestBody UserStatusUpdate request) {
        return adminService.setUserEnabled(admin.id(), id, request.enabled());
    }

    // ------------------------------------------------------------------ scenarios

    @GetMapping("/scenarios")
    @Operation(summary = "All scenarios including inactive drafts")
    public List<AdminScenarioSummary> scenarios() {
        return adminService.scenarios();
    }

    @GetMapping("/scenarios/{id}")
    @Operation(summary = "Full scenario definition (including solution) for the editor")
    public AdminScenarioDetail scenario(@PathVariable Long id) {
        return adminService.scenario(id);
    }

    @PostMapping("/scenarios")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a scenario from a definition")
    public AdminScenarioDetail createScenario(@RequestBody ScenarioDefinition definition) {
        return adminService.createScenario(definition);
    }

    @PutMapping("/scenarios/{id}")
    @Operation(summary = "Replace the definition of a scenario (version is incremented)")
    public AdminScenarioDetail updateScenario(@PathVariable Long id, @RequestBody ScenarioDefinition definition) {
        return adminService.updateScenario(id, definition);
    }

    @PatchMapping("/scenarios/{id}/status")
    @Operation(summary = "Activate or deactivate a scenario")
    public AdminScenarioDetail setScenarioStatus(@PathVariable Long id, @RequestBody StatusUpdate request) {
        return adminService.setScenarioActive(id, request.active());
    }

    @PostMapping("/scenarios/{id}/variations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a validated AI variation of a scenario (stored as inactive draft)")
    public VariationResponse generateVariation(AuthUser admin, @PathVariable Long id) {
        var result = variationService.generate(admin.id(), id);
        return new VariationResponse(result.scenario(), result.source());
    }

    // ------------------------------------------------------------------ attempts

    @GetMapping("/simulations")
    @Operation(summary = "Simulation attempts of all users (filterable, paged)")
    public PageView<AttemptRow> attempts(@RequestParam(required = false) Long userId,
                                         @RequestParam(required = false) Long scenarioId,
                                         @RequestParam(required = false) SimulationStatus status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return adminService.attempts(userId, scenarioId, status, page, size);
    }

    @GetMapping("/simulations/{id}")
    @Operation(summary = "Attempt detail: actions, timeline, result and AI interactions")
    public AttemptDetail attempt(@PathVariable Long id) {
        return adminService.attempt(id);
    }

    // ------------------------------------------------------------------ analytics

    @GetMapping("/analytics/overview")
    @Operation(summary = "Platform statistics")
    public Overview overview() {
        return analyticsService.overview();
    }

    @GetMapping("/analytics/mistakes")
    @Operation(summary = "Most common harmful, missed and unnecessary actions")
    public Mistakes mistakes() {
        return analyticsService.mistakes();
    }
}
