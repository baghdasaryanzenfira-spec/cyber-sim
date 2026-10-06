package am.cybersim.simulation;

import am.cybersim.ai.AiGateway.AiResult;
import am.cybersim.ai.TutorService;
import am.cybersim.ai.context.SimulationSnapshot;
import am.cybersim.ai.dto.AiDtos.AssistantMessage;
import am.cybersim.ai.dto.AiDtos.AssistantReply;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

/**
 * Connects the AI tutor to a running simulation: ownership and state checks, snapshot creation and the hint
 * counter. Like {@link SimulationService#complete}, the AI call happens outside any database transaction.
 *
 * <p>The hint penalty is applied by the application (counter incremented only after a hint was delivered),
 * never decided by the AI.
 */
@Service
public class SimulationAssistantService {

    private final SimulationService simulationService;
    private final SimulationSnapshotFactory snapshotFactory;
    private final TutorService tutor;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SimulationAssistantService(SimulationService simulationService, SimulationSnapshotFactory snapshotFactory,
                                      TutorService tutor, PlatformTransactionManager transactionManager, Clock clock) {
        this.simulationService = simulationService;
        this.snapshotFactory = snapshotFactory;
        this.tutor = tutor;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public AssistantReply hint(Long userId, Long simulationId) {
        SimulationSnapshot snapshot = activeSnapshot(userId, simulationId, "request hints for");
        AiResult<String> result = tutor.hint(snapshot, snapshot.hintsUsed() + 1);
        Integer hintsUsed = tx.execute(status -> {
            Simulation simulation = simulationService.loadOwned(userId, simulationId);
            SimulationStateMachine.requireInProgress(simulation.getStatus(), "request hints for");
            simulation.incrementHintsUsed();
            return simulation.getHintsUsed();
        });
        return new AssistantReply("HINT", result.value(), result.source(), hintsUsed, clock.instant());
    }

    public AssistantReply ask(Long userId, Long simulationId, String question) {
        SimulationSnapshot snapshot = activeSnapshot(userId, simulationId, "ask questions in");
        AiResult<String> result = tutor.ask(snapshot, question);
        return new AssistantReply("QUESTION", result.value(), result.source(), snapshot.hintsUsed(), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<AssistantMessage> conversation(Long userId, Long simulationId) {
        simulationService.loadOwned(userId, simulationId);
        return tutor.conversation(simulationId);
    }

    private SimulationSnapshot activeSnapshot(Long userId, Long simulationId, String operation) {
        return tx.execute(status -> {
            Simulation simulation = simulationService.loadOwned(userId, simulationId);
            SimulationStateMachine.requireInProgress(simulation.getStatus(), operation);
            return snapshotFactory.create(simulation);
        });
    }
}
