package am.cybersim.admin;

import am.cybersim.ai.dto.AiDtos.AiSource;
import am.cybersim.progress.ProgressDtos.ProgressView;
import am.cybersim.simulation.SimulationStatus;
import am.cybersim.simulation.dto.SimulationDtos.SimulationDetail;
import am.cybersim.simulation.dto.SimulationDtos.SimulationResultView;
import am.cybersim.user.Role;
import am.cybersim.user.dto.UserDto;

import java.time.Instant;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    }

    public record AdminUserRow(Long id, String email, String displayName, Role role, boolean enabled,
                               Instant createdAt, Instant lastLoginAt, long attempts, long completed,
                               Integer averageScore) {
    }

    public record AdminUserDetail(UserDto user, ProgressView progress) {
    }

    public record UserStatusUpdate(boolean enabled) {
    }

    public record AttemptRow(Long id, Long userId, String userEmail, String userName, Long scenarioId,
                             String scenarioTitle, SimulationStatus status, Integer scorePercent, int actionCount,
                             int hintsUsed, Instant createdAt, Instant completedAt) {
    }

    public record AiInteractionView(Long id, String type, String provider, String model, String status,
                                    String request, String response, int latencyMs, Integer inputTokens,
                                    Integer outputTokens, Instant createdAt) {
    }

    public record AttemptDetail(AttemptRow attempt, SimulationDetail simulation, SimulationResultView result,
                                List<AiInteractionView> aiInteractions) {
    }

    public record VariationResponse(am.cybersim.scenario.dto.ScenarioDtos.AdminScenarioDetail scenario,
                                    AiSource source) {
    }
}
