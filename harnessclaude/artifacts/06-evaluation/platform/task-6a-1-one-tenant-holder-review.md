---
module: platform
feature: one-tenant-holder
task: 6a-1
verdict: pass
attempt: 3
date: 2026-11-23
reviewer: tsolve-code-reviewer
iteration: 1
currentIteration: 1
---

# Review: platform, Task 6a-1: one Workspace holder (KAN-384), attempt 2

## Summary

- **Attempt 1** (`prompts/handoffs/KAN-384-review.md`, range `ab0489b6..c0104d09`): fail ("fix first"), 2 findings:
  1 critical (12 unit tests red in the `DiscoveryCheckTest`, `IdentityClockTest` and `WorkerConfigurationTest` context
  runners), 1 should-fix (a request bound to a Suspended or unlisted Workspace queues a job the worker refuses; the
  BUG-324 assertions were lost), plus 6 notes.
- **Attempt 2** (this file), range `c0104d09..2d3fb18f`. The fix commits are `68125e49`, `99e124f1`, `8956277a` and `8063b119`.
  `132a51cf` merges `main` (`git show --cc` shows a conflict only in `tasks.md`; the merge diff against `main` holds
  only the 6a-1 files). `ed781269` and `2d3fb18f` are harness only.
- Verdict **fail**: finding 1 of attempt 1 is resolved. Finding 2 is resolved inside `enqueueWorkspaceId` but not in
  its effect. No production caller reaches the new filter, so a request bound to a Suspended or unlisted Workspace
  still queues a job (proved below). The new tests check the method only. They do not check the enqueue path that
  BUG-324 protects.

## Criteria

| Criterion | Result | Notes |
|---|---|---|
| Functionality | PASS for AC-002.11 to .16 (unchanged since attempt 1) | The fixes touch none of the holder code (`ThreadLocalTenantContext`, `PlatformTenantScope`) |
| Completeness | FAIL | The plan says 6a-1 removes no behaviour (Q3 (a), `tasks.md` Task 6a-1). BUG-324's "Suspended request → nothing queued" is still lost on the real enqueue path |
| Correctness | PASS on tests, FAIL on finding 2 | CI on `8063b119` is green. The scratch proof shows the Suspended job queued |
| Integration | PASS | No routing, DB, config or yml change in the PR. Merge clean |

## Card checks

1. **Finding 1: resolved.** `68125e49` only adds beans: `DiscoveryCheckTest.java:108-109`, `IdentityClockTest.java:30-31`
   (`WorkspaceLookup` mock + `TenantContext`) and `WorkerConfigurationTest.java:37` (`TenantContext`). It changes no
   assertion. In `c0104d09..HEAD` the only removed test lines under `backend/src/test` are the two lines of
   `TicketListRepositoryIT` (check 3). The 28 QA tests are untouched. `MockWorkerTenantServiceTest` only gains two
   tests. CI `backend-tests (unit)` is green.
2. **Finding 2: not resolved in effect.** See issue 1. `MockWorkerTenantService.enqueueWorkspaceId` (`:45-50`) now
   filters a bound id through `active` on a request thread. The two restored tests assert empty for Suspended (`S`) and
   unlisted (`KNOWN_ONLY`). Compared with `c0104d09`, the unbound path (single Active, else empty) and the bound
   non-request path (bound id unfiltered) are unchanged. The fix opens no new way to cross Workspaces: the bound path
   can only return the thread's own id or nothing. The single-Active fallback is no longer taken while a request is
   bound, which is stricter than `ab0489b6`, where a Suspended request with one Active Workspace got that other
   Workspace.
