package am.cybersim.admin;

import am.cybersim.admin.AdminDtos.AdminUserDetail;
import am.cybersim.admin.AdminDtos.AdminUserRow;
import am.cybersim.admin.AdminDtos.AiInteractionView;
import am.cybersim.admin.AdminDtos.AttemptDetail;
import am.cybersim.admin.AdminDtos.AttemptRow;
import am.cybersim.admin.AdminDtos.PageView;
import am.cybersim.ai.AiInteractionRepository;
import am.cybersim.analytics.AnalyticsDtos.UserStats;
import am.cybersim.analytics.AnalyticsService;
import am.cybersim.common.ApiException;
import am.cybersim.progress.ProgressService;
import am.cybersim.scenario.Scenario;
import am.cybersim.scenario.ScenarioMapper;
import am.cybersim.scenario.ScenarioService;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDtos.AdminScenarioDetail;
import am.cybersim.scenario.dto.ScenarioDtos.AdminScenarioSummary;
import am.cybersim.simulation.Simulation;
import am.cybersim.simulation.SimulationMapper;
import am.cybersim.simulation.SimulationRepository;
import am.cybersim.simulation.SimulationResult;
import am.cybersim.simulation.SimulationResultRepository;
import am.cybersim.simulation.SimulationStatus;
import am.cybersim.user.User;
import am.cybersim.user.UserRepository;
import am.cybersim.user.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Administration use cases that combine several modules (users, scenarios, simulations, analytics).
 * Only reachable through {@code /api/admin/**}, which Spring Security restricts to {@code ROLE_ADMIN}.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final UserStats NO_STATS = new UserStats(0, 0, null);

    private final UserRepository users;
    private final SimulationRepository simulations;
    private final SimulationResultRepository results;
    private final SimulationMapper simulationMapper;
    private final ScenarioService scenarioService;
    private final ScenarioMapper scenarioMapper;
    private final ProgressService progressService;
    private final AnalyticsService analyticsService;
    private final AiInteractionRepository aiInteractions;

    public AdminService(UserRepository users, SimulationRepository simulations, SimulationResultRepository results,
                        SimulationMapper simulationMapper,
                        ScenarioService scenarioService, ScenarioMapper scenarioMapper, ProgressService progressService,
                        AnalyticsService analyticsService, AiInteractionRepository aiInteractions) {
        this.users = users;
        this.simulations = simulations;
        this.results = results;
        this.simulationMapper = simulationMapper;
        this.scenarioService = scenarioService;
        this.scenarioMapper = scenarioMapper;
        this.progressService = progressService;
        this.analyticsService = analyticsService;
        this.aiInteractions = aiInteractions;
    }

    // ------------------------------------------------------------------ users

    @Transactional(readOnly = true)
    public List<AdminUserRow> users() {
        Map<Long, UserStats> stats = analyticsService.userStats();
        return users.findAll(Sort.by("createdAt").descending()).stream()
                .map(u -> {
                    UserStats s = stats.getOrDefault(u.getId(), NO_STATS);
                    return new AdminUserRow(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.isEnabled(),
                            u.getCreatedAt(), u.getLastLoginAt(), s.attempts(), s.completed(), s.averageScore());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserDetail user(Long id) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        return new AdminUserDetail(UserDto.from(user), progressService.progress(id));
    }

    @Transactional
    public UserDto setUserEnabled(Long adminId, Long userId, boolean enabled) {
        if (adminId.equals(userId) && !enabled) {
            throw ApiException.badRequest("CANNOT_DISABLE_SELF", "Administrators cannot disable their own account");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        user.setEnabled(enabled);
        log.info("Admin {} set enabled={} for user {}", adminId, enabled, userId);
        return UserDto.from(user);
    }

    // ------------------------------------------------------------------ scenarios

    @Transactional(readOnly = true)
    public List<AdminScenarioSummary> scenarios() {
        Map<Long, Long> attempts = simulations.countAttemptsPerScenario().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return scenarioService.listAll().stream()
                .map(s -> scenarioMapper.toAdminSummary(s, attempts.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    // The scenario methods below map inside the transaction: open-in-view is disabled, so lazy children
    // must be read before the transaction ends.

    @Transactional(readOnly = true)
    public AdminScenarioDetail scenario(Long id) {
        return scenarioMapper.toAdminDetail(scenarioService.get(id));
    }

    @Transactional
    public AdminScenarioDetail createScenario(ScenarioDefinition definition) {
        return scenarioMapper.toAdminDetail(scenarioService.create(definition));
    }

    @Transactional
    public AdminScenarioDetail updateScenario(Long id, ScenarioDefinition definition) {
        return scenarioMapper.toAdminDetail(scenarioService.update(id, definition));
    }

    @Transactional
    public AdminScenarioDetail setScenarioActive(Long id, boolean active) {
        return scenarioMapper.toAdminDetail(scenarioService.setActive(id, active));
    }

    // ------------------------------------------------------------------ attempts

    @Transactional(readOnly = true)
    public PageView<AttemptRow> attempts(Long userId, Long scenarioId, SimulationStatus status, int page, int size) {
        Specification<Simulation> spec = (root, query, cb) -> cb.conjunction();
        if (userId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("user").get("id"), userId));
        }
        if (scenarioId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("scenario").get("id"), scenarioId));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        int safeSize = Math.clamp(size, 1, 100);
        Page<Simulation> result = simulations.findAll(spec,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending()));
        Map<Long, Integer> scores = scores(result.getContent());
        List<AttemptRow> rows = result.getContent().stream().map(s -> toRow(s, scores.get(s.getId()))).toList();
        return new PageView<>(rows, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AttemptDetail attempt(Long simulationId) {
        Simulation s = simulations.findById(simulationId).orElseThrow(() -> ApiException.notFound("Simulation"));
        SimulationResult result = results.findById(simulationId).orElse(null);
        List<AiInteractionView> ai = aiInteractions.findBySimulationIdOrderByCreatedAtAsc(simulationId).stream()
                .map(i -> new AiInteractionView(i.getId(), i.getInteractionType().name(), i.getProvider().name(),
                        i.getModel(), i.getStatus().name(), i.getRequestText(), i.getResponseText(), i.getLatencyMs(),
                        i.getInputTokens(), i.getOutputTokens(), i.getCreatedAt()))
                .toList();
        return new AttemptDetail(toRow(s, result == null ? null : result.getScorePercent()),
                simulationMapper.toDetail(s, true),
                result == null ? null : simulationMapper.toResultView(s, result), ai);
    }

    private AttemptRow toRow(Simulation s, Integer score) {
        User u = s.getUser();
        Scenario sc = s.getScenario();
        return new AttemptRow(s.getId(), u.getId(), u.getEmail(), u.getDisplayName(), sc.getId(), sc.getTitle(),
                s.getStatus(), score, s.getActions().size(), s.getHintsUsed(), s.getCreatedAt(), s.getCompletedAt());
    }

    private Map<Long, Integer> scores(List<Simulation> list) {
        return results.findBySimulationIdIn(list.stream().map(Simulation::getId).toList()).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SimulationResult::getSimulationId, SimulationResult::getScorePercent,
                        (a, b) -> a));
    }
}
