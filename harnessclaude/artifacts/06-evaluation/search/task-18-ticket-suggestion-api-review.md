---
module: search
feature: ticket-suggestion-api
task: 18
verdict: pass
attempt: 1
date: 2026-11-09
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: search — Task 18: Similar-ticket suggestions API (KAN-146, STORY-009, AC-006.10)

## Summary

Reviewed the uncommitted changes on `feat/KAN-146-search-suggestion-api` (base `origin/main` f629dfd) against
`harnessclaude/artifacts/03-modules/search/spec.md` (STORY-009, AC-006.10, Interface Contracts). The implementation is
small and reuses `vn.tsolve.search.query` without copying it. `TicketSuggestionServiceImpl` calls
`VisibleScope.of(user)`, then `TicketRankingService.rank(text, summary, empty RankFilter, scope, PageRequest.of(0, 5))`.
It writes the log row through `SearchLogRepository.log(…, "ISSUE_PANEL", items.size(), isFallback)` and returns
`TicketSearchResultResponse` items. The service has no transaction. The Ollama call happens inside
`TicketRankingServiceImpl`, which has no transaction. `open-in-view` is false, and the only transaction is the
log-write transaction owned by the repository. The contract change only adds a path and a schema. The generated client
is regenerated. All 12 new tests pass. Traceability is 5/5 for STORY-009, and AC-006.10 has a test that names it.
There are no blocking findings. One Medium finding remains: the summary and description length limits count UTF-16
units, not characters, which is the same class of problem as BUG-155. It is recorded below as a judgment call for the
user.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-009.1: the ranking text is `trim(summary) + "\n\n" + trim(description)`, or the summary alone when the description is null or blank. The keyword text is the trimmed summary. Results are page 0 of 5, and the item fields come from the shared mapper. AC-009.2: fallback is decided by `TicketRankingServiceImpl`, which catches `AiUnavailableException`. Every Ollama failure, including 4xx, timeout and bad JSON, maps to `AiUnavailableException` in `OllamaHttp.failed`, so it never answers 500. AC-009.3: one row with the user's id, a null Department, the trimmed summary, `ISSUE_PANEL`, `items.size()` and `isFallback`. The `ck_search_log_channel` constraint allows `ISSUE_PANEL`, and `query` is `TEXT`, so a 255-character summary fits. AC-009.4: `@NotBlank` gives MSG01, and `@Size` gives MSG02 with `max_length`. Bean validation runs before the service, so no embedding and no log row happen. AC-009.5: `@PreAuthorize` for TICKET_RESOLVER and PROJECT_MANAGER on the web chain. AC-006.10: no Workspace field. Unknown query parameters, headers and body fields are ignored. |
| Completeness | pass | Every criterion in STORY-009 plus AC-006.10 is implemented. The test names Task 18 lists all exist. Four more tests were added: NUL in the summary, PM allowed, the response field shape, and a failed log write. |
| Correctness | pass | `mvn -q -Dtest='TicketSuggestion*' test` passes: the controller test has 8/8 and the service test has 4/4. Results from the orchestrator, not re-run here: backend unit tests 938/938; the search ITs pass; frontend format, lint, typecheck and build pass; `generate:api` leaves no diff. One Medium point on how length is counted (Issue 1). |
| Integration | pass | `POST /api/v1/search/suggestions`, `suggestTickets`, tag `search`, `TicketSuggestionRequest {summary 1..255, description string\|null ≤32767}` and the 200 `TicketSearchResult[]` (maxItems 5) match the spec's Interface Contracts. The change only adds things: the generated diffs only add exports, imports and handlers. `JiraAppSecurityConfig` uses `securityMatcher("/api/v1/jira-app/**")`, so the FIT chain does not cover this path. The web `SecurityConfig` has no bearer or resource-server handling, so a Forge token alone gets 401 MSG07. |

### Specific checks requested by the dispatcher

1. **Every AC has a test with its id.** Yes. See Traceability below.
2. **Reuse of `vn.tsolve.search.query`.** Yes. The code uses `TicketRankingService`, `RankFilter`, `RankedPage`,
   `TicketSearchResultResponse`, `SearchLogRepository` and `VisibleScope`. It adds no new repository, mapper, SQL or
   embedding call. The log-failure pattern matches `TicketSearchServiceImpl`.
