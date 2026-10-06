package am.cybersim.simulation;

import am.cybersim.ai.AiGateway.AiResult;
import am.cybersim.ai.TutorService;
import am.cybersim.ai.context.SimulationSnapshot;
import am.cybersim.ai.dto.AiFeedback;
import am.cybersim.common.ApiException;
import am.cybersim.scenario.Scenario;
import am.cybersim.scenario.ScenarioService;
import am.cybersim.scoring.ScoreResult;
import am.cybersim.scoring.ScoringEngine;
import am.cybersim.simulation.dto.SimulationDtos.ActionResult;
import am.cybersim.simulation.dto.SimulationDtos.PerformActionRequest;
import am.cybersim.simulation.dto.SimulationDtos.SimulationDetail;
import am.cybersim.simulation.dto.SimulationDtos.SimulationResultView;
import am.cybersim.simulation.dto.SimulationDtos.SimulationSummary;
import am.cybersim.user.User;
import am.cybersim.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Application service of the simulation module: transactions, ownership checks and orchestration of
 * {@link SimulationEngine}, {@link ScoringEngine} and the AI tutor.
 *
 * <p>Security: every method receives the authenticated user id and loads the simulation through
 * {@link #loadOwned}; a simulation of another user is reported as 404 so that ids cannot be probed.
 */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    static final EnumSet<SimulationStatus> UNFINISHED = EnumSet.of(SimulationStatus.CREATED, SimulationStatus.RUNNING,
            SimulationStatus.INVESTIGATING, SimulationStatus.RESPONDING);

    private final SimulationRepository simulations;
    private final SimulationResultRepository results;
    private final UserRepository users;
    private final ScenarioService scenarioService;
    private final SimulationEngine engine;
    private final ScoringEngine scoringEngine;
    private final SimulationMapper mapper;
    private final SimulationSnapshotFactory snapshotFactory;
    private final TutorService tutor;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SimulationService(SimulationRepository simulations, SimulationResultRepository results,
                             UserRepository users, ScenarioService scenarioService, SimulationEngine engine,
                             ScoringEngine scoringEngine, SimulationMapper mapper,
                             SimulationSnapshotFactory snapshotFactory, TutorService tutor,
                             PlatformTransactionManager transactionManager, Clock clock) {
        this.simulations = simulations;
        this.results = results;
        this.users = users;
        this.scenarioService = scenarioService;
        this.engine = engine;
        this.scoringEngine = scoringEngine;
        this.mapper = mapper;
        this.snapshotFactory = snapshotFactory;
        this.tutor = tutor;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Outcome of {@link #create}: {@code created=false} means an unfinished attempt was resumed. */
    public record CreateOutcome(SimulationDetail simulation, boolean created) {
    }

    @Transactional
    public CreateOutcome create(Long userId, Long scenarioId) {
        Scenario scenario = scenarioService.getActive(scenarioId);
        var existing = simulations.findFirstByUserIdAndScenarioIdAndStatusIn(userId, scenarioId, UNFINISHED);
        if (existing.isPresent()) {
            return new CreateOutcome(mapper.toDetail(existing.get(), false), false);
        }
        User user = users.getReferenceById(userId);
        Simulation simulation = new Simulation(user, scenario, clock.instant());
        try {
            simulations.saveAndFlush(simulation);
        } catch (DataIntegrityViolationException e) {
            // the partial unique index rejected a concurrent second attempt
            throw ApiException.conflict("SIMULATION_ALREADY_ACTIVE", "An unfinished attempt for this scenario exists");
        }
        log.info("User {} created simulation {} for scenario '{}'", userId, simulation.getId(), scenario.getSlug());
        return new CreateOutcome(mapper.toDetail(simulation, false), true);
    }

    @Transactional(readOnly = true)
    public List<SimulationSummary> listForUser(Long userId) {
        List<Simulation> list = simulations.findByUserIdWithScenario(userId);
        Map<Long, Integer> scores = scoresFor(list);
        return list.stream().map(s -> mapper.toSummary(s, scores.get(s.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public SimulationDetail get(Long userId, Long simulationId) {
        return mapper.toDetail(loadOwned(userId, simulationId), false);
    }

    @Transactional
    public SimulationDetail start(Long userId, Long simulationId) {
        Simulation simulation = loadOwned(userId, simulationId);
        engine.start(simulation, clock.instant());
        simulations.flush();
        log.info("Simulation {} started", simulationId);
        return mapper.toDetail(simulation, false);
    }

    @Transactional
    public ActionResult performAction(Long userId, Long simulationId, PerformActionRequest request) {
        Simulation simulation = loadOwned(userId, simulationId);
        SimulationEngine.AppliedAction applied = engine.apply(simulation, simulation.getUser(), request.actionKey(),
                request.note(), clock.instant());
        simulations.flush();
        log.info("Simulation {}: action '{}' -> {} (status {})", simulationId, request.actionKey(),
                applied.action().getResult(), simulation.getStatus());
        return new ActionResult(mapper.toPerformedView(applied.action(), false), applied.revealedEvents(),
                mapper.toDetail(simulation, false));
    }

    @Transactional
    public SimulationDetail flagEvent(Long userId, Long simulationId, Long eventId, boolean flagged) {
        Simulation simulation = loadOwned(userId, simulationId);
        engine.setEventFlag(simulation, eventId, flagged);
        return mapper.toDetail(simulation, false);
    }

    @Transactional
    public SimulationDetail abandon(Long userId, Long simulationId) {
        Simulation simulation = loadOwned(userId, simulationId);
        simulation.markAbandoned(clock.instant());
        log.info("Simulation {} abandoned", simulationId);
        return mapper.toDetail(simulation, false);
    }

    /**
     * Completes the simulation in three steps so that no database transaction is open during the AI call:
     * <ol>
     *   <li>TX 1: state transition, deterministic score, result row, AI snapshot;</li>
     *   <li>AI post-simulation analysis (may take seconds; falls back to the mock on any problem);</li>
     *   <li>TX 2: attach the feedback to the result.</li>
     * </ol>
     * If step 2 or 3 failed unexpectedly, the score is still saved — feedback is an enhancement, not a requirement.
     */
    public SimulationResultView complete(Long userId, Long simulationId) {
        record Completed(SimulationSnapshot snapshot, ScoreResult score) {
        }
        Completed completed = tx.execute(status -> {
            Simulation simulation = loadOwned(userId, simulationId);
            simulation.markCompleted(clock.instant());
            ScoreResult score = score(simulation);
            results.save(new SimulationResult(simulation.getId(), score, clock.instant()));
            simulations.flush();
            log.info("Simulation {} completed with score {}/100", simulationId, score.scorePercent());
            return new Completed(snapshotFactory.create(simulation), score);
        });

        try {
            AiResult<AiFeedback> feedback = tutor.feedback(completed.snapshot(), completed.score());
            tx.executeWithoutResult(status -> results.findById(simulationId)
                    .ifPresent(r -> r.attachFeedback(feedback.value(), feedback.source().name())));
        } catch (RuntimeException e) {
            log.error("Feedback generation failed for simulation {}", simulationId, e);
        }
        // explicit transaction: a self-invocation of the @Transactional result() would bypass the Spring proxy
        return tx.execute(status -> resultView(loadOwned(userId, simulationId)));
    }

    @Transactional(readOnly = true)
    public SimulationResultView result(Long userId, Long simulationId) {
        return resultView(loadOwned(userId, simulationId));
    }

    /** Result view without ownership check — for administrators. */
    @Transactional(readOnly = true)
    public SimulationResultView resultForAdmin(Long simulationId) {
        return resultView(simulations.findById(simulationId).orElseThrow(() -> ApiException.notFound("Simulation")));
    }

    private SimulationResultView resultView(Simulation simulation) {
        if (simulation.getStatus() != SimulationStatus.COMPLETED) {
            throw ApiException.conflict("NOT_COMPLETED", "The simulation has not been completed yet");
        }
        SimulationResult result = results.findById(simulation.getId())
                .orElseThrow(() -> ApiException.notFound("Simulation result"));
        return mapper.toResultView(simulation, result);
    }

    ScoreResult score(Simulation simulation) {
        Scenario scenario = simulation.getScenario();
        List<ScoringEngine.PerformedAction> performed = simulation.getActions().stream()
                .map(a -> new ScoringEngine.PerformedAction(a.getActionKey(), a.getLabel(), a.getCategory(),
                        a.getOutcome(), a.isDuplicate(), a.isOutOfOrder(), a.getPointsAwarded()))
                .toList();
        return scoringEngine.score(scenario.getActions(), performed, simulation.getHintsUsed(),
                scenario.getHintPenalty());
    }

    /** Loads a simulation of the given user, or 404 — also used by the assistant service. */
    Simulation loadOwned(Long userId, Long simulationId) {
        return simulations.findById(simulationId)
                .filter(s -> s.isOwnedBy(userId))
                .orElseThrow(() -> ApiException.notFound("Simulation"));
    }

    Map<Long, Integer> scoresFor(List<Simulation> list) {
        List<Long> ids = list.stream().map(Simulation::getId).toList();
        return results.findBySimulationIdIn(ids).stream()
                .collect(Collectors.toMap(SimulationResult::getSimulationId, SimulationResult::getScorePercent));
    }
}
