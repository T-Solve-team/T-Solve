---
module: import-ticket-list
feature: project-access-errors
task: 5
verdict: pass
attempt: 1
date: 2026-10-22
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: import-ticket-list — Task 5: ProjectAccess stub and ProblemDetail errors

## Summary
`ProjectAccess` + `StubProjectAccess` fail closed (empty or unlisted user → manages nothing; inactive → not managed). `GlobalExceptionHandler` now renders ProblemDetail with `code`, `params` and `fieldErrors`; `MessageCodeException` carries status/code/params; `MaxUploadSizeExceededException` → 413 MSG28; `AccessDeniedException` → 403 MSG08. Multipart limits and the two stub variables are in `application.yml` / `.env.example` with empty defaults.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-001.4 mapping `GlobalExceptionHandler.java:30-33`; AC-001.10 `:36-39`; fail-closed stub `StubProjectAccess.java:36-38`. |
| Completeness | pass | All files in tasks.md changed; `.env.example` documents both names without values. |
| Correctness | pass | GlobalExceptionHandlerTest 6/6, StubProjectAccessTest 5/5. |
| Integration | pass | Existing `knowledge` errors (`NotFoundException`, `BusinessRuleException`, validation) keep working through the same handler. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list` → `trace import-ticket-list: 0/41 acceptance criteria have a test` (all ✗). Known harness gap: it scans only `*.test.*/*.spec.*/*_test.*` files and the `AC-001.1` spelling; JUnit methods here cite `_AC_001_1` (backend/AGENTS.md §2). Manual trace (grep of `backend/src/test` for `_AC_XXX_Y`, then reading each test): 41/41 ACs have at least one test that checks the criterion. This task's rows:

| AC | Test | Status |
|---|---|---|
| AC-001.4 (413 mapping) | GlobalExceptionHandlerTest.uploadOverTheLimitIsMsg28_AC_001_4 | PASS |
| AC-001.10 (403 mapping) | GlobalExceptionHandlerTest.missingRoleIsForbiddenWithMsg08_AC_001_10 | PASS |
| (risk review: fail closed) | StubProjectAccessTest (5 tests) | PASS |

## Issues
CRITICAL: none.

Informational:
1. [Low] `backend/src/main/java/vn/tsolve/ticket/service/impl/StubProjectAccess.java:17` — a plain `@Component`; the design's Stub-safety decision says `@ConditionalOnMissingBean(ProjectAccess.class)`. When the workspace module adds the real bean, startup fails with two `ProjectAccess` beans (safe, but a surprise).
   Fix: register the stub as a `@Bean @ConditionalOnMissingBean(ProjectAccess.class)` in a config class, or record in the design that it must be deleted together with the real implementation.
2. [Low] `backend/src/main/java/vn/tsolve/common/error/GlobalExceptionHandler.java:20-87` — no handler for `MissingServletRequestParameterException`/`MissingServletRequestPartException` (upload without `file`/`projectId`) or unexpected exceptions such as `DataIntegrityViolationException`; these return Spring Boot's default error body without `code`, unlike AGENTS.md §5.
   Fix: extend `ResponseEntityExceptionHandler` or add handlers mapping them to 400 MSG06/MSG01 and 500 with a code.
3. [Low] Real oversized uploads are proved only at handler level. Tomcat's default `server.tomcat.max-swallow-size` (2 MB) can reset the connection before the 413 body reaches the browser for files well over 11 MB.
   Fix: set `server.tomcat.max-swallow-size: -1` (or ≥ 11 MB) and check once with a real request.

## Assumptions made
- The `sdd.ts trace` result (0/41) is a tool limitation, not missing tests; the manual `_AC_XXX_Y` trace was used instead (dispatcher instruction).
- Coverage was not measured (no JaCoCo in `backend/pom.xml`) and the spec sets no threshold — open question for the user whether a coverage figure is required.

## Test Report
Shared full run (all 13 tasks), see `task-5-project-access-errors-test-report.md`: `mvn verify` 91/91 passed (87 unit + 4 `ImportConfirmIT` on PostgreSQL 16), 0 failed/skipped; `spotless:check` pass; frontend `typecheck` and `lint` pass. Coverage not measured (no coverage plugin). Security: `security-review.md`.
