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

## 2026-11-22 — KAN-380: one combined answer, Solutions open, description in the search

| Question | Options | Choice | Why |
|---|---|---|---|
| Which screen | Search page · Chatbot · both | Chatbot (the user, 22/11) | The user's answer |
| What changes in the chatbot | Prompt only · display only · both | Both (the user, 22/11): one combined answer; the Solutions it used open under it | The user's answer |
| Where the description counts | Every user of `ticket_embedding` · chatbot only | Every user (the user, 22/11) | One table serves chatbot, search and triage; a chatbot-only vector would double the storage and the embed calls |
| How old vectors are replaced | Delete all rows · `text_version` column, re-embed in the background | `text_version` 1/2; the catch-up job re-embeds version 1 after the tickets with no vector | Deleting leaves the search empty until every ticket is embedded again (4 tickets a minute by default); the old vector is still useful meanwhile |
| Does the description go through PII masking | Mask · send as is | Send as is | No masking exists in the backend; embeddings go to the team's own Ollama (D16 of KAN-377), never to an outside service |
| The Jira App answer page | Change too · leave | Leave (jira-app module) | Outside the chatbot web UI; its own change if wanted |
| Ticket List mock keyword (search AC-004.2) | Add description · leave | Leave | The mock tickets carry no description; the real backend matches it (AC-006.4) |
| Who writes the conversation title | A separate AI call · the classifier call · the frontend cuts the question | The classifier call of the first question returns `title` too | No extra call or latency; the classifier already reads the first question. A failed or slow classifier falls back to the question |
| Title for `OFF_TOPIC` / `INJECTION` | Return it · only for `QUESTION` | Only `QUESTION` | An injection could otherwise choose the text shown in the list |
| Title length | Cut in the backend · only CSS | Both: 60 characters in the backend, one line with "…" and a tooltip in the list | A bad model reply cannot fill session storage; the list stays one line at any width |
