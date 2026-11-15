---
module: jira-app
feature: solution-ai
task: 4
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 4: AI draft and rewrite (solution-ai)

## Summary

The feature owns commit `487e600d`. That commit adds `SolutionFormAi` and `SolutionFormAiImpl` (backend `jiraapp`), the
`SolutionFormDraft` record, `SolutionFormAiImplTest`, and three `.env.example` lines. None of these files changed
between `487e600d` and HEAD (`git diff 487e600d HEAD` on them is empty). The task text in `tasks.md` asks for our own
`LlmSolutionAiService` / `SolutionPrompt`. Design row 96 (12/11) changed this: the feature wraps the `ai` module's
existing `SolutionAiService` (KAN-142), so the prompt, JSON schema and JSON parsing belong to `ai`. I reviewed them as
the parts this feature depends on.

What the feature does: an 18 s deadline on its own bounded pool; the AC-002.2 fallback (summary and description in
Symptoms, cut to 10,000 code points); MSG68 on any rewrite failure, timeout or full pool; a stub switch that is safe by
default. The draft never throws. It logs only error type names. It holds no transaction or DB connection during the
LLM call. The three task criteria (AC-002.1, AC-002.2, AC-007.4) each have a test that checks them. I found no
CRITICAL issue. The informational findings are below.

I did not run the tests: the dispatcher told me not to run Maven, and the main session runs the backend suite. This
verdict depends on that suite passing (see the test report).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.1: `SolutionFormAiImpl.java:99-101` gives the AI sections with `aiDraft=true`. AC-002.2: `:91`, `:101`, `:102-105`, `:144-150` give Symptoms from the issue when the AI falls back, throws, times out or the pool is full, cut to `MAX_SOLUTION_LENGTH` code points; the caller then adds MSG67 (`JiraAppSolutionServiceImpl.java:71-74`). AC-007.4: `:116-121` turns every failure into `AiRewriteUnavailableException` (503 MSG68), which leaves the Solution unchanged because no state is changed. |
| Completeness | pass | Every task criterion is covered. One EXTRA: `TSOLVE_STUB_AI` (user decision 11/11, design line 54). The task's malformed-JSON test is in the `ai` module, not here (`SolutionAiServiceImplTest` `malformedAnswersFallBack`, `rejectsMalformedRewriteAnswer`), because parsing is done there. |
| Correctness | pass (pending suite) | Not run by me (no Maven). The 11 unit tests in `SolutionFormAiImplTest.java` cover AI draft, fallback, failure, deadline, the code-point cut, rewrite, MSG68 on failure and on deadline, both stub modes, and parsing of the stub setting. No CRITICAL finding. |
| Integration | pass | `SolutionFormAi` is used only by `JiraAppSolutionServiceImpl` (`:71`, `:105`). That class is not `@Transactional` (`:31`). `JiraIssueAccess.inIssue` / `asUser` only set a ThreadLocal (`LocalTenantScope.java:26-39`), and `spring.jpa.open-in-view: false` (`application.yml:25`), so no connection is held while the AI runs. The Forge client does not retry `solution-form` or `solution-rewrites` and gives up at 20 s (`tsolveClient.js:7`, `:153-175`); it keeps MSG68 from a 503 (`:175`). |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-002.1 AI draft fills the five sections | PASS | `SolutionFormAiImpl.java:99-101`; test `opensWithTheAiDraftOfTheIssue_AC_002_1` |
| 2 | AC-002.2 unavailable or too slow → Symptoms from issue, others empty (+MSG67 by caller) | PASS | `:91-105`, `:144-159`; tests `fillsSymptomsFromTheIssueWhenTheAiFallsBack_AC_002_2`, `...WhenTheAiFails_...`, `...WhenTheAiMissesTheDeadline_...` (asserts < 2 s with a 100 ms deadline), `cutsAnIssueLongerThanTheSolutionLimit_AC_002_2`, `behavesAsAnUnavailableAiWhenStubbedUnavailable_AC_002_2_AC_007_4` |
| 3 | AC-007.4 rewrite fails / unavailable / too slow → MSG68 | PASS | `:109-121`, `:124-137`; tests `answersMsg68WhenTheRewriteFails_AC_007_4` (checks code `MSG68`), `answersMsg68WhenTheRewriteMissesTheDeadline_AC_007_4` |
| 4 | AC-007.1 (also exercised) rewrite returns five sections | PASS | `:117`, `:186-202`; test `rewritesTheFiveSections_AC_007_1` |
| — | `TSOLVE_STUB_AI` stand-in | EXTRA (justified) | design.md line 54 and row 96, user decision 11/11 |

