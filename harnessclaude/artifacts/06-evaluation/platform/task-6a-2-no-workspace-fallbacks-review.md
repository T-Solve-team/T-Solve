---
module: platform
feature: no-workspace-fallbacks
task: 6a-2
verdict: pass
attempt: 1
date: 2026-11-24
reviewer: tsolve-code-reviewer
iteration: 1
currentIteration: 1
---

# Review: platform — Task 6a-2: no-workspace-fallbacks (PR #323, KAN-385)

## Summary

Verdict **pass**. Reviewed range `main..9766c6e7` (35 files, +510 / -504). Every Workspace fallback scheduled for removal in Task 6a-2 (`StubTenantContext`, `OutboxWorkspace`, `TSOLVE_STUB_WORKSPACE_ID` / `tsolve.stub.workspace-id`, `TSOLVE_TRACKER_DEV_WORKSPACE_ID` / `tsolve.tracker.dev-workspace-id`, `MockWorkerTenantService`'s 2-arg constructor and request-thread check, and `WorkerTenantService.enqueueWorkspaceId`'s default body) is cleanly removed and verified across code, tests, config, and env files. All 6 in-scope acceptance criteria (AC-002.11 to AC-002.16) trace and pass; all 117 targeted unit tests pass locally; GitHub CI on PR #323 @ `9766c6e7` is green. Zero CRITICAL or security findings; 4 informational notes remain.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1 passes for all 6 in-scope ACs (AC-002.11–AC-002.16) and all 6 decisions Q1–Q6. Bound-only enforcement works on `OutboxOnlyConfiguration`, `WebJobsConfiguration`, `ReviewNotificationEnqueuer`, `WorkerSuggestionJobQueue`, and `MockWorkerTenantService`. |
| Completeness | pass | All fallbacks deleted; zero references remain in `backend/`, `.env.example`, `infra/`, `docker-compose*`, or `.github/`. All 6a-1 carryover items addressed (abstract `enqueueWorkspaceId`, 2-arg constructor removed, trace paths fixed, `WorkerSuggestionJobQueue` Active-filtered, `TenantJobServiceImplTest` exception pinned, `OutboxOnlyConfiguration` 186-col Javadoc gone, `.env.example` note added, Task 4 `WorkerTenantService` owner recorded). |
| Correctness | pass | Targeted reviewer run: 19 test classes, 117 tests, 0 failures, 0 errors, 0 skipped. Developer verify + GitHub CI on HEAD `9766c6e7` green. No CRITICAL code-review or High/Critical security findings. |
| Integration | pass | `TenantContext` interface unchanged; `WorkerTenantService.enqueueWorkspaceId()` is now abstract and implemented by both `MockWorkerTenantService` and `OutboxOnlyConfiguration.RequestTenant`; all `MockWorkerTenantService` call sites updated; `WebJobsConfiguration` no longer requires `OutboxOnlyConfiguration`; `WebJobsIT` updated for holder wiring and passes in CI. |

## Stage 1 — Spec Compliance (STORY-002, AC-002.11 to AC-002.16)

