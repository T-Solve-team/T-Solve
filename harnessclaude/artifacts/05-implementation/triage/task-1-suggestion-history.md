# Task 1: Suggestion History list and detail (KAN-149)

**Module:** triage
**Assignee:** Trần Hải Anh
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-11-02

### Working on: suggestion-history
- User confirmed the two remaining Hải Anh FE areas are Knowledge Dashboard and Suggestion History, and chose to include the 10.2 detail popup with the 10.1 list.
- Triage spec has two stories and 20 criteria. The user approved it in chat and recorded its hash with `sdd.ts approve triage` at 2026-11-02T12:58:28.358Z after the Codex PreToolUse hook required a terminal-run approval.
- The active plan has one feature/commit for Jira KAN-149, with test-first list and detail stages. The Jira branch `feature/KAN-149-triage-suggestion-history` was created from current `main`; the harness records its hardcoded `feat/triage` branch, while the team's Jira branch rule controls the actual work.
- `sdd.ts status triage planned` then `implementing`, and `sdd.ts feature triage suggestion-history pending` then `in-progress`, completed successfully. Code implementation and verification are in progress.

### Blockers
None for the approved triage frontend slice. Its real backend history/detail endpoints remain OpenAPI proposals.

### Next up
Implement contract, MSW, list/filter and detail popup with AC tests; run trace, full frontend verification and browser check. Report changed files, checks and proposed commit before committing.

## Session 2026-11-03

### Working on: suggestion-history
- Added the proposed OpenAPI list/detail contract, generated client, role- and Project-scoped MSW data/handlers, URL filters, paged list and read-only detail popup. Ticket sources remain counts, unavailable Wiki sources have MSG110 with no link, and Jira links require HTTPS.
- Test-first UI work: the placeholder failed AC-001.1 before implementation. The targeted triage run passed 34/34 tests across page, mock API and date parsing. The mock UUID validator bug is recorded and fixed as BUG-093; the regression test names that bug.
- `bun harnessclaude/hooks/sdd.ts trace triage`: 20/20 ACs have tests. Removed the planned triage folder from fe-foundation's old placeholder ownership path to disambiguate trace.
- `npm run format:check`: pass. `npm run lint`: pass with one existing warning in `features/tickets/components/TicketListFilters.tsx`. `npm run typecheck`: pass. `npm run build`: pass. Two `npm run generate:api` runs produced identical SHA-256 manifests (`018AFD70210C06B78E7007A309DB9D8A1D0EFE86CC5818403C307A0F999A38BC`).
- Browser CDP check at 1366 x 768 passed the list, full suggestion detail, no-suggestion detail and `answer=NONE` plus paired date filter. Four temporary captures were visually inspected and removed after the check. The FormModal kit visibly displays both Cancel and Close actions; the Close action was exercised by AC-002.6.
- The default full `npm run test` run showed failures in ReviewTicketDetail and UserStatus under high worker load; a 2-worker full run later showed Chatbot failures and was stopped because it was taking over ten minutes. Selected ReviewTicketDetail and Chatbot tests passed in isolation. `UserStatusPage.test.tsx` AC-009.1/6/7/8 still timed out at its 15-second test limit in isolation, and passed with `--testTimeout=60000`; recorded as open BUG-094. The required default full suite is therefore not passing on this workstation.
- No code or harness commit was made. The feature remains `in-progress` until the full verification gate and user review are resolved.

### Next up
Review the KAN-149 diff and test evidence with the user. Resolve the unrelated full-suite timing failure under its own issue or obtain an explicit process decision, then commit one KAN-149 code feature and its harness record. Dashboard code remains locked until its updated spec approval is recorded by the user-run `sdd.ts approve dashboard-audit` command.

## Session 2026-11-03 — commit handoff

