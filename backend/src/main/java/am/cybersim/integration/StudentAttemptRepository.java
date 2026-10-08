package am.cybersim.integration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentAttemptRepository extends JpaRepository<StudentAttempt, Long> {

    Optional<StudentAttempt> findByExternalId(String externalId);

    Page<StudentAttempt> findAllByOrderBySubmittedAtDesc(Pageable pageable);

    Page<StudentAttempt> findByScenarioIdOrderBySubmittedAtDesc(Long scenarioId, Pageable pageable);
}
