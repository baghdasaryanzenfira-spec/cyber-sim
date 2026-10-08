package am.cybersim.scenario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    List<Scenario> findAllByOrderByCreatedAtAsc();

    Optional<Scenario> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countBySlugStartingWith(String prefix);
}
