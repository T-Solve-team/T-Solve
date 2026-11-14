---
module: triage
feature: suggestion-generation
task: 4
verdict: pass
attempt: 1
date: 2026-11-08
reviewer: harnessclaude-reviewer
iteration: 1  # progress.json currentIteration: 1
---

# Review: triage — Task 4: Generate the AI Solution Suggestion (KAN-47)

## Summary
`SuggestionGenerator` implements the tracker's port `SuggestionSearch` and meets every remaining STORY-005 criterion (AC-005.3–005.8, 005.11–005.14). Code range: `e60a93b^..e60a93b`. The blank-summary short-circuit, the 8000-character issue text, the retrieval of both source kinds at the configured threshold with 5 each, the stable merge to the top 5 (tickets first on ties), no chat call without sources, the ticket-private prompt, the `SuggestionSource` shapes and the text-free failures all match the spec. The code runs no transaction and logs nothing. `TriageProperties` binds `SUGGESTION_MIN_SIMILARITY` with default 0.7 and stops startup on a value outside 0..1, including `NaN` and text. The tracker stand-in, its test, `tsolve.stub.suggestion` and `TSOLVE_STUB_SUGGESTION` are gone from code, config, tests, `.env.example` and docs. The full `mvn verify` passes (1012 unit + 272 IT, Spotless), so every Spring context starts with exactly one `SuggestionSearch` bean. Four Low/Nit findings remain, none blocking.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-005.3: `issueText` joins with `"\n\n"`, uses the summary alone for null/blank descriptions, caps at 8000 characters, and embeds once. AC-005.4: `nearestInProjectDepartment(projectId, v, min, 5)` and `wiki.nearest(v, min, 5)` are merged, stably sorted by similarity descending and limited to 5. AC-005.5: the prompt holds the capped issue text and the kept sources only, and the result is `IssueSuggestion(kept sources, answer.strip())`. AC-005.6: `TICKET` items carry the ticket `public_id` with a null title; `WIKI` items carry the id and title. The prompt has the five Solution sections numbered "Solution N", no ticket id (the search DTO has no key or title), and Wiki documents by title + content (≤ 2000), never by id. AC-005.7: empty sources mean no chat call and `Optional.empty()`. AC-005.8: `AiUnavailableException` (which carries no body) passes through, and a blank answer throws `IllegalStateException("Blank suggested solution")`. AC-005.13: a null/blank summary touches no collaborator. NF 5: no `@Transactional` and no logger; the tracker job calls it outside a transaction. |
| Completeness | pass | All 10 remaining criteria are implemented and traced. The task's files match the task list (create 5, delete 2, modify `application.yml` and `.env.example`). Edge cases 25, 27, 29, 31, 34, 35 and 36 are tested directly. Edge case 30 (0.69 / exactly 0.7) is checked only as "the threshold is passed to both ports"; the boundary itself sits in the ports (see Issue 3). |
| Correctness | pass | Focused tests pass (12/12). The reviewer's full `mvn -q verify` exited 0: 1012 Surefire, 272 Failsafe, `spotless:check`. Trace 10/10. |
| Integration | pass | It matches the tracker's port `suggest(UUID, String, String)` → `Optional<IssueSuggestion>`. The caller `SuggestionJobServiceImpl` passes `result.getProjectId()`, which is the Project `public_id` (changeset 019, entity comment), as AC-005.4 needs. It maps empty to "no suggestion" and wraps any exception into `SuggestionJobFailedException` with the type only, so the generator's throw-on-failure contract fits. `SuggestionSource` is serialized into `sources_used` in the shape triage's own `SourcesUsed` reader accepts (string `id`; `title` only checked for `WIKI`). Search's `nearestInProjectDepartment(UUID projectId, …)` takes the Project `public_id`. `EmbeddingClient` / `LlmClient` are used as the chatbot and search modules already use them. The only `implements SuggestionSearch` is `SuggestionGenerator`, and no other module imports `vn.tsolve.triage`, so there is no cycle. `TriageProperties` is picked up by `@ConfigurationPropertiesScan` on `TSolveApplication`. `NoWikiSources` is named `triageNoWikiSources` to avoid the chatbot stand-in's bean name. |

## Traceability