### Working on: suggestion-history
- The user explicitly asked to fix only Trần Hải Anh's bugs and commit KAN-149 without changing other owners' bugs. BUG-093 is fixed and staged in the code commit; BUG-094 remains logged but unstaged and untouched in User Status code/tests.
- Fresh pre-commit check: 34/34 targeted Triage tests passed; `sdd.ts trace triage` remained 20/20; staged diff had no whitespace errors and the staged content secret-pattern scan found no hits. The hook blocked an additional sensitive-filename regex scan because the command text itself contained `.env`; no sensitive file appeared in the staged filename list, which was inspected directly. The blocked scan was not retried.
- Code commit: `default@4440072cedbf7081edd6274ae82b89dcd67e221e` (`feat(triage): add suggestion history and detail (KAN-149)`). This includes only KAN-149 FE/OpenAPI/generated code and the BUG-093 row, with no Dashboard or BUG-094 changes.
- `sdd.ts commit triage default` recorded the SHA. `sdd.ts feature triage suggestion-history evaluating` moved the feature to evaluation. The default full FE test command still has the documented unrelated BUG-094 timing failure; the user's explicit direction is the exception for this commit.

### Next up
Commit the triage HarnessClaude records separately, then begin KAN-151 on its own Jira branch. No push or merge was requested.

## Session 2026-11-04

### Working on: suggestion-history-contract (Task 2)
- The user ruled that KAN-149 is done only with FE and BE. The branch was rebased on `main` 626ce59 (conflicts in `docs/bug-log.md` and `events.jsonl` kept both sides).
- Spec change approved twice (`9e1ada09`, then `24d4a988`): STORY-003/004 add the backend list/detail; the issue summary is dropped and the Project is shown by its Jira project key; `sources_used` shape and the `WikiDocumentAccess` port are defined. A triage-only int64 id was rejected because the session `ProjectMembership.projectId` is UUID; ids follow the team's shared type, which the team still has to decide (DB tables on `main` are BIGINT, `backend/AGENTS.md` says UUID).
- Design: master "Backend history API" section and rows 19–24; tasks 2–3 added; features `suggestion-history-contract` and `suggestion-history-api` registered.
- Task 2 test-first: AC-001.7, AC-001.12, AC-002.1 and AC-002.6 tests failed on the old shape, then passed after the OpenAPI `SuggestionProject` schema, nullable `jiraIssueUrl`, mock data and list/popup changes. Triage run: 26 passed, 8 skipped; `history-filters.test.ts` once hit a 10 s setup-hook timeout under load and passed 8/8 alone.
- `format:check`, `lint` (1 existing warning), `typecheck`, `build` pass; `generate:api` re-run leaves no diff; `trace triage STORY-001 STORY-002` passes; STORY-003/004 have no tests yet (Task 3).

