# Test Report: platform — Task 6a-2: no-workspace-fallbacks

**Date:** 2026-11-24
**Branch / HEAD:** `refactor/KAN-385-platform-no-workspace-fallbacks` @ `9766c6e7` (`main..9766c6e7`, PR #323)
**Commands:**
- Reviewer targeted unit run: `mvn -f backend/pom.xml "-DfailIfNoTests=false" "-Dtest=SuspendedWorkspaceScopeTest,ThreadLocalTenantContextTest,WebSessionFilterTenantContextTest,PlatformTenantScopeTest,NoWorkspaceFallbacksTest,ReviewExpiryDispatchHandlerTest,ReviewExpiryJobHandlerTest,ReviewExpiryServiceImplTest,WorkerSuggestionJobQueueTest,OutboxOnlyConfigurationTest,WebJobsConfigurationTest,WorkerMockConfigurationTest,WorkerTenantServiceTest,ReviewNotificationEnqueueBugTest,ReviewNotificationEnqueuerTest,TenantExecutionServiceImplTest,TenantJobServiceImplTest,MockWorkerTenantServiceTest,WorkerJobTenantBindingTest" test -l <scratch>/kan385-unit-tests.log`
- Traceability: `bun harnessclaude/hooks/sdd.ts trace platform STORY-002`
- Developer verify (from card, not re-run locally per reviewer rules):
  - `773832de`: `mvn -q test-compile` exit 0 · targeted unit/config tests exit 0 (~24 s) · `mvn spotless:check` exit 0 · `EmailOutboxIT` 11/11 pass (~61 s) · `sdd.ts trace platform` AC-002.11–.16 all mapped
  - `82a480a4`: `bun harnessclaude/hooks/sdd.ts check platform` → `spec platform: 15 stories, 187 acceptance criteria — OK`
  - `9766c6e7`: `mvn -f backend/pom.xml spotless:check` → `BUILD SUCCESS` (3.8 s) · `test-compile` → `BUILD SUCCESS` (3.5 s) · `WebJobsConfigurationTest,OutboxOnlyConfigurationTest` → `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0` (8.1 s)
  - Full suites (CI on PR #323 @ `9766c6e7`, confirmed by Hiếu): `backend-tests (unit)`, `it-keycloak-a`, `it-keycloak-b`, `it-other`, `backend`, `pr-title`, `gitleaks` all GREEN (`WebJobsIT` failure on `82a480a4` fixed by `9766c6e7`).

## Results

- Reviewer targeted unit run (19 classes): **Total: 117 · Passed: 117 · Failed: 0 · Errors: 0 · Skipped: 0 · Duration: 59.961 s (`BUILD SUCCESS`)**
- Typecheck / Compile: pass (via Maven compile/test-compile in targeted run + developer verify)
- Lint / Format (`spotless:check`): pass (developer verify on HEAD `9766c6e7` + CI green)
- Full unit + integration suites: pass in GitHub CI on PR #323 @ `9766c6e7`

### Per-Class Breakdown (Reviewer Run)

| Test class | Tests | Failures | Errors | Skipped | Time |
|---|---:|---:|---:|---:|---:|
| `SuspendedWorkspaceScopeTest` | 1 | 0 | 0 | 0 | 0.399 s |
| `ThreadLocalTenantContextTest` | 9 | 0 | 0 | 0 | 4.690 s |
| `WebSessionFilterTenantContextTest` | 4 | 0 | 0 | 0 | 4.397 s |
| `PlatformTenantScopeTest` | 4 | 0 | 0 | 0 | 0.032 s |
| `NoWorkspaceFallbacksTest` | 2 | 0 | 0 | 0 | 33.05 s |
| `ReviewExpiryDispatchHandlerTest` | 3 | 0 | 0 | 0 | 0.413 s |
| `ReviewExpiryJobHandlerTest` | 8 | 0 | 0 | 0 | 0.207 s |
| `ReviewExpiryServiceImplTest` | 3 | 0 | 0 | 0 | 0.108 s |
| `WorkerSuggestionJobQueueTest` | 5 | 0 | 0 | 0 | 0.433 s |
| `OutboxOnlyConfigurationTest` | 7 | 0 | 0 | 0 | 0.430 s |
| `WebJobsConfigurationTest` | 4 | 0 | 0 | 0 | 0.151 s |
| `WorkerMockConfigurationTest` | 2 | 0 | 0 | 0 | 0.063 s |
| `WorkerTenantServiceTest` | 1 | 0 | 0 | 0 | 0.000 s |
| `ReviewNotificationEnqueueBugTest` | 16 | 0 | 0 | 0 | 0.680 s |
| `ReviewNotificationEnqueuerTest` | 18 | 0 | 0 | 0 | 0.175 s |
| `TenantExecutionServiceImplTest` | 7 | 0 | 0 | 0 | 0.031 s |
| `TenantJobServiceImplTest` | 8 | 0 | 0 | 0 | 0.314 s |
| `WorkerJobTenantBindingTest` | 6 | 0 | 0 | 0 | 1.039 s |
| `MockWorkerTenantServiceTest` | 9 | 0 | 0 | 0 | 0.013 s |

## Coverage

| Metric | Value | Target (from spec) |
|---|---|---|
| Line / branch coverage | Not run (targeted unit run only; spec sets no numeric threshold for STORY-002) | Not specified in `platform/spec.md` |

## Acceptance Criteria → Tests

Output of `bun harnessclaude/hooks/sdd.ts trace platform STORY-002` (exit code 1 because AC-002.1–.10 and AC-002.17–.25 belong to later Tasks 6 and 10; all 6 criteria in scope for Task 6a-1/6a-2 are mapped):

```text
  ✓ AC-002.11  backend/src/test/java/vn/tsolve/common/tenant/WebSessionFilterTenantContextTest.java
  ✓ AC-002.12  backend/src/test/java/vn/tsolve/common/tenant/ThreadLocalTenantContextTest.java
  ✓ AC-002.13  backend/src/test/java/vn/tsolve/platform/NoWorkspaceFallbacksTest.java, backend/src/test/java/vn/tsolve/common/tenant/ThreadLocalTenantContextTest.java
  ✓ AC-002.14  backend/src/test/java/vn/tsolve/common/tenant/ThreadLocalTenantContextTest.java
  ✓ AC-002.15  backend/src/test/java/vn/tsolve/common/tenant/SuspendedWorkspaceScopeTest.java
  ✓ AC-002.16  backend/src/test/java/vn/tsolve/common/tenant/ThreadLocalTenantContextTest.java
  ✗ AC-002.1 – AC-002.10, AC-002.17 – AC-002.25  (not in Task 6a-2 scope; Tasks 6 & 10)
trace platform (STORY-002): 6/25 acceptance criteria have a test
```

## Failures

None.

## Notes

- **Slow test (>5 s):** `NoWorkspaceFallbacksTest` took 33.05 s on Windows because `theRemovedNamesAreReadNowhere_AC_002_13` reads all 1,226 files under `src/main`, `src/test`, `../.env.example`, and `../infra` six times each (once per removed name — ~7,356 `Files.readString` + `Pattern.compile` calls). Reading each file once and pre-compiling the 6 patterns (or one combined pattern) would cut this to <1 s. Non-blocking; logged as an informational finding in the review.
- **No skipped or flaky tests** across the 117 tests run.
- `git status --short` showed only `M harnessclaude/events/events.jsonl` (session hook log); `HEAD` matches the card's `9766c6e7`.
