---
module: triage
feature: suggestion-history-api
task: 3
verdict: pass
attempt: 1
date: 2026-11-04
reviewer: harnessclaude-reviewer
iteration: 1  # progress.json currentIteration: 1
---

# Review: triage — Task 3: Backend history and detail API

## Summary
Attempt 1 reviews `e9fee1b..9970e7e` in repo `default`; the code commit is `9970e7e`. The commit adds `vn.tsolve.triage` with five parts:
- `GET /api/v1/suggestion-results` and `GET /api/v1/suggestion-results/{issueKey}`.
- An `@Immutable` read-only mapping of `suggestion_result`.
- A JPA `Specification` query.
- A MapStruct mapper.
- Three ports, each with a fail-closed stand-in: `ManagedProjects`, `JiraSite` and `WikiDocumentAccess`.

No tracker file, changeset, `pom.xml` or `common/` file changed. In `frontend/openapi/tsolve-api.yaml` only the tag description changed.

The reviewer ran `SERVER_PORT=8080 mvn verify`. Surefire passes 425/425, including 19/19 triage tests, and `spotless:check` passes. The 3 Failsafe ITs error with "Could not find a valid Docker environment": the new `SuggestionResultRecordRepositoryIT` and 2 pre-existing ones. That is an environment error, not a failing test. Every STORY-003/004 criterion has a test named with its id. `sdd.ts trace` reports 0/10 only because of a tool limitation (see Traceability).

The verdict is **pass**. There is no CRITICAL finding, no Critical/High security finding, no failing triage test and no AC without a test. There are two open dependencies on code outside triage: the managed-Project source (AC-003.1) and the 401 body (AC-003.3). Both are recorded below as judgment calls.

**AC-003.1 and the stand-ins: a pass with a documented dependency, not a gap.**
1. The rule is enforced by construction. Both endpoints get their Project ids only from `ManagedProjects.of(caller)`: `SuggestionResultServiceImpl.java:84-98` (list) and `:114-119` (detail). An empty set returns an empty page or a 404 before any query (`:93-95`, `:115-116`). `totalElements` comes from the same `Specification`, so the counts and totals are scoped too. No code path reads `suggestion_result` without the caller's Project set.
2. The data source does not exist yet. A grep of `backend/src/main` and the changelog finds no `project_membership` and no `jira_site_url`. The spec puts the `project_membership` tables out of scope (spec.md:81) and lists them under "Required from other modules" (spec.md:107). Building them in triage would break module ownership (`backend/AGENTS.md` §1).
3. The stand-in fails in the safe direction. `NoManagedProjects` answers "no Project", so nobody sees a row; if it were wrong the other way, everybody would. The stand-in follows the team's `ticket` `ProjectAccess`/`StubProjectAccess` pattern and the user's 04/11 decision recorded in design.md:177-192.
4. The behaviour is proven at the port seam by unit tests with a mocked port (`SuggestionResultServiceImplTest.java:133`, `:154`, `:176`, `:286`). The query is proven in the Docker-only IT (`SuggestionResultRecordRepositoryIT.java:110`, `:131`).

The pass has these caveats:
- spec.md:107 still says "the backend code waits for it on `main`". No criterion states the fail-closed default for managed Projects the way AC-004.3 does for Wiki (issue 2).
- Until identity provides the real bean, every Project Manager gets an empty list and every detail is 404 against the real backend. The feature is not usable end to end, and the DoD browser check cannot show real rows (issue 3).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All 10 STORY-003/004 criteria are implemented (Stage 1). The scope comes first, filters combine with AND (`SuggestionResultRecordRepository.java:25-39`), and the Vietnam-day half-open range is `[from 00:00, to+1 00:00)` in Asia/Ho_Chi_Minh (`SuggestionResultServiceImpl.java:179-200`). The excerpt is at most 200 code points with whitespace collapsed. `sources_used` parsing is tolerant, and the Jira link is HTTPS only |
| Completeness | pass | No criterion is MISSING. There is one EXTRA: `sort` other than `createdAt,desc` gives 400/MSG06 (`:163-165`). The Interface Contracts' "malformed filters return … MSG06" covers it. AC-003.3's MSG07 body depends on the shared security chain (issue 1) |
| Correctness | pass | 19/19 triage unit tests pass, and the whole backend Surefire suite passes 425/425. Spotless passes. The IT cannot run without Docker, so the DB-level proof for AC-003.1/003.2/004.2 is unverified on this machine. The reviewer read the entity against changeset 002 and found that it matches. There is no CRITICAL finding |
| Integration | pass | The response matches `tsolve-api.yaml` (`SuggestionResultPage` is `PagedModel` `{content, page}`; field names and nullability match; Jackson emits `isAvailable`). The paths are same-origin, with no Workspace parameter. The FE generated client calls `/api/v1/suggestion-results` directly unless `VITE_API_MOCKING=true` (`frontend/src/main.tsx:15`). Tracker `SuggestionResult` is untouched |

