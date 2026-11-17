# Chatbot iteration 3 — decisions

Decisions taken without asking (the user's standing rule: pick the best option, log it here).

## 2026-11-17 — PR #275 review comments

| Comment | Options | Choice | Why |
|---|---|---|---|
| Refused answers reach the issue-draft LLM (BUG-327) | 404 MSG04 · 409 | 404 MSG04 for `GREETING`, `OFF_TOPIC`, `INJECTION` in draft and create | Same as an unknown answer (AC-010.8); needs no new MSG code |
| Rating only enforced in the web UI (BUG-328) | Filter in the update queries · check in the service | `AND a.outcome IN ('ANSWERED','NO_SOURCE')` in both queries; the Jira App sends no rating for other outcomes | One guard covers web, Jira App and direct calls; no extra read |
| Slow classifier uses the whole deadline (BUG-329) | Fixed sub-budget · share of the time left | One third of the time left; a timeout fails open | No new setting; scales with `CHATBOT_ASK_TIMEOUT` |
| Other classifier exceptions fail the ask (BUG-330) | Catch `RuntimeException` | Catch `RuntimeException`, no log | Matches "fail open" in AC-009.6; the failure may hold the question |
| Lossy rewrite on a first question (BUG-331) | Rewrite only with history · embed question + rewrite | Rewrite only with history | Smallest change; a first question is already stand-alone |
| `Category` → `Outcome` by `valueOf` | Exhaustive `switch` · parse into `Outcome` | Exhaustive `switch` | A renamed constant fails the build, not a request |
| Hand-written outcome list in session storage | `Object.values(ChatbotAnswerOutcome)` | Use the generated const | A new outcome is kept without editing this file |
| Mock greeting list copies the backend | Comment · shared file | Comment naming the backend as the source of truth | No cross-stack sharing mechanism exists |
