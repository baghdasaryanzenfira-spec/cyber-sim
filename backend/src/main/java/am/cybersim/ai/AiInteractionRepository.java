package am.cybersim.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface AiInteractionRepository extends JpaRepository<AiInteraction, Long> {

    List<AiInteraction> findBySimulationIdAndInteractionTypeInOrderByCreatedAtAsc(Long simulationId,
                                                                                   Collection<AiTask> types);

    List<AiInteraction> findBySimulationIdOrderByCreatedAtAsc(Long simulationId);

    @Query("select a.interactionType, a.status, count(a), avg(a.latencyMs) from AiInteraction a "
            + "group by a.interactionType, a.status")
    List<Object[]> usageStatistics();
}
