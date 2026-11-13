---
module: be-foundation
feature: me-session
task: 3
verdict: pass
attempt: 3
date: 2026-11-13
reviewer: tsolve-code-reviewer
iteration: 1
---

# Review: be-foundation — Task 3: me-session

## Summary
Third review of PR #217, the last fix commit only: `230a3d91` (2 files, +23 −3) on top of `e0cc8bbb`, judged against
findings 1 and 6 of attempt 2. Finding 1 is closed. The converter now takes the raw class from a `Class` or a Jackson
`JavaType`, and the document the full application serves at `/v3/api-docs` has `CurrentUser.department` as
`allOf[0].$ref = #/components/schemas/DepartmentRef` with `nullable: true`. The reviewer measured this on the full
application (`@SpringBootTest`), before and after the commit: the three keys of that one property are the only
difference in the whole document (83 schemas, 55 paths). The new `SessionMeIT` test fails on the old converter and
passes on the new one. Edge case 17 is met. Findings 7 and 8 stay open as informational notes by the user's decision,
and finding 6 stays open for the bookkeeping card. CI on `230a3d91` is green. Verdict: pass, with notes.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-003.1–003.5 unchanged by this commit (no change outside the converter and one test); the `/me` response body is as in attempt 2 |
| Completeness | pass | Edge case 17 met: `department` is nullable in the served document, which is the one `npm run api:pull` downloads |
| Correctness | pass | `CurrentUserSchemaTest` 2/2, `SessionMeIT` 4/4; the new test is red on `e0cc8bbb`'s converter and green on `230a3d91`; CI green |
| Integration | pass with notes | `CurrentUser` now matches the contract for `department` in the 3.0.1 form; the `401` body and the `200` media type still differ (findings 7, 8, open by decision) |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-003.1 – AC-003.5 | PASS | as attempt 2; not touched by `230a3d91` |
| 2 | AC-003.6 response matches `CurrentUser`; ids UUID strings | PASS | as attempt 2, plus `SessionMeIT.java:96-104` on the served schema |
| 3 | Edge 14, 15, 16, 28 | PASS | as attempt 1 |
| 4 | Edge 17 `department` nullable after `api:pull` | PASS | `CurrentUserNullableConverter.java:24, 42-47`; `SessionMeIT` `documentsDepartmentAsNullableInTheServedApiDocs_AC_003_6_edge17`; reviewer's before/after diff of `/v3/api-docs` (below) |

No EXTRA behaviour.

## Traceability

```
  ✓ AC-003.1  SessionControllerTest, SessionServiceImplTest, SessionMeIT
  ✓ AC-003.2  SessionControllerTest, SessionServiceImplTest
  ✓ AC-003.3  SessionServiceImplTest
  ✓ AC-003.4  SessionControllerTest, SessionServiceImplTest
  ✓ AC-003.5  SessionControllerTest, SessionMeIT
  ✓ AC-003.6  CurrentUserSchemaTest, SessionMeIT
  ! ids in tests but not in the spec: AC-001.72
trace be-foundation (STORY-003): 6/6 acceptance criteria have a test
```

## Earlier findings

| # | Finding | State | Evidence |
|---|---------|-------|----------|
| 1 | critical — `department` not nullable in the served schema | CLOSED | The three conditions of the card hold, each measured by the reviewer (next section) |
| 2 | informational — `departments` de-duplicated by id + name | CLOSED (attempt 2) | not touched |
| 3 | informational — `format: email`, enum `$ref` | CLOSED (attempt 2) | not touched; its 401 part is finding 7 |
| 4 | informational — no full-application test | CLOSED (attempt 2) | `SessionMeIT` now 4 tests |
| 5 | informational — `orElseThrow()` | CLOSED (attempt 1, by decision) | not touched |
| 6 | informational — process (`progress.json`, `Jira: KAN-?`) | OPEN | for the bookkeeping card |
| 7 | informational — `401` documented with a `CurrentUser` body | OPEN by decision | still served: `"401":{"description":"Unauthorized","content":{"*/*":{"schema":{"$ref":"#/components/schemas/CurrentUser"}}}}` |
| 8 | informational — `200` media type `*/*` | OPEN by decision | still served with `*/*` |

### Finding 1 — the three closing conditions

