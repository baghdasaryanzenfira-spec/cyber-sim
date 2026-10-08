package am.cybersim.ai.dto;

/** API models of the AI module. */
public final class AiDtos {

    private AiDtos() {
    }

    /** Where an AI answer came from: real model, configured mock, or mock used because the model failed. */
    public enum AiSource { AI, MOCK, FALLBACK }
}
