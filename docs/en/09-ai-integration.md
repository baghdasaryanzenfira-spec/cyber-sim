# 09 — AI Integration

## 1. Role of AI in the platform

AI is not a chatbot bolted onto the page. It has four concrete jobs, each fed with structured
simulation context:

| Capability | Trigger | Input context | Output | Affects state? |
|-----------|---------|---------------|--------|----------------|
| A. Contextual hint | Student clicks *Get hint* | scenario briefing, visible evidence, performed actions, state, hint number | Short hint (text) | Only increments `hints_used` (penalty) — decided by application code |
| A'. Assistant question | Student asks a question | same + the question | Answer (text) | No |
| B. Post-simulation analysis | Simulation completed | scenario, expected actions, performed actions with order/points, missed actions, score | Structured feedback: summary, strengths, improvements, missed evidence, order issues, unnecessary actions | Stored as feedback text only; **score is computed by the deterministic scoring engine, not by AI** |
| C. Scenario variation | Admin clicks *Generate variation* | Existing scenario definition | New `ScenarioDefinition` JSON | Saved only after validation, as an **inactive draft** |
| D. Learning recommendations | Student opens progress page | per-category results, missed action categories | List of recommended topics with reasons | No |

## 2. Provider abstraction

```mermaid
classDiagram
  class AiProvider {
    <<interface>>
    +name() String
    +complete(AiRequest) AiResponse
  }
  class ClaudeAiProvider {
    -AnthropicClient client
    -model, maxTokens, timeout
  }
  class MockAiProvider {
    rule-based, deterministic
  }
  class AiAssistantService {
    +hint(simulation)
    +ask(simulation, question)
    +feedback(simulation, score)
    +recommendations(user)
    +variation(scenario)
  }
  class AiPromptBuilder
  class AiOutputValidator
  class AiInteractionLogger
  AiProvider <|.. ClaudeAiProvider
  AiProvider <|.. MockAiProvider
  AiAssistantService --> AiProvider : primary
  AiAssistantService --> MockAiProvider : fallback
  AiAssistantService --> AiPromptBuilder
  AiAssistantService --> AiOutputValidator
  AiAssistantService --> AiInteractionLogger
```

### ADR-8 — `AiProvider` interface with Claude and mock implementations
- **What:** A single small interface: `AiResponse complete(AiRequest request)` where the request carries
  the task type, a system prompt, the user content and the expected output format.
- **Why:** The rest of the application does not know which LLM is used. The mock provider makes the
  platform runnable without a paid key, and makes tests deterministic.
- **Selection:** `AI_PROVIDER=claude|mock` (default `mock`). If `claude` is selected but
  `ANTHROPIC_API_KEY` is empty, the application logs a warning and uses the mock provider.
- **Alternatives:** Spring AI (extra abstraction layer and version coupling with Spring Boot 4 milestones;
  the project needs only one call shape); calling the HTTP API directly with `RestClient`
  (the official SDK already handles retries, typed errors and model parameters).

### Claude provider
- Official **Anthropic Java SDK** (`com.anthropic:anthropic-java`).
- Default model **`claude-opus-5-5`**, configurable through `AI_MODEL`
  (e.g. `claude-sonnet-5-5` or `claude-haiku-4-5` for lower cost/latency).
- Effort is configurable (`AI_EFFORT`, default `low`): hints and feedback are short educational texts,
  so low effort keeps latency and cost down.
- Timeout `AI_TIMEOUT_SECONDS` (default 30 s), limited retries.
- If the model declines a request (`stop_reason = refusal`) the response is treated as a failure and
  the fallback is used.

### Mock provider
Deterministic, rule-based answers built from the scenario data:
- **Hint:** the next expected action that has not been performed yet (respecting prerequisites),
  rewritten as a *question/direction* using the scenario's static hints — never the action name itself
  for the first hint, more specific for later hints.
- **Feedback:** strengths = performed expected actions, improvements = missed expected actions
  (with their explanation), harmful and unnecessary actions, out-of-order actions.
- **Recommendations:** categories with the lowest average score / most missed actions.
- **Variation:** deterministic substitution of IP addresses and resource names.

## 3. Request flow with safety controls

```mermaid
sequenceDiagram
  participant UI
  participant S as AiAssistantService
  participant V as Input validation
  participant P as AiPromptBuilder
  participant AI as AiProvider (Claude)
  participant O as AiOutputValidator
  participant M as MockAiProvider
  participant L as ai_interactions
  UI->>S: hint / question
  S->>V: check simulation owner & state, question length ≤ 500, strip control chars
  S->>P: build system prompt + structured context
  S->>AI: complete(request) with timeout
  alt success
    AI-->>S: text / JSON
    S->>O: validate (length, JSON schema, no solution leak for hints)
    O-->>S: ok
  else timeout / error / refusal / invalid output
    S->>M: complete(request)
    M-->>S: deterministic answer (status FALLBACK)
  end
  S->>L: log type, provider, status, latency, tokens
  S-->>UI: answer + source (AI / MOCK / FALLBACK)
```

## 4. AI safety and reliability (requirement §9)

| Control | Implementation |
|---------|---------------|
| Input validation | Bean Validation on question DTO (`@NotBlank`, `@Size(max=500)`); control characters removed; the question is placed in a delimited `<student_question>` block and the system prompt states that its content is data, not instructions. |
| Output validation | Length limits; JSON parsing + schema checks for feedback and variations; hint check rejects answers that list the exact labels of more than one remaining expected action (solution leak) → fallback. |
| Timeouts | SDK client timeout (`AI_TIMEOUT_SECONDS`) and small retry count. |
| Error handling | Any exception from the provider is caught in `AiAssistantService`; the student never sees a 500 because of AI. |
| Fallback behaviour | Mock provider answer, marked `FALLBACK` in the response and the log. |
| Logging | `ai_interactions` table + application log lines with type, provider, status, latency — no API keys, no full prompts. |
| Configurable provider | `AI_PROVIDER`, `AI_MODEL`, `AI_EFFORT`, `AI_TIMEOUT_SECONDS`, `ANTHROPIC_API_KEY`. |
| No command execution | The AI has no tools. Its output is text or JSON that the application parses; only the application can change state. |
| AI recommends, app decides | Score comes from `ScoringEngine`; variations become inactive drafts that an admin must review and activate. |

## 5. Scenario variation (validated AI content)

1. Admin requests a variation of scenario *S*.
2. AI receives the `ScenarioDefinition` JSON and instructions: keep all `*_key` values, phases,
   outcomes and points unchanged; change only names, IP addresses, usernames, timestamps offsets
   (±20 %), and narrative wording.
3. Output is parsed as `ScenarioDefinition` and validated by `ScenarioDefinitionValidator`
   (the same validator as the admin editor) **plus** a structural comparison with the original
   (same action keys, same outcomes and points, same evidence count).
4. If valid → stored as a new scenario with `active = false` and slug `<original>-var-<n>`.
   If invalid → 422 with the validation errors, nothing stored.

## 6. Prompts

Prompts are built by `AiPromptBuilder` and documented with real examples in §7 after implementation
(Phase 7). Structure of every prompt:

```
SYSTEM: role (cybersecurity instructor for a training platform) + rules + output format
USER:   <scenario> ... </scenario>
        <simulation_state> status, resources, visible evidence, performed actions </simulation_state>
        <task> hint #n | answer question | analyse attempt | recommend | vary </task>
        <student_question> (untrusted) </student_question>
```

## 7. Example request/response

*(Filled in Phase 7 with a captured mock and, if a key is available, a real Claude example.)*