### Stage 1: spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 1 | AC-003.1 scope to managed Projects, newest first, page 0 / size 20 | PASS (documented dependency) | Service `:80-98`, `ORDER` `:48` (`createdAt desc, jiraIssueKey asc`), defaults `:158-159`. Tests: `ServiceImplTest:133` (Project set, page 0, size 20, sort), `:154` (no Project means no query), `ControllerTest:49`, `RepositoryIT:110` (Docker) |
| 2 | AC-003.2 AND; `NONE` = null; inclusive Vietnam days | PASS | `Repository.java:28-38`; `Service:197-199`. `ServiceImplTest:162` checks `2026-11-01..02` → `[2026-10-31T17:00Z, 2026-11-02T17:00Z)`. `RepositoryIT:116` checks `NONE`/`NO` and the 16:59:59.999Z / 17:00Z boundary (Docker) |
| 3 | AC-003.3 401/MSG07; non-PM or unmanaged `projectId` 403/MSG08 | PASS (401 body depends on the shared chain, issue 1) | `@PreAuthorize(hasRole('PROJECT_MANAGER'))` at `Controller:36`. `ProjectNotManagedException` (403/MSG08) at `Service:86-91`. `GlobalExceptionHandler:90-96` maps the denial to 403/MSG08. Tests: `ControllerTest:74` (401 status only), `:81`, `:90`; `ServiceImplTest:176` (forged id, no repository call) |
| 4 | AC-003.4 400/MSG06 for answer/page/size; 400/MSG39 for dates | PASS | `Service:157-174`, `:179-196`; a non-integer `page` gets Spring's type-mismatch 400 with code MSG06 (`GlobalExceptionHandler:163-170`). Tests: `ServiceImplTest:188`, `:203` (partial, `2026-02-30`, `01/11/2026`, reversed), `ControllerTest:100` (`page=first` → MSG06) |
| 5 | AC-003.5 row fields, counts, excerpt ≤ 200, null when none | PASS | Mapper `:18-31`, `excerpt` `:51-61`; `SourcesUsed.java:24-49`. Tests: `ServiceImplTest:219` (URL, counts 2/2, answer, 200 chars, whitespace collapsed), `:239` |
| 6 | AC-003.6 no `jira_site_url` → `jiraIssueUrl` null, row kept | PASS | `Service:149-155`, `NoJiraSite`. Test `ServiceImplTest:251` (empty and `http://`) |
| 7 | AC-004.1 detail adds full solution + `wikiSources`, no Ticket details | PASS | `Service:124-136`; `SuggestionResultDetail` has no Ticket field. Test `ServiceImplTest:266` |
| 8 | AC-004.2 404/MSG04 for missing, unmanaged or non-PM | PASS | `Controller:54-63` (`isAuthenticated()`), `Service:114-122` (a non-PM never reaches the repository). Tests: `ControllerTest:113`, `ServiceImplTest:286`, `RepositoryIT:131` (Docker) |
| 9 | AC-004.3 availability from the wiki port; default false | PASS | `NoWikiDocumentAccess`; `Service:127-130`. Test `ServiceImplTest:301` uses the real stand-ins |
| 10 | AC-004.4 skip unreadable `sources_used` items | PASS | `SourcesUsed.java:24-58` (unknown type, missing/object id, WIKI without title, non-object item, invalid JSON). Test `ServiceImplTest:320` |

