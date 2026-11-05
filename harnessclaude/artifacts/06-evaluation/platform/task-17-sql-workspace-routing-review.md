---
module: platform
feature: sql-workspace-routing
task: 17
date: 2026-11-03
verdict: pass
attempt: 1
reviewer: Codex (inline review from spec, code and fresh verification evidence)
iteration: 1
---

# Review: SQL Workspace foundation for KAN-107

## Summary

Scope: SQL-P1–P4 and the approved staged-delivery amendment. This does not pass the entire platform module,
the User List implementation or real sign-in. Code commit: `8e2c3ab8f1841db03148f52322d7f1b37b61b724`.

## Criteria

| Criterion | Result | Evidence |
|-----------|--------|----------|
| Functionality | PASS | Scoped SQL routing and real PostgreSQL behavior against approved ACs below |
| Completeness | PASS for SQL-P1–P4 | All 41 selected ACs have reviewed tests; unported modules and later User List slice remain explicit |
| Correctness | PASS | Full backend/frontend verification and regression tests |
| Integration | PASS | Qualified SQL resources preserve the old JPA path; original schema files preserved |

## Traceability

`trace platform (STORY-001, STORY-002): 41/41 acceptance criteria have a test` (exit 0).

## Stage 1 — Spec compliance

Fresh official trace: 41/41. The assertions and production paths were read, rather than treating AC names
as proof. Detailed behavioral evidence is in the adjacent task-17 test report.

| Criterion | Result | Evidence |
|-----------|--------|----------|
| AC-001.15 | PASS | Catalog SQL copy/bootstrap and exact catalog table set |
| AC-001.16 | PASS | workspace_email FK/lower-case schema, owner bootstrap |
| AC-001.17 | PASS | Catalog contains only catalog and Liquibase tables |
| AC-001.18 | PASS | Independent catalog/tenant XML; legacy entry point unchanged |
| AC-001.19 | PASS | Empty catalog has no Workspace rows |
| AC-001.20 | PASS | Supplied 23-table SQL applied unchanged; app_user projection matches |
| AC-001.21 | PASS | Legacy DB separate; metadata tables excluded from table-set comparison |
| AC-001.22 | PASS | Real bootstrap/constraints and no legacy/demo tables in SQL DBs |
| AC-001.23 | PASS | Generated BIGSERIAL ids mapped as Long; no UUID replacement |
| AC-001.24 | PASS | Only password reference stored; server-side resolution, sanitized projections/errors |
| AC-001.25 | PASS | Missing/blank secret cannot create a pool |
| AC-001.26 | PASS | Real non-owner runtime connection denial across tenant/catalog/legacy DBs |
| AC-001.27 | PASS | Schema and privilege ITs run on PostgreSQL with owner bootstrap |
| AC-002.1 | PASS | Public Long-based context contract |
| AC-002.2 | PASS | Actual Host resolves and binds through real registered filter |
| AC-002.3 | PASS | Forwarding headers and Workspace selectors ignored |
| AC-002.4 | PASS | Invalid label rejected before catalog lookup |
| AC-002.5 | PASS | Unknown site rejected before handler |
| AC-002.6 | PASS | Reserved/foreign/multiple-label hosts rejected |
| AC-002.7 | PASS | Live suspension blocks next request even with cached pool |
| AC-002.8 | PASS | Anonymous unknown host gets resolver 404 before security 401 |
| AC-002.9 | PASS | Sanitized application/problem+json response with correct code |
| AC-002.10 | PASS (staged scope) | No central/reserved host binding on migrated user paths; other routes unchanged |
| AC-002.11 | PASS | finally clears/restores binding; reused thread and exceptions tested |
| AC-002.12 | PASS | Same/nested binding restored after action |
| AC-002.13 | PASS | Cross-Workspace nested binding refused before action |
| AC-002.14 | PASS | Unknown id cannot run an action |
| AC-002.15 | PASS | Internal scope checks existence, not Active status; web policy checks suspension separately |
| AC-002.16 | PASS | Missing binding throws coded 500 exception and logs call site |
| AC-002.17 | PASS | localhost suffix and ports tested |
| AC-002.18 | PASS | Same account id returns distinct database rows through real HTTP resolver |
| AC-002.19 | PASS (staged scope) | Resolver bypasses health/docs and other unported paths without binding |
| AC-002.20 | PASS | Qualified SQL routing; no legacy default fallback |
| AC-002.21 | PASS | No binding means no tenant connection; caller credentials refused |
| AC-002.22 | PASS | Lazy pool creation and configured per-Workspace connection cap |
| AC-002.23 | PASS | Transaction binding cannot switch Workspace |
| AC-002.24 | PASS | Resource holder preserves Boot's legacy JPA/DataSource auto-configuration |
| AC-002.25 | PASS | Feature switch and scoped before-security filter; disabled 503 |
| AC-002.26 | PASS | Pool-count bound, concurrent single creation, shutdown, sanitized failures |
| AC-002.27 | PASS | Dedicated catalog/runtime users and CONNECT permissions |
| AC-002.28 | PASS | New owner bootstrap, original SQL/PDF hashes retained, no live conversion |

## Stage 2 — Quality and security

CRITICAL: none remaining in the staged feature. BUG-104/105/106 were reproduced before fixing and the
original regression tests pass in the final full backend run.

- Trust boundary: only actual Host binds a Workspace; no Workspace parameter/header selects a connection.
- Parameterized catalog queries; fixed identifiers; ambiguous case-insensitive names fail closed.
- ThreadLocal binding has lexical cleanup; synchronized pool registry prevents duplicate pools/cap races.
- SQL resources are qualified and never substitute for Boot's default resources. Actual old JPA/import tests pass.
- Driver exceptions and connection projections do not expose credentials; production/test secret scans passed.
- No new production REST controller, password flow, token issuer or schema mutation endpoint was introduced.
- Feature switch defaults off; bootstrap and least-privilege grants require explicit operations setup.
- No dependency was added. Java dependency vulnerability auditing is not configured here; this is not a
  blanket dependency/security certification. The optional tracked-env filename audit was privacy-blocked,
  was not retried, and did not expose values.

Informational: the known TicketListFilters fast-refresh lint warning and jsdom pseudo-element warning remain
outside this change. The unported OIDC UUID draft and other legacy modules still require their own migration.

## Fresh validation

- Backend: Maven verify successful, 268 unit tests (one existing disabled), 16 integration tests; Spotless pass.
- Frontend: format/lint/typecheck/build pass; full bounded test run 1269/1269 in 66 files; no timeouts increased.
- API generation: second invocation produces the same generated users client hash.
- Official trace: 41/41; Java trace fix regression tests: four passed, zero failed.
- Git whitespace check clean. SQL/PDF source hashes and code graph update recorded in the test report.

## Issues

None remaining in this feature. Existing warnings and deployment/migration boundaries are listed above.

## Test Report

See `artifacts/06-evaluation/platform/task-17-sql-workspace-routing-test-report.md` for commands, failures before
fixing BUG-104/105/106, final counts and source-preservation hashes.

## Checkpoint

The user agreed to commit the foundation. Code is committed as `8e2c3ab`; record the separate harness commit,
then continue `identity/user-list-backend`. No push is authorized.

## Superseded integration scope — 2026-11-05
User selected the current main UUID architecture for PR119. The earlier numeric SQL checkpoint above remains historical evidence only. The parallel catalog/router/bootstrap is retired from this PR. Current work is identity Task23 user-list-uuid-read, approved AC-004.9–.19; do not infer deployment readiness or current test results from this report. Original commits remain in history.