| # | Criterion (AC id) | Status | Evidence |
|---|---|---|---|
| 1 | AC-002.11: Request ends (normal or exception) → unbinds Workspace so next request on same thread starts with none | PASS | `WebSessionFilterTenantContextTest.java` (moved to `vn.tsolve.common.tenant`; tests normal exit and `IllegalStateException`/`ServletException`/`IOException` followed by unbound next request throwing `NoTenantException`) |
| 2 | AC-002.12: `runIn` when unbound or same Workspace runs and restores binding, also on throw | PASS | `ThreadLocalTenantContextTest.java:50-111`; `OutboxOnlyConfigurationTest.java:74-87`; `WorkerMockConfigurationTest.java:36-45`; `WorkerTenantServiceTest.java:12-18`; `MockWorkerTenantServiceTest.java:66-84,106-116,145-151`; `ReviewNotificationEnqueueBugTest.java:282-293` |
| 3 | AC-002.13: `runIn` with different Workspace throws and does not run action | PASS | `ThreadLocalTenantContextTest.java:116-128`; `NoWorkspaceFallbacksTest.java:41-76` (confirms holder is the only `TenantContext` bean even if `tsolve.stub.workspace-id` is set, and none of the 6 removed symbols appear anywhere); `WorkerSuggestionJobQueueTest.java:90-106`; `WorkerJobTenantBindingTest.java:104-115` |
| 4 | AC-002.14: `runIn` with unknown id throws and does not run action | PASS | `ThreadLocalTenantContextTest.java:133-147`; `MockWorkerTenantServiceTest.java:89-102`; `WorkerJobTenantBindingTest.java:120-129` |
| 5 | AC-002.15: Suspended Workspace still binds via `runIn`; worker refuses its jobs | PASS | `SuspendedWorkspaceScopeTest.java:32-42` (extracted from `PlatformTenantScopeTest` into `vn.tsolve.common.tenant` for traceability); `MockWorkerTenantServiceTest.java:98-101,120-129`; `TenantJobServiceImplTest.java:117-129` |
| 6 | AC-002.16: `requireWorkspaceId()` when unbound throws exception mapped to 500 `MSG06` and logs call site | PASS | `ThreadLocalTenantContextTest.java:152-195`; `OutboxOnlyConfiguration.java:107` + `OutboxOnlyConfigurationTest.java:91-108` (`NoTenantException`, zero DB insert); `WebJobsConfiguration.java:64` + `WebJobsConfigurationTest.java:57-65` (`NoTenantException`, zero JobRunr runtime); `ReviewNotificationEnqueuer.java:118-123` + `ReviewNotificationEnqueueBugTest.java:297-309`; `WorkerSuggestionJobQueue.java:34-39` + `WorkerSuggestionJobQueueTest.java:64-74` |