### Points the dispatcher asked about

- **Deadline vs. Forge/UI budget.** The deadline defaults to 18 s (`:68`) and must be positive (`:73-75`). The spec
  allows 20 s per UI call (spec line 45, NF 2). The 18 s covers the Ollama permit wait (1 s), which happens inside the
  AI thread. That leaves about 2 s for the Forge-to-backend round trip and the DB reads before the AI call
  (`JiraAppSolutionServiceImpl.java:62-67`). Tight, but it fits; see Informational 3.
- **Timeout / malformed JSON / missing sections.** Timeout: `orTimeout` (`:127`) gives fallback or MSG68. Malformed
  JSON, a missing or non-text field, a blank required section, or more than 10,000 characters: the `ai` module falls
  back (`SolutionAiServiceImpl.java:98-117`, `aiDraft=false`), and this feature then uses its own AC-002.2 fallback
  (`:101`). For a rewrite the same cases throw `AiRewriteUnavailableException` (`SolutionAiServiceImpl.java:147-171`),
  which is then mapped again to a clean MSG68 (`:118-120`). A full pool gives `RejectedExecutionException`, then
  `AiCallFailed("Busy")` (`:134-135`), then fallback or MSG68.
- **Prompt injection.** The draft prompt wraps issue text in `<summary>` / `<description>` / `<comment>` tags and says
  the tagged text is data (`SolutionAiServiceImpl.java:42-50`). There are two gaps, both in the `ai` module: the tags
  are not escaped, and the rewrite prompt has no such sentence (Informational 2).
- **No issue or Solution text in logs.** This feature logs only `errorType` (`:103`, `:119`). That value is a class
  simple name or a fixed word (`:131-140`). `AiCallFailed` has no cause and no stack (`:224-228`). The `ai` module
  logs only reason enums (`SolutionAiServiceImpl.java:94,116`), and `OllamaHttp` logs only op, model, duration and
  error type (`OllamaHttp.java:166-176`). Clean.
- **Stub switch in production.** Empty or unset means the real AI (`:46-48`, default `${TSOLVE_STUB_AI:}` at `:66`).
  Any other value fails startup (`:51`). `.env` is git-ignored (`.gitignore:16`). Neither `docker-compose.yml` nor
  `application*.yml` sets the switch. So the stub runs only if someone sets it on purpose. There is no profile guard
  and no startup warning (Informational 4).
- **Transaction / connection during the LLM call.** None (see Integration).

## Traceability

```
  ✓ AC-002.1  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/SolutionFormAiImplTest.java
  ✓ AC-002.2  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/background/watchDone.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/SolutionFormAiImplTest.java
  ✓ AC-002.3  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-002.4  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-002.5  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-007.1  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/SolutionFormAiImplTest.java
  ✓ AC-007.2  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-007.3  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-007.4  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/SolutionFormAiImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
trace jira-app (STORY-002, STORY-007): 9/9 acceptance criteria have a test
```

I read the backend tests named for this task's criteria (AC-002.1, AC-002.2, AC-007.4) in `SolutionFormAiImplTest`.
Each one checks its criterion, not just its name.

## Issues

CRITICAL (blocks pass): none.

Informational (does not block):

