---
module: platform
feature: web-tenant-isolation
task: 10b
verdict: pass
attempt: 1
date: 2026-11-27
reviewer: tsolve-code-reviewer
iteration: 1
currentIteration: 1
---

# Review: platform — Task 10b: tenant isolation through the web resolver (PR #362, KAN-460)

## Summary
Reviewed `git diff origin/main...HEAD` at `c17ca2b4` (2 new test files, +290, no production file) against platform
`spec.md` AC-002.18, AC-002.2, AC-002.3, AC-005.8, iteration-1 `tasks.md` Task 10b, design edge rows #1 and #90, and
the QA test plan `KAN-460-task-10b-qa.md`, with the card decisions Q1 (routing runner context, not the whole app) and
Q2 (the `RANDOM_PORT` Tomcat test in this task).

Both tests go through production code end to end: `TenantIsolationIT` sends `Host` → `ForwardedHeaderFilter` → the
context's real `TenantResolverFilter` (real `HostClassifier`, real `WorkspaceCatalogImpl` on a Registry database) →
a probe with no `runIn` → `TenantAwareJpaTransactionManager` / `TenantRoutingDataSource` → the Workspace's pool. They
are not vacuous: a resolver that read `X-Forwarded-Host`, `Forwarded`, `getServerName()` or a workspace id parameter
would bind B and the `["probe","A"]` + A-database assertion would fail; an unbound request fails on the no-fallback
rule (not 200); a misrouted connection sees B's row, nothing (row guard) or the empty unrouted DB — each fails. The
Tomcat IT's MSG06 JSON body can only come from the resolver (no error MVC or exception handler in that context), so a
pass-through would fail too. 5/5 pass locally; CI backend jobs are still pending. No production leak found.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1: AC-002.18, edge #1 (AC-002.3) and edge #90 (AC-005.8) each checked by a test that would fail on a leak |
| Completeness | pass | No MISSING. The two Task 10a review follow-ups (XFH case, real-Tomcat paths) are both done. No production code (card) |
| Correctness | pass | 5/5 targeted ITs + Spotless, exit 0, on `c17ca2b4`; no CRITICAL finding. CI backend pending at review time |
| Integration | pass | Reuses `RoutingRunner`, `TwoWorkspaceDatabases`, `AcceptingKnownWorkspaces`; no new fixture; both ITs land in an `it-other-*` CI part with no `ci.yml` change |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-002.18 two Workspaces, own DB, a row each in the same tenant table; through the web resolver A reads only A's row by JPA and by native SQL, never B's | PASS | `aSiteRequestReadsOnlyItsRow_byJpa_AC_002_18`, `…_byNativeQuery_AC_002_18`; A then B; `containsInAnyOrder("probe", note)` (exact size, `TenantIsolationIT.java:141`) + `current_database()` equals that Workspace's DB; `TenantRoutingDataSource` asserted as primary (`:121`) |
| 2 | AC-002.2 site host binds that Workspace for the request | PASS (through real routing) | `<site>.tsolve.test` from `TSOLVE_SITE_ORIGIN_TEMPLATE`; holder empty after each request (`:143`) |
| 3 | AC-002.3 / edge #1 `Host` only; `X-Forwarded-Host`, `Forwarded`, header / parameter ids ignored | PASS | `aSiteHostNamingTheOtherWorkspaceReadsOnlyItsRow_edge_1_AC_002_3`, both probe paths; `serverName` = B's host proves the forwarded host took effect while the resolver still bound A; `framework` strategy pinned from `application.yml` (`:79`) |
| 4 | AC-005.8 / edge #90 Jira App path on a site host, encoded or with a path parameter → 404 `MSG06` under real Tomcat decoding | PASS | `TenantResolverTomcatIT` `/api/v1/jira%2Dapp/x`, `/api/v1/jira-app;x/y` over a raw socket; status line, JSON body, `code` = `MSG06` (`:51-59`) |

### Stage 2 — quality

CRITICAL (blocks pass):
No issues found.

Informational (does not block):
- [TenantIsolationIT.java:132] The chain builds its own `new ForwardedHeaderFilter()` rather than the one Boot
  registers under `framework`; equivalence rests on the property check at `:79`. If production ever adds a
  `ForwardedHeaderFilterCustomizer` that matters for `Host`, this test would not see it.
  Fix: none now; the later full-application IT on production routing covers the real registration.
- [TenantIsolationIT.java:55,64,76] Each test calls `runner().run(...)`, so the routing context starts three times
  (26 s for the class) where the QA plan said one run holds all three. Within the CI part budget.
  Fix: optional — one `run` in a shared helper, or accept as is.
- [TenantIsolationIT] Spring Security and identity's `WebSessionFilter` are not in front of the probe (decision Q1),
  so a rebinding further down the real chain is not covered here. Order before Security is covered by
  `TenantFilterOrderTest`. Fix: the planned whole-application IT (later task).

## Security Review — platform (Task 10b) — 2026-11-27

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 0 |
| STRIDE | 0 | 0 | 0 | 0 |

- Secrets: the diff holds no credential; DB passwords and the catalog key come from `TwoWorkspaceDatabases` (random
  per run). CI `gitleaks` passed. Dependencies: no `pom.xml` change, no audit needed.
- STRIDE (Spoofing / Elevation of privilege across Workspaces): the change is the evidence for GBR-37 / TS-011 — a
  forged `X-Forwarded-Host`, `Forwarded`, `?workspaceId` or `X-Workspace-Id` on A's host never reaches B's data, and
  an encoded Jira App path on a site host is refused before any chain. Production read for leaks:
  `TenantResolverFilter.java` reads `request.getHeader("Host")` only, and `ForwardedHeaderFilter` does not rewrite
  `Host`; none found.

## Assumptions made
- `isolation_probes` is a test-only table, not a production tenant table. It counts as "the same tenant table" of
  AC-002.18 because the tenant changelog's runAlways installer puts the production row policy on it
  (`TenantGuardDatabase.addProbeTable`), as in `RowGuardIT` and `TenantRoutingIT`.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace platform STORY-002` → `25/25 acceptance criteria have a test`; AC-002.18 ✓
`TenantIsolationIT.java`; AC-002.3 ✓ also `TenantIsolationIT.java`. AC-005.8 (STORY-005) is additionally named by
`TenantResolverTomcatIT`.

## Issues
No blocking issue. Informational items in Stage 2.

## Test Report
See `task-10b-web-tenant-isolation-test-report.md`. Command: `mvn -q -B spotless:check verify -Dtest=NoUnitTest
-Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TenantIsolationIT,TenantResolverTomcatIT` → exit 0;
`TenantIsolationIT` tests=3 failures=0 errors=0, `TenantResolverTomcatIT` tests=2 failures=0 errors=0.
`gh pr checks 362`: backend jobs pending, the rest pass or skip.

Proposed status (not changed; the card does not ask): `web-tenant-isolation` → `passing` once CI on PR #362 is green
(it is `pending` in `progress.json`, so it goes through `implementing` → `evaluating` first).
