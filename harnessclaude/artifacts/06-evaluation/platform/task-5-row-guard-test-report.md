# Test Report: platform — Task 5: row-guard

**Date:** 2026-11-24
**PR / Range:** PR #322 (`60594a7c..cd001049`, branch `feat/KAN-253-platform-row-guard`)
**Commands (reviewer, fresh on `cd001049`):**
- Targeted ITs: `mvn -f C:\Users\ADMIN\Documents\GitHub\T-Solve-KAN-253\backend\pom.xml failsafe:integration-test failsafe:verify "-Dit.test=ConventionGuardIT,RowGuardIT"` (log: `%TEMP%\kan253-t5-review-it.log`, exit 0)
- Traceability: `bun harnessclaude/hooks/sdd.ts trace platform STORY-001 STORY-003` (from `C:\Users\ADMIN\Documents\GitHub\T-Solve-KAN-253`)
- PR CI checks: `gh pr checks 322`
- Typecheck / lint / full local suite: not re-run by reviewer per role rule; verified via developer's verify on `cd001049` (`mvn spotless:check test`: 3,242 tests, 0 failures; 7 targeted IT classes: 44 tests, 0 failures) and PR #322 GitHub CI (`cd001049`, all required jobs green). No coverage command is configured and the spec sets no coverage threshold.

## Results
- Reviewer targeted ITs: Total: 17 · Passed: 17 · Failed: 0 · Skipped: 0 · Duration: 17.77s (`ConventionGuardIT`: 9/9 in 10.80s; `RowGuardIT`: 8/8 in 6.965s) · `BUILD SUCCESS`
- Developer verify (`cd001049`): `mvn spotless:check test` pass (3,242/3,242, 06:06 min); targeted ITs (`ChatbotRatingSchemaIT, WorkspaceInfoRepositoryIT, ConventionGuardIT, RowGuardIT, ChatbotSchemaIT, ReviewStorageIT, RegistryChangelogIT`) pass (44/44, 01:48 min)
- PR #322 CI (`gh pr checks 322` on `cd001049`):
  - `backend`: pass (3s)
  - `backend-tests (unit)`: pass (2m44s)
  - `backend-tests (it-keycloak-a)`: pass (2m33s)
  - `backend-tests (it-keycloak-b)`: pass (4m9s)
  - `backend-tests (it-other)`: pass (8m42s)
  - `frontend`: pass (2s) (`frontend-checks` & `frontend-tests` skipped — no frontend changes)
  - `changes`: pass (13s)
  - `gitleaks`: pass (9s)
  - `pr-title`: pass (25s)
- Typecheck / Compile: pass · Lint (`spotless:check`): pass (developer verify + CI `backend`)

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line coverage | not measured (no tool configured) | none in spec |

## Acceptance Criteria → Tests
```
  ✓ AC-001.15  infra/tests/registry-db.test.sh, backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
  ✓ AC-001.16  backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
  ✓ AC-001.17  backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
  ✗ AC-001.34  no test under this module's paths (...) — belongs to Task 4 / Task 7 (outside Task 5)
  ✓ AC-001.18  backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java, backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.19  backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
  ✓ AC-001.20  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.21  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.22  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.23  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.24  backend/src/test/java/vn/tsolve/platform/registry/PasswordCipherTest.java
  ✓ AC-001.25  backend/src/test/java/vn/tsolve/platform/registry/PasswordCipherTest.java, backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
  ✗ AC-001.26  no test under this module's paths (...) — belongs to Task 7 provisioning (outside Task 5)
  ✓ AC-001.27  backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java, backend/src/test/java/vn/tsolve/common/tenant/RowGuardIT.java
  ✓ AC-001.28  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java, backend/src/test/java/vn/tsolve/common/tenant/RowGuardIT.java
  ✓ AC-001.29  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java, backend/src/test/java/vn/tsolve/common/tenant/RowGuardIT.java
  ✓ AC-001.30  backend/src/test/java/vn/tsolve/common/tenant/RowGuardIT.java
  ✓ AC-001.31  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.32  infra/tests/registry-db.test.sh, backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.33  backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
  ✓ AC-003.10  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
trace platform (STORY-001, STORY-003): 19/21 acceptance criteria have a test
```
All 12 Task 5 acceptance criteria (`AC-001.18`, `AC-001.20`–`.23`, `AC-001.27`–`.32`, `AC-003.10`) have passing tests. The two `✗` entries (`AC-001.26` and `AC-001.34`) belong to Tasks 4 and 7 and are outside Task 5's scope.

## Failures
None.

## Notes
- Test duration: `ConventionGuardIT` (10.80s, 9 tests) and `RowGuardIT` (6.965s, 8 tests) share the single Testcontainers `pgvector/pgvector:pg16` server via `PostgresTestBase` (one DB per fixture, no extra container started; compliant with `backend/AGENTS.md` §8). No single test is flaky or skipped.
- `git grep -n "isolation_probes" backend/src/main` returned 0 matches (confirming the test-only probe table does not leak into production).
- Rollback compatibility of changesets `051`–`054` (including `053`'s `<rollback>` added in `cd001049`) is proven by `ChatbotRatingSchemaIT` and `WorkspaceInfoRepositoryIT` passing in developer verify and CI `backend-tests (it-other)`.