3. **Only the files in Task 18 changed.** Yes. The changes are the 4 main files, the 2 tests, `tsolve-api.yaml` and
   `generated/**` (`model/index.ts`, `model/ticketSuggestionRequest.ts`, `search/search.ts`, `search/search.msw.ts`).
   The rest is harness bookkeeping in `progress.json` and `events.jsonl`.
4. **The contract change only adds things and matches the spec.** Yes. `maxItems: 5` was added to the 200 array,
   which is consistent with "at most 5".
5. **Bug-log lessons.**
   - BUG-150 (NUL byte): handled for `summary`. `@Pattern("[^\\x00]*")` gives 400 MSG06 on `summary`, the same code
     as BUG-150, and the test is `rejectsNulInSummaryBeforeSuggesting_AC_009_4`. A NUL in `description` is not
     rejected, but it can reach only Ollama. It is never in the keyword SQL or `search_log`, because both use the
     summary. Any Ollama error becomes `AiUnavailableException` and then the fallback, so it cannot cause a 500. I
     accept this.
   - BUG-155 (raw UTF-16 length): **not carried over.** See Issue 1.
   - BUG-156 (a failed log write must not fail the answer, and no query in the log): handled.
     `returnsSuggestionsWhenSearchLogFails_AC_009_3` checks a single WARN that holds only the exception class. It
     checks that the summary, the description and the ticket title are absent, and that there is no throwable.
     AC-007.9 holds: the class logs nothing else.
6. **No mock data, and no transaction around Ollama.** Confirmed. The only fakes are the generated MSW and faker
   handlers in `generated/**`, which orval generates for every operation. No screen uses them.
7. **Security.** Confirmed. The endpoint is reached only through the web session. No FIT chain covers it.

## Traceability

```
bun harnessclaude/hooks/sdd.ts trace search STORY-009
  ✓ AC-009.1  …/search/suggestion/service/impl/TicketSuggestionServiceImplTest.java, …/search/suggestion/controller/TicketSuggestionControllerTest.java
  ✓ AC-009.2  …/search/suggestion/service/impl/TicketSuggestionServiceImplTest.java
  ✓ AC-009.3  …/search/suggestion/service/impl/TicketSuggestionServiceImplTest.java
  ✓ AC-009.4  …/search/suggestion/controller/TicketSuggestionControllerTest.java
  ✓ AC-009.5  …/search/suggestion/controller/TicketSuggestionControllerTest.java
trace search (STORY-009): 5/5 acceptance criteria have a test

bun harnessclaude/hooks/sdd.ts trace search STORY-006 (AC-006.10 line)
  ✓ AC-006.10  …/search/suggestion/controller/TicketSuggestionControllerTest.java, (+ list, search, feedback tests)
```

## Issues

No blocking issues.

1. **Medium (non-blocking, judgment call). The length limits count UTF-16 units, not characters, so the BUG-155
   lesson is not carried over.**
   - **Where:** `backend/src/main/java/vn/tsolve/search/suggestion/dto/TicketSuggestionRequest.java:9-10`. Both fields
     use `@Size`, and Hibernate's `@Size` counts `String.length()`, which is UTF-16 units.
   - **Effect:** a summary of 255 emoji or other supplementary characters gets 400 MSG02. Yet the contract's JSON
     Schema `maxLength: 255` counts code points, and AC-009.4 says "longer than 255 characters". The same applies to
     a description over 16383 supplementary characters.
   - **Comparison:** BUG-155 fixed the same gap for `q` (`TicketSearchQuery` uses `codePointCount`). The real impact
     is limited: Jira itself enforces 255 per Java `String.length()`, so a summary taken from Jira will not go over.
   - **Fix, if the user wants parity:** count code points in the service or a validator, keeping the MSG02 field
     error and `max_length`. Add a test such as `accepts255EmojiSummary_BUG_xxx`, and log a bug row in
     `docs/bug-log.md`.
   - **Otherwise:** state in the spec that the limit is in UTF-16 units, as Jira counts.
