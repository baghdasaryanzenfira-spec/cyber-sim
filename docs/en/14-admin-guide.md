# 14 — Administrator Guide

Administrators sign in with an `ADMIN` account (demo: `DEMO_ADMIN_EMAIL` / `DEMO_ADMIN_PASSWORD` from `.env`).
The **Administration** section appears in the side menu. Administrators can also train like students.

## 1. Admin dashboard
Totals (students, active scenarios, simulations, average score, completion rate), attempts over the last 14 days,
average score per scenario, recent attempts and the most common harmful actions.

## 2. Users
List of all accounts with attempts, completed simulations, average score and last login.
- **Enabled** switch: disable/enable an account (disabled users cannot sign in; you cannot disable yourself).
- **Progress**: the user's progress page and all their attempts.

## 3. Scenario management
The list shows every scenario (also inactive drafts and AI variations) with version, number of actions/events and
attempts.
- **Active** switch: show/hide the scenario for students.
- **Edit** (pencil): open the scenario editor.
- **Generate AI variation** (sparkle icon): the AI creates a variant with new names, IP addresses and wording. The
  result is validated (same structure, keys, points and evidence as the original) and stored as an **inactive draft**.
  Review it in the editor and activate it if it is good.
- **New scenario**: start from an empty editor.

## 4. Scenario editor

| Tab | What you configure |
|-----|-------------------|
| General | slug (fixed after creation), title, summary, briefing, difficulty, category, estimated time, **hint penalty**, **order penalty**, learning objectives, incident explanation, recommended solution, active flag |
| Infrastructure | simulated resources: key, type, name, region, initial status, properties (JSON) |
| Logs, alerts & evidence | timeline entries: offset, type, severity, source, message, resource, **evidence** marker + note, and the investigation action that **reveals** the entry (empty = visible from the start) |
| Actions & scoring | the action catalogue: phase, category, target resource, **outcome** (EXPECTED / NEUTRAL / HARMFUL), **points**, "should come after" (order rule), effect on the resource status, console output, explanation |
| Hints | static hints from general to specific (offline tutor + AI instructor notes) |
| JSON | the complete definition as JSON (import/export) |

**Save** validates the whole scenario. If something is wrong, all problems are listed (e.g. *points must be > 0 for
EXPECTED*, *unknown resource*, *prerequisites form a cycle*). Saving increments the version; completed attempts keep
their original scores.

Scoring rule of thumb: make the points of all EXPECTED actions add up to 100, so the raw score equals the percentage.

## 5. Attempts
Filter attempts by scenario and status. **Details** shows the full result (score, AI feedback, breakdown), resources,
all logs including evidence markers, the incident timeline and every AI interaction (type, provider, status,
latency, question and answer).

## 6. Analytics
Score distribution, per-scenario statistics (attempts, completion, average score, average time), **most frequently
missed steps**, **most common harmful actions**, unnecessary actions, and AI usage/reliability (requests per task
and status, average latency).
