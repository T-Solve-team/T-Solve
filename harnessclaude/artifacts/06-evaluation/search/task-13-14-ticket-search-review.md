---
module: search
feature: ticket-ranking, ticket-search-api
task: 13, 14
verdict: pass
attempt: 1
date: 2026-11-06
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: search — Tasks 13 and 14: ticket ranking and the search API (KAN-144, STORY-007, AC-006.2, AC-006.6, AC-006.9, AC-006.10)

## Summary
I reviewed the uncommitted change against `72699fd` on `feat/KAN-144-search-ticket-search-api`: the new
`vn.tsolve.search.query` package (controller, DTOs, `SearchLog` entity, `InvalidSearchQueryException`, mapper,
`TicketRankingRepository`, `SearchLogRepository`, both services and `Excerpts`), its six test classes, `.env.example`
(`SEARCH_MIN_SIMILARITY=0.5`), `TicketSearchResult.resolvedAt` made nullable in `frontend/openapi/tsolve-api.yaml`,
and the regenerated `ticketSearchResult.ts` and `search.msw.ts`. I compared each STORY-007 criterion, and AC-006.2,
AC-006.6, AC-006.9 and AC-006.10, with the code and with the test that names it. All of them are met. Every native
query binds its values as parameters, the SQL text is built from constants only, and no class of the package logs
anything. Neither service is `@Transactional`, so the embedding call runs outside any transaction. There are no
CRITICAL findings. Verdict: PASS. Four Low or informational findings are below.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Semantic: `TicketRankingRepository.java:47` keeps `1 - (e.embedding <=> :query) >= :minSimilarity` (inclusive, default 0.5 from `TicketRankingServiceImpl.java:30`). `:59` orders by distance, then `t.id`. Keyword fallback: `:48-53` uses `ILIKE … ESCAPE '\'` on title, Symptoms and Resolution steps. `TicketRankingServiceImpl.java:67` escapes `\` first, then `%` and `_`. `:76` orders by `resolved_date DESC NULLS LAST, t.id DESC`. Scope: `:40-41` keeps `PUBLISHED`, an `ACTIVE` Department and `department_id IN (scope)`. An empty scope returns before the embedding call (`TicketRankingServiceImpl.java:39`). Filters: `:42-45` combine them with AND. Each optional value is bound as text and cast. The dates use `>=` and `<=`, so both days are included, and a NULL `resolved_date` matches neither. `departmentId` is compared with `d.public_id` inside the scope. `helpfulRate`: `round(100.0 * helpful / total)`, which rounds half away from zero, and NULL without Feedback (LEFT JOIN). `resolvedAt`: `TicketSearchResultMapper.java:24-28` uses `atStartOfDay(Asia/Ho_Chi_Minh)`, or null. `project.name` is `jira_project_key` (`:18`). Search Log: `TicketSearchServiceImpl.java:29-37` writes a row on page 0 only, with the trimmed `q`, `WEB`, `totalElements` and `isFallback`. `SearchLogRepository.java:17-20` finds the Department by `public_id` and writes NULL when none is given. Validation: `TicketSearchController.java:38` uses `@NotBlank @Size(max = 200)` (MSG01/MSG02 on `q`). `TicketSearchQuery.java:27-34` checks the UUID (canonical form only), the contract `source`, strict `uuuu-MM-dd` with a four-digit regex, `page ≥ 0` and `size` 1..100 (MSG06), and gives MSG39 for reversed dates. Roles: `@PreAuthorize` TICKET_RESOLVER / PROJECT_MANAGER. No Workspace parameter is read. |
| Completeness | pass | AC-007.1 to AC-007.9, AC-006.2 (empty scope means no embedding and no SQL), AC-006.6, AC-006.9 (rules reused by AC-007.7) and AC-006.10 are covered. The files and test names match plan Tasks 13 and 14. The `ponytail:` comment on the missing HNSW index is at `TicketRankingRepository.java:55`. |
| Correctness | pass | The controller ran `mvn clean verify`: 576 unit tests and 78 ITs, including `TicketRankingRepositoryIT` and `SearchLogRepositoryIT`. Spotless is clean. I did not run them myself, as the dispatcher asked (Assumption 1). I read every test. The IT seeds prove each of these: the cut-off (LOW at similarity 0 is left out), the id tie-breaker (BEST before TIED at equal distance), paging across pages, exclusion of Unpublished, DELETED-Department and out-of-scope tickets, and literal `%`/`_`/`\` (`50ABoff\` is not matched). They also prove newest first with NULL last, inclusive dates that leave out an undated ticket, 67/50/null, `+07:00` start of day or null, and a no-filter call (no `bytea` error). |
| Integration | pass | The response has the contract fields of `TicketSearchResult`, and `PagedModel` gives `{content, page}`. The YAML keeps `resolvedAt` required and makes it `[string, 'null']`. The generated `resolvedAt: string \| null` and the MSW mock follow it. The design accepts that the native SQL reads `ticket`, `project` and `department` directly (AGENTS rule 2 exception, user 06/11). Transactions sit on the repository methods, not on the service impl. This is a deliberate deviation from AGENTS §3 that the design records ("each run in their own short repository transaction"), so that no transaction spans the Ollama call (§4). |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-007.1 cut-off, order, tie-breaker, default page | PASS | `TicketRankingRepositoryIT.ranksBySimilarityAboveCutOff_AC_007_1`, `neverReturnsUnpublishedOrDeletedDepartment_AC_007_1`. `TicketRankingServiceImplTest.sendsOnlyQueryAndUsesSemanticCutOff_AC_007_1_AC_007_9` checks no transaction during embed. `TicketSearchControllerTest.ignoresWorkspaceParameter_AC_006_10` checks `PageRequest.of(0, 20)`. |
| 2 | AC-007.2 keyword fallback | PASS | `TicketRankingServiceImplTest.fallsBackToKeywordWhenOllamaFails_AC_007_2` (the escaped pattern `%50\%\_off\\%`). `TicketRankingRepositoryIT.matchesKeywordLiterallyNewestFirst_AC_007_2`. |
| 3 | AC-007.3 filters AND, inclusive dates, outside scope | PASS | `appliesFiltersAndInclusiveDates_AC_007_3`, `returnsEmptyForDepartmentOutsideScope_AC_007_3` (both paths), `answersWithNoFilterAtAll_AC_007_3`. |
| 4 | AC-007.4 excerpt, helpfulRate | PASS | `ExcerptsTest.collapsesSpaceAndCutsAt200_AC_007_4`, `returnsRoundedHelpfulRateOrNull_AC_007_4` (67, 50, null, and the mapped excerpt). |
| 5 | AC-007.5 resolvedAt | PASS | `returnsResolvedAtAsStartOfVietnamDayOrNull_AC_007_5` (through the mapper). |
| 6 | AC-007.6 page-0 Search Log | PASS | `TicketSearchServiceImplTest.logsPageZeroOnlyWithFallbackFlag_AC_007_6` (page 1 writes no row, `is_fallback` true/false, total 43, null Department, no transaction). `SearchLogRepositoryIT.writesRowWithDepartmentOfFilter_AC_007_6` (FK resolved, NULL for none or unknown). |
| 7 | AC-007.7 400 rules | PASS | Five controller tests: MSG01 (missing, empty, blank), MSG02 (201 characters), MSG39, MSG06 (14 bad filter/paging values, 7 bad dates incl. `+999999999-12-31`, `2026-1-5`, `2026-02-30`). Each checks `verifyNoInteractions(tickets)`. |
| 8 | AC-007.8 401/403 | PASS | `answers401And403_AC_007_8` (MSG07, MSG08, no service call). |
| 9 | AC-007.9 only query to Ollama, no text in logs | PASS | `embed(List.of(text))` only. No logger in the `query` package. `OllamaHttp` logs no body. `SqlExceptionHelper` is OFF in `application.yml`. `neverLogsQueryText_AC_007_9` attaches a root appender. |
| 10 | AC-006.2 / AC-006.6 / AC-006.10 | PASS | `skipsEmbeddingForEmptyScope_AC_006_2`, `usesJiraProjectKeyAsProjectName_AC_006_6`, `ignoresWorkspaceParameter_AC_006_10` (query parameter and header). |

### Stage 2 — Quality

CRITICAL: none.

Security: every value in both native queries and in the insert is a named bind parameter. The concatenated
fragments (`COLUMNS`, `TABLES`, `FEEDBACK`, `FILTERS`, `SIMILARITY`, `KEYWORD`) are compile-time constants. The
vector is bound as a text literal and cast. `departmentId` is a parsed UUID, and `source` comes from a fixed set.
There is no SQL injection path. Visibility fails closed on an empty scope.

Low / informational (does not block):
1. **Low** [`TicketSearchController.java:33,38`] Spring MVC's built-in method validation runs before the
   `@PreAuthorize` proxy. A signed-in user without either role who sends a blank or 201-character `q` gets 400
   MSG01/MSG02, not 403 MSG08. Nothing leaks, and no embedding or log row is made. `TicketListController` has the
   same order. The spec does not say which wins (Assumption 2).
   Fix, optional: validate `q` inside the method (`TicketSearchQuery.parse`), or add a test that fixes the order.
2. **Low** [`TicketSearchServiceImpl.java:30`, `TicketRankingServiceImpl.java:51`] A `q` with an inner NUL
   character (`q=a%00b`) passes `@NotBlank`. PostgreSQL rejects `0x00` in text, so the `search_log` insert (and the
   fallback `ILIKE`) fails with 500, after the embedding call. The list keyword has the same gap.
   Fix, optional: reject control characters in `TicketSearchQuery.parse` (400 MSG06), and log a row in
   `docs/bug-log.md` if accepted as a bug.
3. **Info** [`Excerpts.java:10`] The cut comes after the trim, so an excerpt can end in a space
   (`ExcerptsTest` line 13 asserts `"x"*199 + " "`). The cut can also split a surrogate pair. Both are within "at
   most 200 characters".
   Fix, optional: `strip()` after the cut.
4. **Info** [`TicketRankingRepository.java:30-37`] The `helpfulRate` derived table aggregates the whole
   `ticket_feedback` table on every query. This is fine at the planned volume, and it is the same ceiling as the
   exact vector scan.
5. **Info** No test checks the JSON form of `resolvedAt` (`+07:00` on the wire). Spring Boot's default Jackson keeps
   the offset of an `OffsetDateTime`, and no `spring.jackson.time-zone` is set, so the wire form is right today.

## Assumptions made
1. **Tests not run by the reviewer (open judgment call).** The dispatcher told me not to run Docker or mvn.
   Correctness rests on the controller's `mvn clean verify` run (576 unit, 78 IT, Spotless clean). I ran only
   `sdd.ts trace`. The frontend `typecheck` and the "`generate:api` leaves no diff" check (plan Task 13) were not
   reported to me. I checked by reading that the generated diff matches the YAML change.
2. **403 versus 400 order (open judgment call).** AC-007.8 and AC-007.7 do not say which answer wins when a caller
   without a role also sends an invalid `q`. I judged it non-blocking (Low 1), because no data, embedding or log
   row comes out of it.
3. **Tie-breaker direction.** AC-007.1 says "the internal ticket id as the tie-breaker" without a direction. The
   code uses ascending (`t.id`). AC-007.2 asks for descending, and the code uses descending there.
4. **`q` length on the raw value.** `@Size(max = 200)` counts the untrimmed `q` in UTF-16 units, the same as the
   contract's `maxLength: 200`. A 199-character query with surrounding spaces past 200 is rejected.
5. **Search Log for an out-of-scope or unknown `departmentId`.** The row names the Department when it exists, and
   NULL when it does not. AC-007.6 says "the Department of `departmentId`", so I accept both.
6. **Feature statuses not changed.** The dispatcher asked for a read-only review. The main session moves
   `ticket-ranking` and `ticket-search-api` to `passing` with `sdd.ts feature`.

## Traceability
```
  ✓ AC-007.1  TicketRankingServiceImplTest, TicketRankingRepositoryIT
  ✓ AC-007.2  TicketRankingServiceImplTest, TicketRankingRepositoryIT
  ✓ AC-007.3  TicketRankingRepositoryIT
  ✓ AC-007.4  ExcerptsTest, TicketRankingRepositoryIT
  ✓ AC-007.5  TicketRankingRepositoryIT
  ✓ AC-007.6  TicketSearchServiceImplTest, SearchLogRepositoryIT
  ✓ AC-007.7  TicketSearchControllerTest
  ✓ AC-007.8  TicketSearchControllerTest
  ✓ AC-007.9  TicketRankingServiceImplTest, TicketSearchServiceImplTest
trace search (STORY-007): 9/9 acceptance criteria have a test
trace search (STORY-006): 12/12 (AC-006.2, AC-006.6, AC-006.10 include the new query tests)
```
I read each test that names a criterion. Each one asserts the behaviour, and none only mentions the id.

## Issues
None blocking. Low 1-2 and Info 3-5 are under Stage 2.

## Test Report
- `mvn clean verify`: 576 unit and 78 IT tests pass, including `TicketRankingRepositoryIT` (9) and
  `SearchLogRepositoryIT` (1). Spotless is clean. The controller ran these, not the reviewer (Assumption 1).
- `bun harnessclaude/hooks/sdd.ts trace search STORY-007`: 9/9. `STORY-006`: 12/12. The reviewer ran both.
- Scope: `git status --porcelain -uall` lists only the `query` main and test files, `.env.example`, the YAML, the
  two generated files, `progress.json`, `events.jsonl` and `frontend/src/features/search/mocks/handlers.ts`. That last
  file is excluded by the dispatcher and was not reviewed.
