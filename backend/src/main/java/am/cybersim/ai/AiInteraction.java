package am.cybersim.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Audit record of one AI request. Stores the student's question and the answer, never the full prompt
 * (it contains the solution) and never credentials.
 */
@Entity
@Table(name = "ai_interactions")
public class AiInteraction {

    public enum Status { SUCCESS, FALLBACK, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "simulation_id")
    private Long simulationId;

    @Column(name = "scenario_id")
    private Long scenarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false)
    private AiTask interactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiProvider.Type provider;

    @Column
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "request_text", columnDefinition = "text")
    private String requestText;

    @Column(name = "response_text", nullable = false, columnDefinition = "text")
    private String responseText;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiInteraction() {
    }

    public AiInteraction(Long userId, Long simulationId, Long scenarioId, AiTask interactionType,
                         AiProvider.Type provider, String model, Status status, String requestText,
                         String responseText, int latencyMs, Integer inputTokens, Integer outputTokens,
                         Instant createdAt) {
        this.userId = userId;
        this.simulationId = simulationId;
        this.scenarioId = scenarioId;
        this.interactionType = interactionType;
        this.provider = provider;
        this.model = model;
        this.status = status;
        this.requestText = requestText;
        this.responseText = responseText;
        this.latencyMs = latencyMs;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSimulationId() {
        return simulationId;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public AiTask getInteractionType() {
        return interactionType;
    }

    public AiProvider.Type getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Status getStatus() {
        return status;
    }

    public String getRequestText() {
        return requestText;
    }

    public String getResponseText() {
        return responseText;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