Notes on decisions Q1–Q6:
- **Q1:** `WorkerTenantService.java:16` has abstract `enqueueWorkspaceId()`, verified by `WorkerTenantServiceTest.java:12-18`. Both implementations (`MockWorkerTenantService` and `OutboxOnlyConfiguration.RequestTenant`) override it explicitly.
- **Q2:** `OutboxWorkspace.java` deleted; `OutboxOnlyConfiguration.java:95,103-108`, `WebJobsConfiguration.java:27,64`, and `ReviewNotificationEnqueuer.java:39,67,117-123,164-165` read `TenantContext` directly. `WebJobsConfiguration` no longer checks for `OutboxOnlyConfiguration`.
- **Q3:** `MockWorkerTenantService.java:38-44` filters any bound id by `active::contains` regardless of `RequestContextHolder`, and falls back to the single Active id only when unbound (matching BUG-164).
- **Q4:** `WorkerSuggestionJobQueue.java:34-39` requires both `currentWorkspaceId().isPresent()` and `enqueueWorkspaceId().isPresent()`, preventing unbound single-Active fallback queuing and Suspended bound queuing.
- **Q5:** `WebSessionFilterTenantContextTest` moved to `vn.tsolve.common.tenant` and `SuspendedWorkspaceScopeTest` created there; `sdd.ts trace` now maps all 6 ACs (`AC-002.11`–`AC-002.16`).
- **Q6:** `.env.example`, `infra/staging.env.example`, `infra/README.md`, and `application.yml` updated; removed env vars appear nowhere in repo code/config.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace platform STORY-002`:
- `✓ AC-002.11` — `backend/src/test/java/vn/tsolve/common/tenant/WebSessionFilterTenantContextTest.java`
- `✓ AC-002.12` — `backend/src/test/java/vn/tsolve/common/tenant/ThreadLocalTenantContextTest.java`
- `✓ AC-002.13` — `backend/src/test/java/vn/tsolve/platform/NoWorkspaceFallbacksTest.java`, `backend/src/test/java/vn/tsolve/common/tenant/ThreadLocalTenantContextTest.java`
- `✓ AC-002.14` — `backend/src/test/java/vn/tsolve/common/tenant/ThreadLocalTenantContextTest.java`
- `✓ AC-002.15` — `backend/src/test/java/vn/tsolve/common/tenant/SuspendedWorkspaceScopeTest.java`
- `✓ AC-002.16` — `backend/src/test/java/vn/tsolve/common/tenant/ThreadLocalTenantContextTest.java`
- `✗ AC-002.1 – AC-002.10, AC-002.17 – AC-002.25` — out of Task 6a-2 scope (Tasks 6 and 10). Total: `6/25`.

## Stage 2 — Quality & Impact Review

Impact scan across all `TenantContext`, `WorkerTenantService`, `WebJobQueue`, `EmailOutboxService`, and async/scheduled/startup entry points:
- No `TenantContext` consumer runs on an unbound startup, `@Scheduled`, or `@Async` thread (`ObjectFileStorage.checkBucket()` only runs `headBucket`, not tenant-scoped; `MockUserListSeeder` wraps in `TenantScope.runIn`; `TicketsPublishedListener` and `EmbeddingCatchUpJob` do not inject `TenantContext`).
- In `OutboxOnlyConfiguration.RequestTenant.currentWorkspaceId()` (`:105-108`), throwing `NoTenantException` when unbound propagates out of `EmailOutboxServiceImpl.queue` (after `EmailAddressRules.validate`). In `ReviewNotificationEnqueuer`, the web-process guard (`:118`) catches unbound threads before calling `queue.queue(...)` and logs one warning; if any other caller invokes `EmailOutboxService.queue` unbound, `NoTenantException` is caught by `GlobalExceptionHandler` → 500 `MSG06` (AC-002.16).
- In `WebJobsConfiguration.WebJobs.enqueue` (`:64`), `tenantContext.requireWorkspaceId()` runs before `runtime()` so an unbound call fails with `NoTenantException` without initializing JobRunr or touching the Jobs DB.

## Security Review — platform — 2026-11-24

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 0 |
| STRIDE | 0 | 0 | 0 | 0 |

- **Secrets:** Pattern scan over the full diff (`main..9766c6e7`) found 0 secret hits. `git ls-files` contains only `.env.example` and `frontend/.env.example`; `.gitignore` excludes `.env`, `.env.*` (except `!.env.example`), `*.pem`, `*.key`.
- **Dependencies:** No `pom.xml` or dependency changes in PR #323 (no audit tool re-run required).
- **Code & STRIDE (tenant isolation):**
  - *Spoofing / Elevation of privilege (IDOR / cross-tenant leakage):* Eliminating `StubTenantContext`, `OutboxWorkspace`, `TSOLVE_STUB_WORKSPACE_ID`, and `TSOLVE_TRACKER_DEV_WORKSPACE_ID` removes all static/single-Active fallbacks from the web request and suggestion-queue paths. No code can silently fall back to an environment-configured Workspace or a single Active Workspace when a request thread is unbound, nor enqueue a suggestion/job/email for a Suspended or unlisted bound Workspace (`MockWorkerTenantService.enqueueWorkspaceId` now filters bound ids on every thread, not only servlet request threads).
  - *Information disclosure / Repudiation:* `ThreadLocalTenantContext.logCaller()` logs only class/method without Workspace UUIDs; `ReviewNotificationEnqueuer` and `WorkerSuggestionJobQueue` log only counts/issue keys and exception class names (never email addresses, feedback, or exception messages).
  - *Tampering / DoS:* No new endpoints, queries, or deserialization paths added.

## Issues

### CRITICAL (blocks pass)

No issues found.

### Informational (does not block)

1. `backend/src/test/java/vn/tsolve/tracker/service/impl/WorkerSuggestionJobQueueTest.java:64-74` — `logsWithoutThrowingWhenNoWorkspaceIsBound_AC_002_16` stubs `enqueueWorkspaceId()` to `Optional.empty()`, so it does not exercise Q4's exact guard (`currentWorkspaceId().isEmpty()` while `MockWorkerTenantService.enqueueWorkspaceId()` returns the sole Active Workspace).
   - Fix: add a case with a real `ThreadLocalTenantContext` (unbound) and single-Active `MockWorkerTenantService` asserting `verifyNoInteractions(jobs)` and `verify(tenants, never()).runIn(any(), any())` so the `currentWorkspaceId().isPresent()` half of line 34 is pinned directly.
2. `backend/src/main/resources/application.yml:93` & `backend/README-worker.md:45-48` & `backend/src/main/java/vn/tsolve/worker/service/WebJobQueue.java:12` — stale comments/docs still describe the removed `OutboxWorkspace` behavior: `application.yml:93` says `web-jobs-enabled` "needs outbox-enabled", `README-worker.md:45-48` says `OutboxOnlyConfiguration` falls back to `TSOLVE_MOCK_ACTIVE_WORKSPACES` and "the staging web process has no `TenantContext` bean", and `WebJobQueue.java:12` says "no single Workspace". Also `ReviewNotificationEnqueuer.java:59` Javadoc is 129 columns (limit 120 in `backend/AGENTS.md`).
   - Fix: update those 3 comments/docs to describe `TenantContext`-only binding and reflow `ReviewNotificationEnqueuer.java:59` (can be folded into Task 4 or a quick cleanup).
3. `backend/src/test/java/vn/tsolve/platform/NoWorkspaceFallbacksTest.java:67-73` — scans all 1,226 files once per entry in `REMOVED_NAMES` (6 passes, re-reading files and re-compiling `Pattern` each time), taking 33.05 s on Windows.
   - Fix: pre-compile one combined regex (or read each file once and check pre-compiled patterns) to reduce runtime to <1 s.
4. `backend/src/test/java/vn/tsolve/worker/config/WebJobsConfigurationTest.java:25-71` — the `staging` runner still wires `OutboxOnlyConfiguration.class` and `OUTBOX` on every test, so there is no test proving Q2's new independence (`WebJobsConfiguration` starting with `web-jobs-enabled=true` and `outbox-enabled=false`).
   - Fix: add a small test running `WebJobsConfiguration` + `ThreadLocalTenantContext` without `OutboxOnlyConfiguration` / `outbox-enabled`.

## Assumptions made

- **No real `WorkerTenantService` yet (Q1 / Task 4):** `OutboxOnlyConfiguration.RequestTenant.enqueueWorkspaceId()` returns `Optional.empty()` because `RequestTenant` is private to `OutboxOnlyConfiguration` and only `EmailOutboxServiceImpl` calls it (which uses `currentWorkspaceId()` only). That is safe as long as `RequestTenant` stays private and is never exposed as a bean.
- **Unbound single-Active rule in `MockWorkerTenantService` (Q3):** Kept intentionally for local/demo BUG-164 tests until Task 4 replaces `MockWorkerTenantService` with the catalog-backed `WorkerTenantService`; `WorkerSuggestionJobQueue` explicitly blocks this fallback by checking `currentWorkspaceId().isPresent()` first (Q4).
- **Proposed feature status:** `no-workspace-fallbacks` → `passing` (not changed in `progress.json` per card instructions; currently `pending` in `progress.json`).

## Test Report

See [task-6a-2-no-workspace-fallbacks-test-report.md](file:///c:/Users/ADMIN/Documents/GitHub/T-Solve/harnessclaude/artifacts/06-evaluation/platform/task-6a-2-no-workspace-fallbacks-test-report.md).
- Reviewer targeted unit run: 19 test classes, **117 run, 0 failed, 0 errors, 0 skipped** (59.961 s, `BUILD SUCCESS`).
- `sdd.ts trace platform STORY-002`: **6/25** — all 6 in-scope ACs (`AC-002.11`–`AC-002.16`) mapped with `✓`.
- Developer verify + GitHub CI on PR #323 @ `9766c6e7`: `spotless:check`, `test-compile`, `backend-tests (unit)`, `it-keycloak-a`, `it-keycloak-b`, `it-other`, `backend`, `pr-title`, `gitleaks` all **pass / GREEN**.
