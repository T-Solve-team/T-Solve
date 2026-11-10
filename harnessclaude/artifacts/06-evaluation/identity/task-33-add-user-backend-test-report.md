# Test Report: identity — Task 33: add-user-backend

- Feature: Add user backend (KAN-108) · PR #164, code range `e2c496cc..595c6e74` (squash-merged); evaluated on up-to-date `main` at `29a42b7e`.
- Date: 2026-11-10 · Run by: dispatcher (evaluate session), reviewed by harnessclaude-reviewer (subagent)

## Full suite

Command: `mvn -B verify -DargLine=-Dapi.version=1.44 -Dspotless.check.skip=true` in `backend/` (JDK 21.0.12.1,
Maven 3.9.9, Docker 29.9.2). The `api.version` workaround is for BUG-058/BUG-129. Spotless was skipped in this run
because it cannot run in a linked worktree (BUG-130), and was checked separately (see Format). Result: **BUILD SUCCESS**, 12:30 min.

| Runner | Classes | Tests | Failures | Errors | Skipped |
|--------|---------|-------|----------|--------|---------|
| Surefire (unit, `*Test`) | 108 | 1086 | 0 | 0 | 0 |
| Failsafe (IT, `*IT`, Testcontainers `pgvector/pgvector:pg16`) | 51 | 395 | 0 | 0 | 0 |

Feature classes (from `backend/target/surefire-reports` and `failsafe-reports`):

| Class | Tests | Failures | Errors | Time |
|-------|-------|----------|--------|------|
| `UserCreationIT` | 11 | 0 | 0 | 2.7 s |
| `MockSignedInCallerIT` (AC-005.14 seed) | 7 | 0 | 0 | 4.4 s |
| `UserCreationServiceImplTest` | 38 | 0 | 0 | 0.4 s |
| `UserCreationControllerTest` | 17 | 0 | 0 | 0.9 s |

No flaky, skipped or failing test. No test in these classes took over 5 s except as noted in the table (ITs that
start their own container).

## Format and contract

- `mvn spotless:check` cannot run in a linked worktree (BUG-130, `docs/bug-log.md`). Instead, the same Palantir
  formatter (`spotless-maven-plugin` 2.43.0, `palantirJavaFormat` + `removeUnusedImports`, LF line endings) was run
  through a temporary POM without `ratchetFrom` on the current `main` versions of every Java file the PR changed
  (20 files): **no violation**.
- Frontend checks were not run: the PR changes no file under `frontend/`.

## Traceability

harnessclaude/artifacts/06-evaluation/identity/task-33-add-user-backend-test-report.md0

## Coverage

No coverage tool is configured for the backend; coverage was judged by reading the tests against each criterion
(see the review).

## Defects

No new defect. Known environment bugs: BUG-058/BUG-129 (Docker 29 API version for Testcontainers), BUG-130 (Spotless
`ratchetFrom` in a linked worktree).