```
  ✓ AC-005.3  backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionGeneratorTest.java
  ✓ AC-005.4  backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionGeneratorTest.java
  ✓ AC-005.5  backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionGeneratorTest.java
  ✓ AC-005.6  backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionGeneratorTest.java
  ✓ AC-005.7  backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionGeneratorTest.java
  ✓ AC-005.8  backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionGeneratorTest.java
  ✓ AC-005.11  backend/src/test/java/vn/tsolve/common/config/TriagePropertiesTest.java
  ✓ AC-005.12  backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionGeneratorTest.java
  ✓ AC-005.13  backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionGeneratorTest.java
  ✓ AC-005.14  backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionGeneratorTest.java
trace triage (STORY-005): 10/10 acceptance criteria have a test
```

## Issues

No blocking issue. Non-blocking findings:

1. `backend/src/main/java/vn/tsolve/tracker/service/SuggestionSearch.java:7-11` — **Low** — The port's Javadoc is now false. It says the suggestion is "owned by `search`" and that "a configured stand-in answers until KAN-47 implements it". Since AC-005.14, triage's `SuggestionGenerator` implements it and there is no stand-in. `tracker/dto/IssueSuggestion.java:5` ("What search suggests") is stale in the same way. **Fix:** reword both comments to say that triage's `SuggestionGenerator` implements the port (KAN-47, triage STORY-005). The next tracker or triage change can do this as a quick change.
2. `backend/src/main/java/vn/tsolve/triage/service/impl/SuggestionGenerator.java:71-77` — **Low** — The generator trusts both ports to apply `minSimilarity`. It has no own `similarity >= minimum` filter. The search implementation is tested to do so. The future wiki implementation of `WikiSourceRetrieval` is not, and if it ignores the argument, below-threshold Wiki documents would become sources and reach the model, silently breaking AC-005.4/AC-005.7. **Fix (optional):** add `.filter(s -> s.similarity() >= minimum)` before `sorted`, plus one test where a port returns a 0.69 source with threshold 0.7 and the chat model is not called (edge case 30).
3. `backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionGeneratorTest.java:72-95` — **Low** — `keepsAtMostFiveOfBothAtTheThresholdMostSimilarFirst_AC_005_4` stubs a 0.7 ticket, but the 5-item limit drops it, so the test never shows a source exactly at the threshold being kept. It checks only that 0.7 is passed to the ports. **Fix (optional):** together with Issue 2, add a case where a source at exactly 0.7 is kept and one at 0.69 is not.
4. `backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionGeneratorTest.java:64` — **Nit** — The code uses fully-qualified `org.mockito.Mockito.times(2)` while all other Mockito helpers are statically imported. **Fix:** add `import static org.mockito.Mockito.times;`.

### Assumptions made
- **PII masking (AGENTS §4, "Text sent to an external AI service goes through PII masking first").** The issue summary/description and the Solution sections go to `EmbeddingClient`/`LlmClient` unmasked. No masking utility exists anywhere in the backend. The existing AI callers (`ChatbotAnswerServiceImpl`, search's embedding services) send text the same way. The spec does not ask for masking. I treated the self-hosted Ollama as not "external" and did not fail on this. **Open judgment call for the user:** should masking be required here (and in the other AI callers), which would need a spec change?
- **"Wiki documents by title only" (AC-005.6)** is read as "identified to the model by title, never by id". The document's content (capped at 2000 characters) may still be in the prompt, as the design states.
- **AC-005.14** is judged satisfied by three things together: the classpath-scan test, a repository-wide search that finds no `StubSuggestionSearch` / `tsolve.stub.suggestion` / `TSOLVE_STUB_SUGGESTION` outside `harnessclaude/` history, and every IT context starting with no `NoUniqueBeanDefinitionException`. The scan does not see `@Bean` factory methods, but none exist for this type.
- **Tracker job ↔ generator** is accepted as unit-tested on each side with fakes; the spec's Definition of Done asks for "fake models and retrieval". No end-to-end IT runs the real generator from the job.
- The threshold filter is accepted as the ports' job, since AC-005.4 says retrieval happens "through" the ports "at or above" the threshold. Issue 2 is therefore a hardening suggestion, not a criterion failure.

## Test Report
See `task-4-suggestion-generation-test-report.md`. Trace 10/10. Focused tests: `SuggestionGeneratorTest` 10/10 and `TriagePropertiesTest` 2/2. The reviewer's full `mvn -q verify` exited 0: 1012 unit, 272 integration (Testcontainers), `spotless:check` pass. No coverage tool is configured.
