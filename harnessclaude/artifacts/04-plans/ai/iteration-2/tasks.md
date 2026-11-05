---
module: ai
date: 2026-11-05
total_tasks: 3
owner: Dev
---

# Tasks: ai — iteration 2

Spec: artifacts/03-modules/ai/spec.md
Master: artifacts/04-plans/ai/design.md

Branch per Jira issue (team rule): `feat/KAN-139-ai-ollama-clients`, `feat/KAN-142-ai-solution-draft`,
`feat/KAN-143-ai-solution-rewrite`, each from an up-to-date `main` (Task 2 and Task 3 need the previous task merged,
or branch from it with the user's agreement). One feature per commit, with the user's agreement.

## Task 1: Ollama chat and embedding clients (KAN-139)

**Story:** STORY-001, STORY-002, STORY-003
**Feature:** ollama-clients
**Description:** Add `AiUnavailableException`; `OllamaHttp` (two `RestClient`s with the connect / chat / embed
timeouts, the semaphore of `AI_MAX_CONCURRENT` with `AI_PERMIT_WAIT`, the one-retry rule, one log line per call with
no content); `LlmClient` + `ChatMessage` + `OllamaLlmClient` (`/api/chat`, `stream: false`, optional `format`);
`EmbeddingClient` + `OllamaEmbeddingClient` (`/api/embed`, count and 1024-length checks). Add the eight variables to
`.env.example`. Test-first against WireMock.
**Files:** backend/src/main/java/vn/tsolve/ai/{package-info.java, client/**, exception/AiUnavailableException.java};
backend/src/test/java/vn/tsolve/ai/{client/**, AiStartupTest.java}; .env.example; docs/bug-log.md for any defect.
**Dependencies:** Spec approved; module `planned`.
**Estimated size:** medium.
**Verification:** AC-001.1–001.3, AC-002.1–002.3, AC-003.1–003.8 named tests; edge cases 1–14 (log capture shows no
prompt or answer text); `mvn verify`.

## Task 2: AI draft of a Solution (KAN-142)

**Story:** STORY-004
**Feature:** solution-draft
**Description:** Add the dto records (`IssueContent`, `SolutionSections`, `SolutionDraft`), `SolutionAiService` with
`draft`, and `SolutionAiServiceImpl.draft`: the draft prompt, the five-field JSON schema, the 12,000-character cut,
the checks of AC-004.2 and the fallback draft. Tested with a fake `LlmClient`.
**Files:** backend/src/main/java/vn/tsolve/ai/{dto/**, service/SolutionAiService.java,
service/impl/SolutionAiServiceImpl.java}; backend/src/test/java/vn/tsolve/ai/service/impl/SolutionAiServiceImplTest.java;
docs/bug-log.md for any defect.
**Dependencies:** Task 1.
**Estimated size:** small–medium.
**Verification:** AC-004.1–004.5 named tests; edge cases 16–21 and 27; `mvn verify`. Tell the `tracker` owner the
draft call and `isAiDraft` → MSG67.

## Task 3: AI rewrite of a Solution (KAN-143)

**Story:** STORY-005
**Feature:** solution-rewrite
**Description:** Add `AiRewriteUnavailableException` (503, MSG68) and `SolutionAiService.rewrite` in
`SolutionAiServiceImpl`: the rewrite prompt over the filled sections, empty sections kept empty, the checks of
AC-005.3, no call when every section is empty, no save of any kind.
**Files:** backend/src/main/java/vn/tsolve/ai/{exception/AiRewriteUnavailableException.java,
service/SolutionAiService.java, service/impl/SolutionAiServiceImpl.java};
backend/src/test/java/vn/tsolve/ai/service/impl/SolutionAiServiceImplTest.java; docs/bug-log.md for any defect.
**Dependencies:** Task 2.
**Estimated size:** small.
**Verification:** AC-005.1–005.5 named tests; edge cases 23–27; `mvn verify`; `sdd.ts trace ai` shows all 24
criteria covered. Tell the `tracker` owner the rewrite call and MSG68.