Rows 19–24 of the design's Edge Cases table are all covered: 19 at `ServiceImplTest:176`, 20 at `ControllerTest:81`, 21 at `ServiceImplTest:286` and `RepositoryIT:131`, 22 at `ServiceImplTest:162` and `RepositoryIT:116`, 23 at `ServiceImplTest:320`, and 24 at `ServiceImplTest:251`.

### Stage 2: quality and `backend/AGENTS.md`

CRITICAL (blocks pass): none.

Conventions checked:
- **Rule 3** (service interface plus `impl`): met.
- **Rule 4** (entity to DTO through MapStruct `SuggestionResultMapper`): met; `unmappedTargetPolicy=ERROR` compiles.
- **Rule 7** (`@PreAuthorize` on both endpoints; SRS codes MSG04/06/08/39 only): met.
- **Rule 9** (no Lombok, no wildcard imports, no `System.out`; grep finds none): met.
- **§3** (`@Transactional(readOnly = true)` on reads; a `protected` no-arg constructor and no setters on the entity): met.
- **§5** (module exceptions extend `MessageCodeException`; no `@ExceptionHandler` in the module): met.
- **§7** (no new changeset): met.
- **NFR-4** (no Maven dependency; tracker entity and table unchanged): met.

There is no N+1 problem: the mapping is in memory per page, and `idx_suggestion_project (project_id, created_at)` serves the query.

### Security review: triage — 2026-11-04

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 1 |
| STRIDE | 0 | 0 | 1 | 1 |

- Secrets: the only hits in the diff are the IT's dummy properties (`test-secret`, `unused`). The only tracked env files are `.env.example` and `frontend/.env.example`.
- Dependencies: none added (`pom.xml` is not in the diff). The reviewer ran no Maven audit tool; none is installed, and none was installed.
- Spoofing: both endpoints need a session through the shared chain. The 401 has no MSG07 body (Medium, issue 1).
- Tampering: GET only. All values are bound as Criteria/JPQL parameters, and the issue key is path-encoded in the Jira URL (`Service:154`).
- Repudiation: read-only endpoints; the spec asks for no audit event (`AGENTS.md` §5 lists only state changes).
- Information disclosure:
  - Error bodies carry only codes.
  - Ticket sources appear only as counts.
  - A non-PM, an unmanaged Project and a missing row all get the same 404. A non-PM is answered before any database read, so timing does not reveal existence.
  - Wiki titles of unavailable documents are returned, as AC-004.1 requires (Low, issue 7).
- Denial of service: `size` ≤ 100 and `sort` is fixed. The reachable 500 on extreme dates (Low, issue 4) leaks nothing.
- Elevation of privilege:
  - IDOR is closed. A forged `projectId` gets 403 before any query, and the detail lookup is `jira_issue_key AND project_id IN managed`.
  - Workspace isolation is one database per Workspace (changeset 002 header), so no `workspace_id` predicate is needed.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace triage STORY-003 STORY-004` (exit 1):

```
  ✗ AC-003.1  no test under this module's paths (backend/src/main/java/vn/tsolve/triage/**, backend/src/test/java/vn/tsolve/triage/**, ...)
  ✗ AC-003.2 … ✗ AC-004.4  (same message)