3. **CI fix: correct.** `TicketListRepositoryIT` sets `TSOLVE_STUB_WORKSPACES` to one entry in the format of
   `NoWorkspaceLookup.java:17` and binds that id at `:274`. The removed lines were `"SEARCH_DEV_SCOPE="` (now with a
   trailing comma) and `UUID.randomUUID()`; no assertion changed. No production change is needed. In the real app no
   path can bind an unknown id: `ThreadLocalTenantContext.runIn` (`:47-49`) refuses any id that `KnownWorkspaces` does
   not know, and it is the only writer of the `ThreadLocal`. All main-code `runIn` callers go through it. The request
   filter binds the session's Workspace, which the lookup knows. The reverse risk is that ids read from config get
   refused loudly when they are not in `TSOLVE_STUB_WORKSPACES`: `WorkerSuggestionJobQueue.java:46`
   (`TSOLVE_TRACKER_DEV_WORKSPACE_ID`), `JiraAppTokenServiceImpl.java:62` (`TSOLVE_STUB_JIRA_SITES`), and
   `MockSignedInCallerFilter.java:43` / `MockUserListSeeder.java:28` (identity-mock). This is already on the attempt-1
   list for 6a-2.
4. **Scope: clean.** The second-parent diff of the merge holds only the 31 tenant/test files of 6a-1. The Registry
   files in `c0104d09..HEAD` (`C001`/`C002`, `RegistryDataSourceConfig`, `application.yml`, `.env.example`,
   `10-tsolve-dbs.sh`) come from `main` (#317), not from this PR. The attempt-1 notes for 6a-2 still apply, and none
   leaked in.
5. **New risk.** Issue 1, plus the doc drift in issue 2.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace platform STORY-002`: 4/25. ✓ AC-002.12, .13, .14, .16
(`ThreadLocalTenantContextTest`). ✗ AC-002.11 and .15: the tests are in `identity/session` and `identity/signin/port`,
outside platform's paths (attempt-1 note 5, a harness change). AC-002.1 to .10 and .17 to .25 are not in this task.

## Issues

1. **should-fix**: `backend/src/main/java/vn/tsolve/worker/service/impl/TenantJobServiceImpl.java:34-35` and
   `backend/src/main/java/vn/tsolve/worker/service/impl/ReviewNotificationEnqueuer.java:124-125`: both callers check
   `currentWorkspaceId().isPresent()` first and, when it is bound, enqueue directly without calling
   `enqueueWorkspaceId()`. `MockWorkerTenantService.currentWorkspaceId()` reads the holder, so the new
   `bound.filter(active::contains)` at `MockWorkerTenantService.java:48` runs only when a Workspace is bound, and no
   production caller calls it then. The branch is still unreachable from production, as in attempt 1. The commit
   message of `8956277a` ("A request bound to a Suspended or unlisted Workspace no longer queues a job") does not hold.
   - Proof (scratch program outside the repo, compiled against `backend/target/classes` built after `8956277a`): a
     request thread bound to Suspended `S` with Active `{A, B}` prints `enqueueWorkspaceId() ... Optional.empty`, then
     `enqueueFromRequest ... QUEUED job for S (Suspended)`, `jobs stored: 1`. On `ab0489b6` the same call threw
     `Enqueue requires a bound workspace`.
   - The tests `enqueueWorkspaceQueuesNothingFor*_BUG_324` (`MockWorkerTenantServiceTest.java:118-141`) check the
     method only, so they pass while the behaviour they name is gone. `TenantJobServiceImplTest.java:95` also runs
     only the bound Active case.
   - Fix (choose one; a judgment call): (a) in `enqueueFromRequest`, refuse a bound Workspace that is not active
     (`tenants.isWorkspaceActive(bound)`, same `IllegalStateException`), and do the same in the review-email bound
     branch. Add a `TenantJobServiceImplTest` case: a request bound to Suspended and to known-but-unlisted is refused,
     with 0 jobs stored. Then the mock's request branch can go. (b) Accept GBR-38 (the worker refuses the job at
     `TenantExecutionServiceImpl.java:24`): revert the branch at `:48`, rename or reword the two tests so they claim
     only what they check, and record "a Suspended request queues and the worker refuses it" in the BUG-324 row and
     the 6a-2 plan.
2. **note**: doc drift from `8956277a`.
   - `MockWorkerTenantService.java:39-41` still says "the single Active Workspace, else the request's ... Workspace",
     but the order is now bound first.
   - `WorkerTenantService.java:11-14` defines `enqueueWorkspaceId` for "a caller on an unbound thread", which the new
     bound branch goes beyond.
   - `OutboxOnlyConfiguration.java:97-98` says "the same rule as `MockWorkerTenantService.enqueueWorkspaceId`", but
     the outbox returns a bound id without the filter.
   - Fix together with issue 1.
3. **note**: the review record `kan-384-one-tenant-holder-review.md` says "Both findings fixed" and "No second review",
   and `2d3fb18f` set `one-tenant-holder` to passing. Both conflict with this verdict. Proposed status: `fixing`, not
   changed here because the card does not say so.
4. **note**: attempt-1 notes 3, 4, 5, 7 and 8 are still open for 6a-2:
   - the 2-arg constructor at `MockWorkerTenantService.java:20-22`;
   - the unbound branch of `TenantJobServiceImpl.java:37-41` is not covered;
   - the trace paths;
   - the `WorkerTenantService.java:7` comment;
   - the `.env.example` note.

## Assumptions made

- Attempt 1 judged finding 2 by its effect ("queues a job that the worker refuses"), so I judged the fix by that
  effect, not only by the method's unit tests. I kept the severity at should-fix because no data crosses Workspaces and
  the worker refuses the job.
- The card allows only this review file, so there is no separate test report. The test evidence is below.

## Test Report

- Developer's verify, from the card and PR #320: `gh pr checks 320` shows that on `8063b119` both runs (CI and PR
  checks) completed with success. On `2d3fb18f` (harness only) all checks pass except `backend-tests (it-other)`, which
  was still in progress when read.
- My runs:
  - `sdd.ts trace platform STORY-002` (above).
  - `mvn -o -q dependency:build-classpath` to a scratchpad file.
  - `javac` + `java` of `SuspendedRequestProof` in the session scratchpad against `backend/target/classes`: exit 0,
    output quoted in issue 1.
- No Maven test run and no full suite: the card asks for targeted proof only, and the existing tests are green in CI.
  The finding is about a path that they do not cover.

---

# Attempt 3 (range `2d3fb18f..b386ac2d`)

Commits: `8ba76c08` (tests) and `b386ac2d` (fix). The diff holds 8 files under `backend/src`: 5 main and 3 test.
It has no config, yml, DB, routing or harness change. HEAD `b386ac2d` is the pushed branch head. The worktree is
clean except this file.

## Summary

Verdict **pass**. The attempt-2 should-fix is resolved at both callers. No critical and no should-fix finding remains.
There are 5 notes; none blocks.

## Card checks

1. **Both callers: resolved.**
   - `TenantJobServiceImpl.java:34-38`: on a bound thread, an empty `enqueueWorkspaceId()` throws
     `IllegalStateException("Enqueue requires an active workspace")` before `enqueue`, so no job is stored.
   - `ReviewNotificationEnqueuer.java:124-138`: a missing `WorkerTenantService` (the staging web process) still runs
     `enqueue` directly, unchanged. Otherwise `enqueueWorkspaceId()` is read first. When it is empty, the enqueuer logs
     `(no Workspace to bind)` and returns. When it is present, a bound thread runs directly and an unbound thread uses
     `runIn`.
   - Active bound Workspace: `MockWorkerTenantService.java:49` returns the bound id, so one job or email is queued.
   - Unbound thread: same as `2d3fb18f` and `main`. `enqueueWorkspaceId` follows the single-Active rule, and empty
     throws `Enqueue requires a bound workspace` or warns.
   - Non-request bound thread (a job thread): `:49` returns the bound id unfiltered when
     `RequestContextHolder.getRequestAttributes() == null`, so this path is not affected. Covered by
     `WorkerJobTenantBindingTest.anAfterCommitEnqueueQueuesForTheRequestsWorkspace` (`:186`, Active A) and
     `ReviewNotificationEnqueueBugTest.enqueuesDirectlyOnAnAlreadyBoundThread_BUG_164` and
     `prefersTheBoundWorkspace_BUG_164`.
2. **Production effect: acceptable.**
   - `TenantJobServiceImpl` is a bean only when `tsolve.worker.enabled=true` (`WorkerConfiguration.java:20,55`). Its
     only `WorkerTenantService` bean is the `worker-mock` one (`WorkerMockConfiguration.java:15`). So the throw is
     reachable only on local and demo stacks. Staging has no `WorkerTenantService` bean and takes the
     `context == null` branch of the enqueuer.
   - User-visible effect on those stacks, for a request bound to a Workspace that is Suspended in the mock list or
     not in it:
     - Wiki upload: still succeeds. The log has `Could not enqueue wiki-index` at ERROR
       (`WikiUploadServiceImpl.java:195-199`, BUG-295). The document stays `PENDING`.
     - Wiki restore: the exception leaves `afterCommit` (`WikiTrashServiceImpl.java:115` has no try/catch). The
       restore request returns an error although the restore is committed (note 4).
     - Review email: one WARN, no email.
   - Compared with `2d3fb18f`, the job was queued there and failed later at `TenantExecutionServiceImpl.java:24`, with
     the same `PENDING` result. `main` already threw `Enqueue requires a bound workspace` here: its request branch
     filtered by `active`. With one Active Workspace, `main` instead queued the job for that other Workspace.
   - So the change restores `main`'s refusal without the cross-Workspace guess. It matches spec
     `platform/spec.md:52-53`: a Suspended Workspace's site is refused (MSG150) and its jobs do not run. In the real
     platform such a request never gets past the site check.
3. **Tests: they prove the fix and weaken nothing.**
   - The 4 BUG_324 refusal tests were red before the fix, as the QA hand-off `KAN-384-fix-3-qa.md` line 3 records:
     "expected 0 stored jobs, got 1" and "outbox.queue was called".
     - `TenantJobServiceImplTest.java:114-161` uses a real `ThreadLocalTenantContext`, a real `MockWorkerTenantService`
       and a request attribute, and asserts 0 stored jobs.
     - `ReviewNotificationEnqueuerTest.java:189-222` asserts `never().queue` for Suspended and unlisted, and
       `times(1)` for Active.
   - The 7 stubs (`bindActiveWorkspace`, `:67-70`) return the same id from `currentWorkspaceId` and
     `enqueueWorkspaceId`, which models an Active bound Workspace. Compared with `main` and `2d3fb18f` (7
     `currentWorkspaceId` stubs), no assertion or `verify` changed.
   - BUG-164 test against `main` (`git show main:…ReviewNotificationEnqueueBugTest.java:219-227`):
     - `verify(outbox).queue` is now `times(1)`, the same check.
     - `verify(tenants).runIn(any, any)` is now `times(1)`, the same check. The only `runIn` is the test's own bind,
       so a re-bind inside the enqueuer still fails the test.
     - New: `boundWhenQueued` must be exactly `[B]`, which is stronger.
     - Dropped: `never().enqueueWorkspaceId()`, which contradicts the fix.
     - The test's intent, "queue directly on the bound Workspace without re-binding", is still checked.
4. **Javadocs: match the code.**
   - `MockWorkerTenantService.java:39-43`: bound first, filtered only on a request thread, else the single Active one.
   - `WorkerTenantService.java:11-15`: callers queue no job when it is empty.
   - `OutboxOnlyConfiguration.java:96-102`: the outbox does not check a bound id against the Active list, which is
     true (`:113-115`). Note 1 covers the line length.
5. **Nothing else new.** No config, DB or routing change. No 6a-2 work leaked in: the `ponytail` at
   `MockWorkerTenantService.java:48` still defers the request check to 6a-2, and the 2-arg constructor is still there.

## Issues

1. **note**: `backend/src/main/java/vn/tsolve/worker/config/OutboxOnlyConfiguration.java:99` is 186 columns. The
   limit in `backend/AGENTS.md:229` is 120; Spotless passes only because Palantir does not reflow Javadoc. The line
   also splits `{@code MockWorkerTenantService` / `.enqueueWorkspaceId}`. Fix: reflow the paragraph.
2. **note**: `TenantJobServiceImplTest.java:124-128` and `:149-153` catch any `RuntimeException`, so they do not pin
   the refusal. Fix: `assertThatThrownBy(...).isInstanceOf(IllegalStateException.class).hasMessage("Enqueue requires
   an active workspace")`, and keep the count of zero.
3. **note (6a-2)**: `WorkerTenantService.java:16` defaults `enqueueWorkspaceId()` to empty. Both callers now ask it on
   a bound thread, so any implementation that does not override it refuses every request enqueue: the job service
   throws and review emails are dropped. Today only the mock is a bean. The real platform implementation must return
   the bound Active id, and its test should say so.
4. **note**: `backend/src/main/java/vn/tsolve/wiki/service/impl/WikiTrashServiceImpl.java:112-117`: restore calls
   `enqueueFromRequest` in `afterCommit` with no try/catch, unlike upload (BUG-295). Any enqueue refusal turns a
   committed restore into an error response. This is older than this PR (`main` throws there too) and is outside its
   scope. Proposed: a `docs/bug-log.md` row.
5. **note (6a-2)**: `backend/src/main/java/vn/tsolve/tracker/service/impl/WorkerSuggestionJobQueue.java:42-43` is a
   third caller with the old shape: when bound, it calls `jobs.enqueue` directly and does no Active check. This
   matters since 6a-1 made the request Workspace visible there; on `main` this path used
   `TSOLVE_TRACKER_DEV_WORKSPACE_ID`. A Suspended request queues a suggestion job that the worker refuses (GBR-38).
   It is not part of BUG-324 or of this card. Decide in 6a-2 whether it gets the same check.

Still open from attempts 1 and 2 (for 6a-2): the 2-arg constructor, the trace paths, the `.env.example` note, and the
config ids that must be named in `TSOLVE_STUB_WORKSPACES`. Attempt-2 note 3 (the status set to passing early) is
answered by this verdict.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace platform STORY-002`: 4/25, unchanged from attempt 2 (AC-002.12, .13, .14 and .16
✓). AC-002.11 and .15 show ✗ because of the trace paths (attempt-1 note 5). The BUG_324 tests are named by bug id, not
by an AC.

## Assumptions made

- The card asks whether the throw is acceptable. I judged it against `main` and spec `platform/spec.md:52-53`, not
  against `2d3fb18f`, where the job was queued and refused later.
- Status: I propose `one-tenant-holder` → passing and did not change it (the card does not say so).

## Test Report (attempt 3)

- Developer's verify, from the card: `mvn spotless:check test` on 20 unit classes: 221 run, then the one BUG-164
  failure was fixed. The 3 changed classes ran 42 tests with 0 failures. The ITs ReviewNotificationIT, EmailOutboxIT,
  WebJobsIT and JobRunrTenantIT ran 30 tests with 0 failures. All of this ran on uncommitted code. The committed diff
  (8 files) matches the files the hand-offs name.
- `gh pr checks 320` on `b386ac2d`: "no checks reported" at review time. CI had not registered, and the card says not
  to wait.
- My runs: `sdd.ts trace platform STORY-002`, plus `git diff`/`git show` against `main` and `2d3fb18f`. No Maven run:
  no finding needed proof by test.
