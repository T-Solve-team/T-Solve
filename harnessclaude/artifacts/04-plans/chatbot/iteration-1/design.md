---
module: chatbot
date: 2026-10-23
iteration: 1
---

# Design: chatbot — iteration 1

Master: `artifacts/04-plans/chatbot/design.md`

Iteration 1 builds KAN-48 (STORY-001, STORY-002, STORY-004) in three tasks (KAN-71, KAN-73, KAN-72) and KAN-75
(STORY-003, AC-004.6) in Task 4, frontend only with MSW mocks. The master design applies in full except:

- STORY-003 has no comment (Figma 12.1 (c); user, 24/10).
- The backend `chatbot` package (prompt, Ollama, PII masking, search adapter, knowledge gap) is deferred to a change
  of the spec once JDK 21 and Maven are set up.