1. **Served document of the full application.** A scratch copy of `230a3d91` (outside the repo) with one added
   `@SpringBootTest` probe that writes the body of `GET /v3/api-docs` to a file gives, for OpenAPI `3.0.1`:
   `CurrentUser.properties.department = {"allOf":[{"$ref":"#/components/schemas/DepartmentRef"}],"nullable":true}`.
2. **No other schema touched.** The same probe on the same copy with the converter file put back to `e0cc8bbb`'s
   text (checked identical with `git show e0cc8bbb:…`) gives the "before" document. A key-by-key comparison of the
   two parsed documents reports three differences, all under
   `/components/schemas/CurrentUser/properties/department`: `$ref` removed, `allOf` added, `nullable` added. Nothing
   else differs in 83 schemas and 55 paths. By the code, too: the guard is `rawClass(type) == CurrentUserResponse.class`
   (line 24) and the only write is `CurrentUser.properties.department` (lines 25-35).
3. **A test fails on the old code.** On that copy with the old converter, `SessionMeIT` gives
   `Tests run: 4, Failures: 1` —
   `SessionMeIT.documentsDepartmentAsNullableInTheServedApiDocs_AC_003_6_edge17:100 No value at JSON path
   "$.components.schemas.CurrentUser.properties.department.nullable"`. On `230a3d91` in the worktree it gives 4/4.

## Issues

CRITICAL (blocks pass): none.

Informational (does not block):

7. **[informational, open by decision]**
   `backend/src/main/java/vn/tsolve/common/session/controller/SessionController.java:27-28` — the `401` is served with
   the `CurrentUser` schema; the contract has the Problem response. Fix as written in attempt 2.
8. **[informational, open by decision]** `SessionController.java:22-30` — the `200` is served with media type `*/*`;
   the contract has `application/json`. Fix: `produces = MediaType.APPLICATION_JSON_VALUE`.
6. **[informational, open]** process — `progress.json` `me-session` status and `tasks.md` Task 3 `Jira: KAN-?`. For
   the bookkeeping card.
9. **[informational, new]** `backend/src/test/java/vn/tsolve/common/session/SessionMeIT.java:100-103` — the test
   checks `nullable` and `allOf[0].$ref` but not that `allOf` has one item or that the plain `$ref` is gone. The
   converter writes exactly that today, so nothing is wrong; a later change that adds a second `allOf` item would
   pass. Fix (optional): add `jsonPath("…department.allOf.length()").value(1)`.

No new critical finding. The contract writes `department` in the 3.1 form (`anyOf [$ref, type: 'null']`) and the
application serves 3.0.1 (`allOf` + `nullable`); the card's decision names the 3.0.1 form as the closing condition.
`npm run api:pull` / `generate:api` were not run by the developer or the reviewer (frontend is out of this PR).

## Security Review — be-foundation Task 3 — 2026-11-13 (attempt 3, delta)

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 0 |
| STRIDE | 0 | 0 | 0 | 0 |

- `230a3d91` changes a schema-writing guard and adds one test. No change to authorisation, input handling, logging
  or the response body; no new dependency (`JavaType` is Jackson, already on the classpath). `/v3/api-docs` exposure
  is as before this commit.

## Test Report

Reviewer, at `230a3d91` (worktree clean except these two review files): `CurrentUserSchemaTest` 2/2, `SessionMeIT`
4/4, BUILD SUCCESS for both; trace 6/6. Developer's verify (card): `mvn spotless:check test` 1477 tests, 0 failures,
BUILD SUCCESS (3:42), run on `e0cc8bbb` plus the fix before it was committed; `git show 230a3d91` holds exactly the
two files the debugger's hand-off names and the worktree has no other change, so it is accepted. Full verify not
re-run. CI (`gh pr checks 217`, head `230a3d91`): backend, backend-tests (unit, it-keycloak-a, it-keycloak-b,
it-other), frontend, changes, gitleaks, pr-title all pass; frontend-checks and frontend-tests skipping; nothing
failing or pending. Details: `task-3-me-session-test-report.md`.

## Assumptions made

- "Pass with notes" is recorded as `verdict: pass` (the front matter allows only pass or fail); the notes are
  findings 6, 7, 8 and 9.
- The before/after probe ran in a scratch copy of the commit outside the repo (`git archive`), with one added probe
  class and, for the "before" run, the old converter text. No file in the worktree was changed.
- Proposed status: `passing`. Not changed by the reviewer (the card does not ask for it; finding 6 covers the
  status bookkeeping).
