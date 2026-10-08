# 08 — Scenario Execution Model and Test Runner

> This document originally described the learner-facing simulation engine. That runtime moved to the other
> team's module (see [17-admin-authoring-platform.md](17-admin-authoring-platform.md)); the file now documents
> what replaced it here: the **execution model** a scenario defines, and the **test runner** that plays every
> scenario in memory with exactly those rules before it can be published. The file name is kept so existing
> references stay valid.

## 1. Responsibilities

A scenario is not just content — it defines an executable model: which actions are available, in which order
they make sense, which log entries they disclose, and how the simulated infrastructure reacts. The authoring
platform must guarantee that this model actually *works* before a version is handed to the learner module.
That is the job of `ScenarioTestRunner` (package `am.cybersim.authoring`): play the scenario in memory, with the
same rules a trainee will face, and fail publishing if anything is off. It is deterministic and side-effect
free (no database, no user), so it can run on every save and as a publishing gate.

## 2. Execution model

The rules the test runner enforces are the semantics of the `ScenarioDefinition` document:

| Rule | Data that drives it |
|------|---------------------|
| An action can be performed once; repeating it has no effect | `action_key` uniqueness |
| An action performed before its prerequisite counts as **out of order** | `prerequisite_action_key` |
| Events with `revealed_by_action_key = NULL` are visible from the start | `scenario_events` |
| Performing an investigation action **reveals** the events that reference it (progressive disclosure) | `revealed_by_action_key` |
| A response action may change a resource's status (e.g. `RUNNING → ISOLATED`) | `target_resource_key` + `effect_status` |
| Every performed action is scored at the moment it is performed | `outcome`, `points`, penalties (see §5) |

The in-memory play (`ScenarioTestRunner.play`) starts from the initial resource states, walks a list of actions,
tracks revealed events and resource status changes, and finally scores the run with the deterministic
`ScoringEngine` — the same component whose rules the learner module applies, so "passes here" means "behaves
predictably there".

## 3. Correct path

The runner computes a dependency-respecting order of all `EXPECTED` actions
(`ScenarioGraph.topologicalOrder`) and plays it. Checks (all must pass):

| Check | Why it matters |
|-------|----------------|
| All correct actions can be performed | a prerequisite cycle would make the scenario unsolvable |
| Performed in valid order, no penalties | the declared prerequisites must themselves be satisfiable in order |
| Maximum score is reached (100 %) | the advertised maximum must actually be attainable |
| All evidence is discoverable | every `evidence = true` event must be reachable on the correct path |
| Incident ends in a changed state | the response must have a visible effect on at least one resource |
| No correct action is missed | internal consistency of the catalogue |

## 4. Dangerous path

The runner then plays all `HARMFUL` actions. Checks:

| Check | Why it matters |
|-------|----------------|
| Dangerous actions exist | without one, the "wrong way" cannot be demonstrated or tested |
| Every harmful action is penalised | negative points must actually be configured |
| Score stays failing (&lt; 50 % and raw ≤ 0) | doing only wrong things must not look like success |
| Incident is not resolved | every expected action must still be missing afterwards |
| A harmful action does not look like the fix | a harmful action must not produce the same resource end state as the correct response |
| Every harmful action explains why it is wrong | the explanation (≥ 20 characters) is the teaching content for mistakes |

## 5. Scoring

Scoring is the deterministic `ScoringEngine` (ADR-6 — AI is never involved in grading). Rules, configurable per
scenario (`out_of_order_penalty`, `hint_penalty`) and per action (`points`):

| Situation | Points |
|-----------|--------|
| `EXPECTED` action, first time, in order | `+points` |
| `EXPECTED` action before its prerequisite | `+points − out_of_order_penalty` (minimum 0) |
| `NEUTRAL` action | 0 (reported as unnecessary) |
| `HARMFUL` action | negative `points` |
| Repeated action | 0 |
| Each AI hint (learner side) | `−hint_penalty` |

`rawScore` is the sum (may be negative); `maxScore` is the sum of all `EXPECTED` points; `scorePercent` is the
clamped percentage. The result (`ScoreResult`) lists one line per scored element plus every missed expected
action with its explanation. The test runner uses `hintsUsed = 0`; the hint penalty exists for the learner
module, which applies the same engine.

## 6. Determinism and reuse

- The whole pipeline — graph, validation, test run, scoring, quality score — is a pure function of the
  `ScenarioDefinition`. Same input, same verdict; no clock, no randomness, no I/O.
- Because the test runner and the learner module share the `ScoringEngine` rules and the same definition
  document (frozen in `scenario_versions`), a published scenario's advertised properties — solvable, max score
  reachable, dangerous path penalised — hold wherever the version is executed.
- AI-generated content (generation polish, variations) gets no shortcut: it is parsed, structure-checked and
  then validated and test-run exactly like hand-written content ("AI proposes, application decides").
