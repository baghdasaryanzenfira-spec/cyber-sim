package am.cybersim.scenario;

import am.cybersim.scenario.dto.ScenarioDtos.ScenarioBriefing;
import am.cybersim.scenario.dto.ScenarioDtos.ScenarioSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
@Tag(name = "Scenarios")
public class ScenarioController {

    private final ScenarioService scenarioService;

    public ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @GetMapping
    @Operation(summary = "List active training scenarios")
    public List<ScenarioSummary> list() {
        return scenarioService.listActive();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Scenario briefing (description and learning objectives, no solution)")
    public ScenarioBriefing briefing(@PathVariable Long id) {
        return scenarioService.getBriefing(id);
    }
}
