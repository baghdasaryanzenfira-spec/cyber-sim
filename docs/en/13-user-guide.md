# 13 — User Guide (Students)

## 1. Account
1. Open the platform (Docker setup: http://localhost:3000).
2. **Register** with e-mail, display name and a password of at least 8 characters, then **Sign in**.

## 2. Dashboard
Shows completed simulations, average and best score, AI hints used, your score history, unfinished simulations
(with *Resume*) and the available scenarios.

## 3. Choosing a scenario
*Scenarios* lists all active training incidents with difficulty, topic and estimated time. *Open briefing* shows the
incident description and learning objectives. Click **Start simulation**.

## 4. Working on a simulation

The simulation page is your incident-response console:

| Area | What you do there |
|------|-------------------|
| Header | Status (`RUNNING → INVESTIGATING → RESPONDING`), timer, number of actions, **Finish & get score**, Abandon |
| Logs & alerts | Read log lines and alerts. Filter by source. Click the **flag** icon to put an entry on your *evidence board*. |
| Cloud resources | Simulated VMs, users, keys, buckets and their current status (e.g. `RUNNING`, `ISOLATED`, `DISABLED`). |
| AI assistant | **Get a hint** (costs a few points) or ask a question. The assistant sees what you see but will not reveal the solution. |
| Available actions | *Investigate & identify* actions (inspect logs, classify the incident…) and *Respond* actions (contain, eradicate, recover, harden). Click an action, optionally add a note, **Execute**. |
| Incident timeline | Attacker activity and your own actions in chronological order. |

Tips:
- Investigation actions can reveal **new log entries** — look at the logs again after each one.
- Order matters: e.g. identify the compromised resource before isolating it. Out-of-order actions earn fewer points.
- Some actions are harmful (they destroy evidence or disrupt healthy systems) and cost points.
- Repeating an action gives no extra points.

Your simulation is saved automatically; you can leave and resume it later.

## 5. Results
After **Finish**, the result page shows:
- the **score** (0–100) with raw points, hint penalty, time and evidence found;
- **AI feedback**: summary, strengths, improvements, order issues, missed evidence, unnecessary actions, next steps;
- the **score breakdown** of every action and the **missed expected actions** with explanations;
- the **evidence** list (flagged / seen but not flagged / never uncovered);
- *What really happened* and the *Recommended response*.

## 6. Progress & history
Shows statistics per topic, per scenario, all attempts (open results or resume) and **AI learning recommendations**.