2. **Low (informational). A summary of 255 characters plus surrounding spaces is rejected.**
   - **Where:** `TicketSuggestionRequest.java:9`.
   - **Effect:** such a summary gets 400, because `@Size` counts before the trim. BUG-155 changed `q` to count after
     the trim.
   - **Why it is not a defect:** Jira trims summaries, and the contract `maxLength` applies to the raw value, so this
     is consistent with the contract.
3. **Low (informational). Blank-checking uses two different rules.**
   - **Where:** `@NotBlank` and `String.trim()` treat only characters ≤ U+0020 as blank. `TicketSuggestionServiceImpl`
     line 38 checks the description with `isBlank()`, which uses Unicode white space.
   - **Effect:** a summary made only of U+3000 passes validation and is ranked as a one-character query. Nothing
     breaks: there is no 500 and the log row is valid.
   - **No action needed.**

## Assumptions made

- **NUL in `summary` gives MSG06 on `summary`.** AC-009.4 does not list NUL. I applied the BUG-150 decision for `q`
  (MSG06 on the field), which the bug log records as agreed.
- **NUL in `description` is accepted.** I judged this acceptable because the description reaches only Ollama, and
  every Ollama failure falls back. This is a judgment call for the user: should `description` also reject NUL, for
  symmetry?
- **How to count "characters" in AC-009.4 (Issue 1).** I did not fail the review on it. The spec says "characters (the
  Jira field limits)", and Jira counts UTF-16 units. The contract's JSON Schema `maxLength` counts code points. This
  is an open judgment call for the user: choose code-point parity with BUG-155, or a spec note saying UTF-16.
- **Unknown body fields such as `workspaceId` are ignored, not rejected.** I read AC-006.10 ("SHALL change nothing in
  the answer") as allowing an unknown field to be ignored. This is the same as the other search endpoints.
- **No IT for the suggestion path.** I accepted unit tests only. Task 18 lists no IT, and the ranking SQL, the
  `search_log` insert and the `ISSUE_PANEL` channel constraint are already covered by the search ITs
  (`SearchSchemaIT` inserts an `ISSUE_PANEL` row).
- **No feature status change.** I did not run `sdd.ts feature` because the dispatcher limited me to the report file.
  The main session should run `bun harnessclaude/hooks/sdd.ts feature search ticket-suggestion-api passing`.

## Test Report

| Run | Result |
|-----|--------|
| `cd backend && mvn -q -Dtest='TicketSuggestion*' -Dsurefire.failIfNoSpecifiedTests=false test` (run by this reviewer, 2026-11-09) | exit 0. `TicketSuggestionControllerTest` has 8 tests, 0 failures and 0 errors. `TicketSuggestionServiceImplTest` has 4 tests, 0 failures and 0 errors. |
| Backend unit suite (orchestrator) | 938/938 pass |
| Search ITs (orchestrator) | pass. The unrelated Keycloak-startup and ReviewQueueEventsIT timing failures were being re-run. They are not in the search module. |
| Frontend `format:check`, `lint`, `typecheck`, `build` (orchestrator) | pass |
| `npm run generate:api` twice (orchestrator) | no diff in `src/shared/api/generated` |
| `sdd.ts trace search STORY-009` (this reviewer) | 5/5 |

Tests in the change:
- `TicketSuggestionControllerTest`:
  - `rejectsMissingOrLongSummary_AC_009_4`
  - `rejectsLongDescription_AC_009_4`
  - `rejectsNulInSummaryBeforeSuggesting_AC_009_4`
  - `refusesForgeTokenAlone_AC_009_5`
  - `answers403ForEmployee_AC_009_5`
  - `allowsProjectManager_AC_009_5`
  - `ignoresWorkspaceParameter_AC_006_10`
  - `returnsResultArrayWithTicketFields_AC_009_1`
- `TicketSuggestionServiceImplTest`:
  - `ranksSummaryAndDescriptionTopFive_AC_009_1`
  - `fallsBackToSummaryKeyword_AC_009_2`
  - `logsIssuePanelRowWithReturnedCount_AC_009_3`
  - `returnsSuggestionsWhenSearchLogFails_AC_009_3`