trace triage (STORY-003, STORY-004): 0/10 acceptance criteria have a test
```

The tool cannot see backend tests, for two reasons:
- `sdd.ts:47` `TEST_GLOBS` has no `*Test.java` or `*IT.java` pattern.
- `lib/spec.ts:34` `AC_MENTION` matches only `AC-003.1`, not the backend convention `_AC_003_1`.

The reviewer read the test method names instead. All 10 criteria have at least one test, 23 test methods in total (map in the test report). Each test checks its criterion; for the 401 half of AC-003.3, see issue 1.

## Issues

None of these blocks the verdict.

1. **[Medium, dependency] 401 has no MSG07 body (AC-003.3, first clause).**
   - Where: `backend/src/main/java/vn/tsolve/identity/AuthSecurityConfig.java:59-60` (`HttpStatusEntryPoint(UNAUTHORIZED)`, empty body) and `common/security/DefaultSecurityConfig.java:23-24` (HTTP Basic). No backend code emits MSG07.
   - The test `SuggestionResultControllerTest.java:74-77` asserts only the status.
   - The user still sees MSG07: `frontend/src/app/providers/global-errors.ts:11` maps any 401 to the MSG07 dialog.
   - Triage cannot fix this without editing `identity/` or `common/`, which belong to other owners.
   - Fix: the shared chain's owner (be-foundation `roles-security` or KAN-117) writes a 401 `ProblemDetail` with `code: MSG07`. Then add `jsonPath("$.code").value("MSG07")` to `ControllerTest:75`. Alternatively, reword AC-003.3 and the Interface Contracts through a spec change, saying the shared chain answers 401 and the web app shows MSG07.
2. **[Spec sync] The spec does not record the ports decision.**
   - spec.md:107 says "the backend code waits for it on `main`". The design's 04/11 revision (design.md:177-192, a user decision) replaced that with `ManagedProjects`/`JiraSite` ports and fail-closed stand-ins.
   - No criterion states the managed-Project default the way AC-004.3 does for Wiki. The `JiraSite` default is covered by AC-003.6.
   - Fix (plan skill, Change Mode): update the identity and workspace lines of "Required from other modules", add a changelog row, and optionally add an AC-004.3-style clause: "WHILE identity provides no implementation, no Project is managed."
3. **[Info] Replacing the stand-ins.**
   - `service/impl/NoManagedProjects.java:12`, `NoJiraSite.java:8` and `NoWikiDocumentAccess.java:9` are unconditional `@Component`s. When identity, workspace or wiki adds its own bean, startup fails with two candidate beans unless the stand-in is deleted in the same change. The Javadoc says to delete it; the hand-off must say so too.
   - Unlike `ticket`'s `StubProjectAccess` (`tsolve.stub.projects`), the triage stand-in cannot be seeded. Against the real backend, every Project Manager sees MSG04 and every detail is 404. The DoD browser check with real data has to wait for KAN-117.
4. **[Low] Extreme ISO dates give 500 instead of 400/MSG39.**
   - Where: `SuggestionResultServiceImpl.java:197-199`. `last.plusDays(1)` is outside the `try`. `to=+999999999-12-31` throws `DateTimeException`; the reviewer checked this with a scratch run. The handler answers 500/MSG06.
   - Years beyond PostgreSQL's `timestamptz` range, such as `+300000-01-01`, pass parsing and reach the database.
   - Fix: convert inside the `try`, or reject years outside 1..9999 with MSG39. Add a case to `ServiceImplTest:203`.
   - If the user agrees this is a bug, it gets a `docs/bug-log.md` row and a test named with the bug id (`AGENTS.md` §10). The reviewer did not write the log.
5. **[Info] Role logic in the controller.**
   - `SuggestionResultController.java:60-62` derives `projectManager` from the authorities in the controller, and `:25-27` hard-code the role. `common/security/Roles.java` has no `PROJECT_MANAGER` constant (`AGENTS.md` §3 "no business logic", §5 "role names as constants").
   - Fix: when KAN-117 adds the role constant, reference it. Consider moving the role test into the service, or into a small shared helper, so the hard-coded name lives in one place.
6. **[Info, harness] `sdd.ts trace` cannot cover backend criteria.**
   - Where: `harnessclaude/hooks/sdd.ts:47` and `harnessclaude/hooks/lib/spec.ts:34` (see Traceability).
   - NFR-1 ("`sdd.ts trace triage` passes") cannot hold for STORY-003/004 until the harness also scans `**/*Test.java` and `**/*IT.java` and accepts `AC_003_1`. This is a harness change outside this feature.
7. **[Low, spec-conformant] Titles of unavailable Wiki documents reach the Project Manager.**
   - Where: `SuggestionResultServiceImpl.java:128-130`. While `NoWikiDocumentAccess` is the stand-in, the title of every WIKI item in `sources_used` is returned, with `isAvailable=false`.
   - AC-004.1 asks for exactly this, and AC-002.4 shows MSG110. Design.md:35 says only references "the viewer may be told about". This is a judgment call.
8. **[Info] The DB-level proof needs Docker.**
   - `SuggestionResultRecordRepositoryIT` must pass in CI with Docker before merge. It is the only proof of:
     - the order and tie-breaker (AC-003.1);
     - `IS NULL` for `NONE` and the 17:00Z Vietnam-day boundary in PostgreSQL (AC-003.2);
     - the scoped detail lookup (AC-004.2);
     - the JSONB-to-`String` read;
     - Hibernate `validate` of the second entity mapped on `suggestion_result`.
   - What the reviewer checked by reading:
     - The columns, types and nullability match changeset 002.
     - `reporter_answer` is constrained to `YES`/`NO`.
     - Hibernate 6.5.3 `AbstractJsonFormatMapper.fromString` returns the raw text for a `String` target (`javap`).

## Assumptions made

These are judgment calls the reviewer could not put to the user. Please confirm or correct each one.

1. **AC-003.1 with `NoManagedProjects` passes as a documented dependency (Summary).** The alternative is to hold the verdict until KAN-117 provides `project_membership`. The reviewer did not, because the spec itself lists it as required from identity and the user chose ports on 04/11.
2. **AC-003.3's 401 is judged on the status, with MSG07 owned by the shared security chain (issue 1).** If the user reads "THE backend SHALL return 401/MSG07" strictly, as a triage obligation, the verdict becomes fail. The fix would then be a spec change or a shared-chain change, since triage cannot fix it alone.
3. **The `sdd.ts trace` 0/10 result is a tool limitation, not a MISSING criterion.** Coverage was verified by reading the test names. NFR-1's "trace passes" is therefore unmet for the backend criteria, through no fault of this feature (issue 6).
4. **A Failsafe IT that cannot start without Docker is not a failing test, as the dispatcher said.** AC-003.1, AC-003.2 and AC-004.2 rely on it for DB-level proof, and the unit tests cover the same rules at the service seam. NFR-2's "`mvn verify` passes" is not shown on this machine.
5. **"Plain-text excerpt" (AC-003.5) means the stored text with whitespace collapsed, cut at 200 Unicode code points.** Markdown or HTML in `suggested_solution` is not stripped; the FE renders it as text (AC-001.12).
6. **Returning the titles of unavailable Wiki documents follows AC-004.1 and is accepted (issue 7).**
7. **Extreme-year dates giving 500/MSG06 is Low and non-blocking (issue 4).** "Not a calendar date" in AC-003.4's sense was read as "not a parseable ISO date".
8. **A `projectId` that is not a UUID gets 403/MSG08, not 400.** AC-003.4 does not list `projectId` format, and any value outside the managed set is "a Project they do not manage".

## Test Report

Full report: `harnessclaude/artifacts/06-evaluation/triage/task-3-suggestion-history-api-test-report.md`.

- `SERVER_PORT=8080 mvn -q test -Dtest='SuggestionResult*Test' -Dsurefire.failIfNoSpecifiedTests=false`: exit 0. `SuggestionResultControllerTest` passes 6/6 and `SuggestionResultServiceImplTest` 13/13, with 0 failures, 0 errors and 0 skipped.
- `SERVER_PORT=8080 mvn verify`: Surefire `Tests run: 425, Failures: 0, Errors: 0, Skipped: 0`. Failsafe `Tests run: 3, Failures: 0, Errors: 3`; all three are "Could not find a valid Docker environment", including `SuggestionResultRecordRepositoryIT`. BUILD FAILURE at `failsafe:verify` (environment).
- `mvn -q spotless:check`: exit 0.
- The known environment fact was confirmed. Without `SERVER_PORT`, `JiraAppEventControllerTest` fails to load its context on `${SERVER_PORT}`: 1 class, 6 methods. Tracker code is not in this diff.
- Coverage is not measured, because no plugin is configured and the spec sets no target. Frontend checks were not re-run; the only frontend change is the OpenAPI tag description.
