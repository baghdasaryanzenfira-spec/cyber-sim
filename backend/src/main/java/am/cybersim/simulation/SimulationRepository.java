package am.cybersim.simulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SimulationRepository extends JpaRepository<Simulation, Long>, JpaSpecificationExecutor<Simulation> {

    @Query("select s from Simulation s join fetch s.scenario where s.user.id = :userId order by s.createdAt desc")
    List<Simulation> findByUserIdWithScenario(@Param("userId") Long userId);

    Optional<Simulation> findFirstByUserIdAndScenarioIdAndStatusIn(Long userId, Long scenarioId,
                                                                    Collection<SimulationStatus> statuses);

    @Query("select s.scenario.id, count(s) from Simulation s group by s.scenario.id")
    List<Object[]> countAttemptsPerScenario();

    long countByStatus(SimulationStatus status);

    long countByUserId(Long userId);
}
