package am.cybersim.simulation;

import am.cybersim.ai.dto.AiDtos.AskRequest;
import am.cybersim.ai.dto.AiDtos.AssistantMessage;
import am.cybersim.ai.dto.AiDtos.AssistantReply;
import am.cybersim.security.AuthUser;
import am.cybersim.simulation.dto.SimulationDtos.ActionResult;
import am.cybersim.simulation.dto.SimulationDtos.CreateSimulationRequest;
import am.cybersim.simulation.dto.SimulationDtos.FlagEventRequest;
import am.cybersim.simulation.dto.SimulationDtos.PerformActionRequest;
import am.cybersim.simulation.dto.SimulationDtos.SimulationDetail;
import am.cybersim.simulation.dto.SimulationDtos.SimulationResultView;
import am.cybersim.simulation.dto.SimulationDtos.SimulationSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Student simulation API. Thin: every method delegates to exactly one service call. */
@RestController
@RequestMapping("/api/simulations")
@Tag(name = "Simulations")
public class SimulationController {

    private final SimulationService simulationService;
    private final SimulationAssistantService assistantService;

    public SimulationController(SimulationService simulationService, SimulationAssistantService assistantService) {
        this.simulationService = simulationService;
        this.assistantService = assistantService;
    }

    @PostMapping
    @Operation(summary = "Create a simulation for a scenario (or resume the unfinished one)")
    public ResponseEntity<SimulationDetail> create(AuthUser user, @Valid @RequestBody CreateSimulationRequest request) {
        SimulationService.CreateOutcome outcome = simulationService.create(user.id(), request.scenarioId());
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(outcome.simulation());
    }

    @GetMapping
    @Operation(summary = "My simulation history")
    public List<SimulationSummary> list(AuthUser user) {
        return simulationService.listForUser(user.id());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Current state of a simulation")
    public SimulationDetail get(AuthUser user, @PathVariable Long id) {
        return simulationService.get(user.id(), id);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start the incident (CREATED → RUNNING)")
    public SimulationDetail start(AuthUser user, @PathVariable Long id) {
        return simulationService.start(user.id(), id);
    }

    @PostMapping("/{id}/actions")
    @Operation(summary = "Perform an investigation or response action")
    public ActionResult performAction(AuthUser user, @PathVariable Long id,
                                      @Valid @RequestBody PerformActionRequest request) {
        return simulationService.performAction(user.id(), id, request);
    }

    @PutMapping("/{id}/events/{eventId}/flag")
    @Operation(summary = "Flag or unflag a log entry as evidence")
    public SimulationDetail flagEvent(AuthUser user, @PathVariable Long id, @PathVariable Long eventId,
                                      @RequestBody FlagEventRequest request) {
        return simulationService.flagEvent(user.id(), id, eventId, request.flagged());
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Finish the simulation: score + AI feedback")
    public SimulationResultView complete(AuthUser user, @PathVariable Long id) {
        return simulationService.complete(user.id(), id);
    }

    @PostMapping("/{id}/abandon")
    @Operation(summary = "Abandon the simulation")
    public SimulationDetail abandon(AuthUser user, @PathVariable Long id) {
        return simulationService.abandon(user.id(), id);
    }

    @GetMapping("/{id}/result")
    @Operation(summary = "Result of a completed simulation")
    public SimulationResultView result(AuthUser user, @PathVariable Long id) {
        return simulationService.result(user.id(), id);
    }

    @PostMapping("/{id}/assistant/hint")
    @Operation(summary = "AI contextual hint (small score penalty)")
    public AssistantReply hint(AuthUser user, @PathVariable Long id) {
        return assistantService.hint(user.id(), id);
    }

    @PostMapping("/{id}/assistant/ask")
    @Operation(summary = "Ask the AI assistant a question")
    public AssistantReply ask(AuthUser user, @PathVariable Long id, @Valid @RequestBody AskRequest request) {
        return assistantService.ask(user.id(), id, request.question());
    }

    @GetMapping("/{id}/assistant/messages")
    @Operation(summary = "Assistant conversation history")
    public List<AssistantMessage> messages(AuthUser user, @PathVariable Long id) {
        return assistantService.conversation(user.id(), id);
    }
}
