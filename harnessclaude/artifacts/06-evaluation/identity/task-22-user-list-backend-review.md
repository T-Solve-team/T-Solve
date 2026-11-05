---
module: identity
feature: user-list-backend
task: 22
verdict: pass
attempt: 2
date: 2026-11-03
reviewer: Codex (inline spec and quality review with fresh command evidence)
iteration: 1
---

# Review: identity — Task 22: user-list-backend

## Summary

Scope: KAN-107 SQL-U1–U5 and approved STORY-004 only. Implementation meets the selected
criteria. Full backend verification and the full frontend recheck pass. The first frontend
run's intermittent Review Queue failure remains separately logged as BUG-108.
This does not complete real OIDC/session issuance or backend account mutations/detail.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | SQL read projections, literal search, AND filters, live Admin access and numeric-string ids |
| Completeness | pass for selected slice | All 19 STORY-004 criteria have reviewed tests; separate backend actions are explicitly excluded |
| Correctness | pass | Full backend passes; final frontend run 1273/1273 in 67 files, exit 0; first-run BUG-108 remains recorded |
| Integration | pass for selected slice | Real catalog/two-tenant HTTP stack, separate legacy JPA, generated frontend contract and mock navigation |

## Stage 1 — Spec compliance

| AC | Result | Evidence |
|----|--------|----------|
| AC-004.1 | PASS | UserListPage workspace-only rows and handlers' workspace predicate |
| AC-004.2 | PASS | URL-backed search/role/status/numeric Department tests; repository AND filters |
| AC-004.3 | PASS | Page/default size and URL sort tests; stable numeric tie-break repository test |
| AC-004.4 | PASS | Existing loading-state UserListPage test retained |
| AC-004.5 | PASS | Existing error/retry UserListPage test retained |
| AC-004.6 | PASS | Empty result MSG04 test retained |
| AC-004.7 | PASS | Existing frontend route guard plus backend live Admin checks |
| AC-004.8 | PASS | Frontend preserves overlong input/current results and URL; no request submitted |
| AC-004.9 | PASS | Repository uses supplied app_user/user_role/department; UserListIT uses real routed PostgreSQL |
| AC-004.10 | PASS | Long.MAX_VALUE SQL row/parameter and exact string href; no JavaScript Number id conversion |
| AC-004.11 | PASS | Trim/literal wildcard search, role EXISTS/Employee-only and combined filter tests |
| AC-004.12 | PASS | No duplicated users, baseline Employee once, nullable Department/status and read-only response |
| AC-004.13 | PASS | Defaults/size bounds, PagedModel shape, count independent of pagination and id tie-break |
| AC-004.14 | PASS | Invalid query/overflow vectors, coded field metadata, empty missing Department and overflow page |
| AC-004.15 | PASS | Retained inactive Departments returned in name/id order with decimal-string ids |
| AC-004.16 | PASS | Both endpoints @PreAuthorize; missing/untrusted/foreign/anonymous, deleted/deactivated and stale-role cases |
| AC-004.17 | PASS | No Last sign-in in list DTO/table; all requests normalize old sort before results load |
| AC-004.18 | PASS | Real two-tenant same ids/separate counts; foreign session rejected and context empty after failures/success |
| AC-004.19 | PASS | Original/copy SQL hashes, account rows unchanged by GET; SQL/PDF originals retained and legacy changelogs untouched |

Internal integration refinements are recorded in the session log: optional legacy Department
metadata on creation-options, mock-only numeric aliases, reuse of canonical KAN-117 role definitions,
and public existing SQL test helpers. These implement the approved staged contract rather than
migrating unported write/detail modules or adding sign-in behavior.

## Traceability

```text
trace identity (STORY-004): 19/19 acceptance criteria have a test
```

The trace names are supported by reviewed assertions in UserListQueryTest, UserListAccessServiceTest,
UserListSessionFilterTest, UserListRepositoryIT, UserListIT, UserListSql.test.tsx and existing list tests.
Spec completeness check: 9 stories / 140 ACs, OK. Only STORY-004 is evaluated here.

## Stage 2 — Quality and security

CRITICAL in new feature code: none remaining. BUG-107 is reproduced by a regression and fixed:
untrusted sessions cannot open tenant connections before their binding is validated.

- Only the real resolved host chooses the Workspace. Typed server principal binding must match it;
  headers/query values and stale authorities cannot grant access. Both HTTP read paths are tested.
- Security filter builds a temporary context without mutating the session; service reloads access in
  the same read-only REPEATABLE_READ snapshot used for the response. No new credential issuer exists.
- SQL values use parameters. Dynamic identifiers/directions come only from fixed allow-lists;
  role queries are bounded by page size (maximum 100), with constant query count rather than N+1.
- Controller → service interface → repository layering is preserved. JDBC read DTOs and Long ids follow
  the explicit SQL amendment; legacy JPA auto-configuration and applied changelogs remain unchanged.
- No account write/audit mutation is added by these GETs. CSRF is retained; response errors use
  existing catalog codes and do not echo supplied identity or database credentials.
- Frontend keeps ids as strings, renders names/email as React text, and confines compatibility mapping
  to mocks. User Detail's Last sign-in display remains outside this list change.
- Gitleaks production identity/common-security scans: no leaks. Dangerous execution/HTML pattern search
  found no hits in the scoped code. No dependency manifest or lockfile changed.
- Existing BUG-069 is reproduced by full npm audit (19 moderate/two high development-toolchain findings).
  Production-only audit has zero vulnerabilities. This review does not certify the entire toolchain;
  its existing high findings still need a separate dependency change. Java vulnerability audit is not
  configured. The previously privacy-blocked env filename audit was not retried or bypassed.

Informational: feature remains disabled until provisioning/trusted session issuance is connected.
Existing TicketListFilters lint and jsdom pseudo-element warnings remain. BUG-108 is a separately
logged Review Queue test failure; no Review Queue code, assertions or timeouts changed here.

## Test Report

See `artifacts/06-evaluation/identity/task-22-user-list-backend-test-report.md` for fresh evidence,
source hashes, first-run failure and final frontend verification.

## Issues

No blocking issue remains in this feature. The no-concurrent-build/indexing frontend recheck
passes all 1273 tests in 67 files, exit 0. This does not close BUG-108 or BUG-069. The user
approved the commit: code is `b6b15970fb03ec8f979884af4c6ad4b40771a40c`, repository hooks passed,
and official feature tracking is passing. No push is authorized.

## Superseded integration scope — 2026-11-05
User selected the current main UUID architecture for PR119. The earlier numeric SQL checkpoint above remains historical evidence only. The parallel catalog/router/bootstrap is retired from this PR. Current work is identity Task23 user-list-uuid-read, approved AC-004.9–.19; do not infer deployment readiness or current test results from this report. Original commits remain in history.