- BUG-104: the full suite showed `route-table.test.tsx` `AC-005.3 shows the placeholder of 10.1 Triage History` failing since Task 1 built the screen. Fixed under the user-confirmed quick change: removed the 10.1 row (the file's own rule; chatbot did the same for 12.1) and added `AC-001.1 renders 10.1 instead of the not-built placeholder (BUG-104)`.
- Full `npm run test` (default workers): 35 failures in 20 files of other features, all `findBy` or hook timeouts under load. Re-run of those files plus triage with `--maxWorkers=2`: 61 files, 1199 tests passed.

- Commits (user agreed 04/11): `default@6de048c` fix BUG-104 (quick change closed), `default@bef5072` Task 2 contract. `sdd.ts commit triage default` recorded bef5072; `suggestion-history-contract` moved to evaluating.

### Blockers
Task 3 (backend) waits for KAN-117 / PR #107 (users, roles, `project_membership`) on `main`.

### Next up
Evaluate `suggestion-history` and `suggestion-history-contract`. Task 3 after PR #107 merges; then push and open one KAN-149 PR with FE and BE.

## Session 2026-11-04 — evaluation attempt 1

- Independent review: `suggestion-history` pass (moved to passing); `suggestion-history-contract` fail on one CRITICAL — the AC-001.11 Workspace-exclusion assertion checked a removed `issueSummary` string and could not fail (moved to fixing).
- Fix `default@c2a80da`: assert the foreign issue key and suggestion are absent; AC-001.7 covers a null `jiraIssueUrl`; mock sorts by parsed time; the day-boundary test is renamed AC-001.3. BUG-105 logged (open, FE Lead): the shared `src/test/setup.ts` `beforeAll` hits the 10 s hook timeout under load. Triage + route-table: 71 passed, 8 skipped by BUG-105; `history-filters.test.ts` alone 8/8; trace 20/20.
- Open for the FE Lead: the Project filter versus guideline 03 §5 (decision-log entry). Feature back to evaluating for attempt 2.

## Session 2026-11-04 — evaluation attempt 2

- Independent review attempt 2 of `suggestion-history-contract`: pass. The AC-001.11 assertions now fail if the foreign row leaks. Re-checked here: verdict pass in the review frontmatter; triage mocks + hooks 16/16. Feature moved to passing.
- Open: BUG-105 (FE Lead); Project filter vs guideline 03 §5 (FE Lead decision log); `openapi/tsolve-api.yaml` tag text still says "frontend proposal". Task 3 (`suggestion-history-api`) waits for PR #107.

## Session 2026-11-04 — Task 3 backend (suggestion-history-api)

- User: build the backend now, only own tasks and bugs. Neither `main` nor PR #107 has `app_user`/`project_membership`, and `workspace` has no `jira_site_url`; user chose triage ports with fail-closed stand-ins (`ManagedProjects`/`NoManagedProjects`, `JiraSite`/`NoJiraSite`, `WikiDocumentAccess`/`NoWikiDocumentAccess`), like `ticket`'s `StubProjectAccess`. Design and Task 3 updated.
- Added `vn.tsolve.triage`: `SuggestionResultController` (`GET /api/v1/suggestion-results` with `hasRole('PROJECT_MANAGER')`, `GET /{issueKey}` with `isAuthenticated()`), `SuggestionResultService(Impl)`, read-only `@Immutable` `SuggestionResultRecord` on `suggestion_result`, Specification search, `SourcesUsed` tolerant JSON parsing, MapStruct `SuggestionResultMapper` (200-code-point excerpt, whitespace collapsed), `InvalidHistoryFilterException` (MSG06/MSG39) and `ProjectNotManagedException` (MSG08). No changeset, no edit outside triage; OpenAPI tag text updated, regenerate leaves no diff.
- Tests: `SuggestionResultServiceImplTest` 13/13 (caught a real `\s` regex escape bug before commit) and `SuggestionResultControllerTest` 6/6; `SuggestionResultRecordRepositoryIT` written for the filters, Vietnam day bounds, order and detail scope — not run here (no Docker).
- `SERVER_PORT=8080 mvn verify`: surefire 425/425 pass; failsafe 3 ITs error with "Could not find a valid Docker environment" (two existing ITs and the new one); `mvn spotless:check` pass. Without `SERVER_PORT`, `JiraAppSecurityConfigTest` and `JiraAppEventControllerTest` (tracker) fail on the unresolved `${SERVER_PORT}` — local environment, not triage.
- `sdd.ts trace triage` cannot see Java tests on `main` (its globs miss `*Test.java`); the fix is on the KAN-117 branch. Test names cite `_AC_003_x` / `_AC_004_x`.
- Commit (user agreed 04/11): `default@9970e7e`. `sdd.ts commit triage default` recorded it; `suggestion-history-api` moved to evaluating (the first `feature` call exited with a Bun crash banner and changed nothing; the retry succeeded).

## Session 2026-11-04 — Task 3 evaluation attempt 1

- Independent review (with security review): `suggestion-history-api` pass; moved to passing. Concerns recorded in the review: AC-003.1 relies on identity providing `ManagedProjects` (fail-closed until then); 401 has no MSG07 body from the shared security chain (not triage-owned); `trace` cannot see Java tests on `main`; the IT needs Docker in CI.
- BUG-106 (own, Low): an extreme ISO date gave 500. Test-first fix: `rejectsADateOutsideFourDigitYearsWithMsg39_AC_003_4_BUG_106` failed with a `DateTimeException`, then passed once the filter takes only `yyyy-MM-dd`. Triage tests 20/20; Spotless pass. Not committed yet.
- Commit (user agreed 04/11): `default@ad30534` fix BUG-106; recorded with `sdd.ts commit`.
- Spec text synced with the ports decision (no criterion changed); user re-approved `cf15ceed`.
