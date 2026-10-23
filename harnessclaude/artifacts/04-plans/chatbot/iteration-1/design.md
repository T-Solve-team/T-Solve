---
module: chatbot
date: 2026-10-23
iteration: 1
---

# Design: chatbot — iteration 1

Master: `artifacts/04-plans/chatbot/design.md`

Iteration 1 builds KAN-48 (STORY-001, STORY-002, STORY-004) in three tasks (KAN-71, KAN-73, KAN-72), frontend only
with MSW mocks. The master design applies in full except:

- STORY-003 (UC-53 ratings) and the `PUT /api/v1/chatbot/answers/{answerId}/rating` path are deferred to a later
  iteration; no rating buttons are shown.
- The backend `chatbot` package (prompt, Ollama, PII masking, search adapter, knowledge gap) is deferred to a change
  of the spec once JDK 21 and Maven are set up.