1. **[SolutionFormAiImpl.java:124-128] A timed-out call keeps running and holds an Ollama slot.**
   `orTimeout` completes the future but does not stop the running `ai` call. The pool thread then stays inside
   `OllamaHttp` until `AI_CHAT_TIMEOUT` (default 60 s, `OllamaHttp.java:52`) and holds one of the
   `AI_MAX_CONCURRENT=2` permits (`OllamaHttp.java:54,107`) that the web app's AI calls also use. If Ollama is slow,
   two abandoned Jira App calls can make web AI calls fail as Busy for up to a minute after the Jira user has already
   had MSG67 or MSG68. The comment at `:61-62` covers pool threads but not Ollama permits. Queued tasks whose deadline
   has passed are skipped by `CompletableFuture`, so this affects only calls already running.
   Fix: call `future.cancel(true)` on timeout (HTTP reads ignore interrupts, so this alone is not enough), or give the
   `ai` call a read timeout under the 18 s (for example a per-call deadline or a Jira-App-specific chat timeout). Or
   accept the cost and note it in design.md Edge Cases row 8.
2. **[ai module, not this commit: SolutionAiServiceImpl.java:42-58] Prompt-injection guard is partial.**
   Issue text sits inside tags but is not escaped, so a description that contains `</description>` can close its tag
   early. The rewrite prompt (`:51-58`) has no "this is data" sentence. On a fallback, Symptoms holds the reporter's
   issue text (`SolutionFormAiImpl.java:144-149`), so text written by a reporter can reach the rewrite prompt. The
   impact is small: the output is plain text in a preview that the user must Accept (AC-007.2), and the model has no
   tools. Fix (owner of `ai`, KAN-142): escape or strip the closing tags in issue text; add the data-not-instructions
   sentence to the rewrite prompt and tag each section.
3. **[SolutionFormAiImpl.java:68] Small margin under the 20 s client cutoff.** A server deadline of 18 s plus the DB
   reads before the AI call and the Forge round trip leaves about 2 s. If the server answers after the client's 20 s
   (`tsolveClient.js:7`), the user sees MSG06 instead of MSG67 or MSG68. Fix: if this happens in demos, lower the
   default to 15-16 s; or keep 18 s and accept the risk.
4. **[SolutionFormAiImpl.java:64-70] Nothing is logged when the stub is on.** With `TSOLVE_STUB_AI=available`, users
   get canned drafts marked `aiDraft=true` (`:95-96`), and no log line shows this. The default is safe; this only
   helps someone notice the setting was left on by mistake. Fix: log one WARN at startup when the mode is not `AI`.
5. **[SolutionFormAiImplTest.java] Untested paths.** No test covers the full-pool path (`:134-135`), the
   Spring-constructor checks (`JIRA_APP_AI_THREADS < 1` at `:205-207`, timeout of zero or less at `:73-75`), or the
   claim that a failure log carries no text (no `CapturedOutput` assertion like the one in the `ai` module).
   Fix: add these tests.

## Assumptions made

- I judged the code against design row 96 (wrap the `ai` module's `SolutionAiService`), not against the older Task 4
  file list (`LlmSolutionAiService`, `SolutionPrompt`, `StubSolutionAiService`, `application.yml`). The design records
  that the plan changed on 12/11. The user should confirm that `tasks.md` Task 4 needs no update.
- I accept the `ai` module's malformed-JSON tests (not named with an AC id) as coverage for the task's "AC-007.4
  (malformed JSON)" request, because the parsing happens in that module.
- I count the partial prompt-injection guard (Informational 2) as non-blocking, because the output is plain text that
  the user must Accept. The user should decide whether it blocks.
- I count the abandoned in-flight call after a timeout (Informational 1) as non-blocking, because the spec only
  requires the user-facing behaviour, and that is met.
- The verdict assumes the main session's `mvn verify` passes. If any test in `SolutionFormAiImplTest` fails, the
  verdict becomes fail.

## Test Report

See task-4-solution-ai-test-report.md
