---
module: platform
feature: cross-module-isolation
task: 23
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
---

# Review: platform — Task 23: cross-module-isolation (STORY-015, AC-015.1 – AC-015.7)

## Summary
Commit 8f75599b adds two test files only (`CrossModuleIsolationIT`, `IsolationFixtures`); no production code changed. Each
AC is checked by value, not by name. Both Workspaces are seeded with the same ticket title, the same embedding vector and
BETA rows of every kind (Published, pending review), so a leak would fail the tests (see Leak sensitivity). Evidence from
the manager (not re-run): `CrossModuleIsolationIT` 13/13, `mvn verify` 496 tests 0 failures.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-015.1 – .7 each proven; see table below |
| Completeness | pass | 7/7 ACs traced; the `/api/v1/review-queue` path fix is recorded in the spec change log |
| Correctness | pass | Manager run green; test code read in full |
| Integration | pass | Real `WebSessionStore`, host binding, tenant routing, repositories; only the two AI clients are stubs (as AC-015.5 allows) |

### Stage 1 – spec compliance
| AC | Status | Evidence |
|----|--------|----------|
| 015.1 | PASS | `CrossModuleIsolationIT.java:99` ACME ticket id absent in BETA DB, readable through the ACME session; BETA marker absent |
| 015.2 | PASS | :110 `totalElements==1`, one id equal to ACME's; BETA published id, pending id and marker excluded. A BETA row with the same title would make the size 2 and fail |
| 015.3 | PASS | :122 / :130 same 404 and same `code` as a random UUID; BETA row counts of all tables (audit included) unchanged; ACME counts unchanged for feedback |
| 015.4 | PASS | :142 one result, ACME id; `search_log` +1 in ACME with user/query/result_count, BETA table counts unchanged |
| 015.5 | PASS | :166 captured chat messages contain the ACME marker, not BETA's; 1 citation, solution fields equal ACME text; stored answer in ACME, BETA unchanged |
| 015.6 | PASS | :194 PM session, `totalElements==1`, ACME pending id only; BETA pending excluded |
| 015.7 | PASS | :218 six endpoints, 401, both DB row counts equal, `verifyNoInteractions(embeddings, llm)`; CSRF supplied on writes so it tests the missing session, not CSRF |

## Traceability
```
  ✓ AC-015.1 … AC-015.7  backend/src/test/java/vn/tsolve/platform/isolation/CrossModuleIsolationIT.java
trace platform (STORY-015): 12/12 acceptance criteria have a test   (AC-015.8 – .12 are Task 24)
```

## Leak sensitivity (asked check)
- AC-015.2/.4/.6: BETA holds a Published ticket with the same title (`IsolationFixtures.TITLE`, `ticket()` at :74), a pending one,
  and the same embedding. If BETA rows were returned, `totalElements` would be 2 and the id/marker assertions fail. They cannot
  pass on a leak.
- Scope adapter (`IsolationFixtures.java:131`): replaces `NoVisibleScope` (fail-closed in production) by a test-only bean that
  reads `project` from the routed JDBC and validates through the real `ScopeAccessService`. It does not widen visibility beyond the
  ACME account's own memberships. A routing leak would still fail AC-015.4: the ACME resolver is not a member of any BETA
  project, and a search run against BETA's database would return nothing (expected 1). So the adapter does not mask the defect
  AC-015.4 targets. It does mean the test does not exercise the production visibility rule (see Issues, minor 1).

## Issues
Critical: none.

Important: none.

Minor:
1. `IsolationFixtures.java:131` — AC-015.4 runs with a test-invented `VisibleScope`; production beans give no scope (`NoVisibleScope`) or all (`DevAllVisibleScope`), so a real-scope integration is untested. The spec does not forbid the seam and the Javadoc says why. Fix: keep under "Assumptions made"; re-point the IT at the production scope when identity/project integration lands.
2. `CrossModuleIsolationIT.java:63` — two `@MockBean` plus `@Import(ScopeConfiguration)` build their own Spring context (backend/AGENTS.md section 8 asks for context reuse). One class, tolerable. Fix: move to a shared base if more isolation ITs follow.

AGENTS.md test rules: shared `TwoTenantDatabases` and Redis (no new container), names cite the AC id, cleanup in `@AfterEach`. Pass.

## Test Report
Not re-run (manager owns Docker/Maven): 13/13 and 496/0. `sdd.ts trace` run by me: 12/12. See `task-24-e2e-smoke-test-report.md`. Proposed status: `passing`.
