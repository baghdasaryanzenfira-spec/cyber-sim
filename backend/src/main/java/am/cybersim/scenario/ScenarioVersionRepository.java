package am.cybersim.scenario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScenarioVersionRepository extends JpaRepository<ScenarioVersion, Long> {

    List<ScenarioVersion> findByScenarioIdOrderByVersionNumberDesc(Long scenarioId);

    Optional<ScenarioVersion> findByScenarioIdAndVersionNumber(Long scenarioId, int versionNumber);
}
