---
module: ticket-review
feature: review-email-link-fallback
task: 21
verdict: fail
attempt: 1
date: 2026-11-14
reviewer: reviewer
iteration: 1
---

# Review: ticket-review — Task 21: review-email-link-fallback

## Summary

Commit `e9eec864` fixes both task 20 findings it was meant to fix.

- **BUG-223:** `ReviewNotificationEnqueuer` now looks up the site host once per event, inside the bound Workspace. A lookup that throws counts as a missing host, so every email is still queued, without the button and the link (AC-010.8).
- **BUG-224:** the misleading test is renamed `continuesAfterAFailedQueueCall_BUG_164`.

The new regression test would fail against the old code. No STORY-010 criterion is broken. The worker unit tests (115) and Spotless pass locally, and `ReviewNotificationIT` passed 16/16 in CI on a head that contains this commit.

**The verdict is fail for one reason: the backend CI on that head is red.** `backend-tests (it-other)` of PR #233 (run `43602465544`, head `2baa4282`) ends with `BUILD FAILURE`, `Tests run: 536, Failures: 0, Errors: 1`. The erroring test is `EmailOutboxIT.recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221` (worker module, commit `c5bfbaee`), not code from this feature. The rule is: any failing test gives verdict fail, and no pass on a red build. The code of this feature is otherwise ready to pass. See "Assumptions made" 1.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Both findings fixed; AC-010.1–010.9 still met (Stage 1 below) |
| Completeness | pass | 9/9 criteria have a test; the AC-010.8 fix has its own test named with AC and bug id; no EXTRA behaviour |
| Correctness | fail | Local: 115/115 worker unit tests, Spotless clean. CI on head `2baa4282`: `it-other` fails with 1 error in `EmailOutboxIT` (outside this diff); `ReviewNotificationIT` 16/16 passes |
| Integration | pass | Same interfaces as before (`WorkspaceSiteService.siteHost`, `EmailOutboxService.queue`, `WorkerTenantService`); signature change only of a private method |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-010.1 after commit, in the decision's Workspace | PASS | `ReviewNotificationEnqueuer.java:56`: the lookup is the first statement of the `enqueue` Runnable, so on an unbound thread it runs inside `context.runIn(workspaceId, enqueue)` (`:82`) and reads `currentWorkspaceId()` of the bound Workspace; on a bound thread it reads that Workspace directly (`:73-74`) |
| 2 | AC-010.2 compose and queue per writer | PASS | Unchanged except that `render` takes the resolved host (`:105-124`); `passesRenderedContentAndTicketLink_AC_010_1_AC_010_2_AC_010_8` still asserts the exact queued email |
| 3 | AC-010.3 failure never reaches the API response | PASS | The lookup has its own `catch (RuntimeException)` (`:100-102`) inside the outer catch (`:84-91`); `onDecided` cannot throw. `continuesAfterQueueOrLookupFailure…_AC_010_3…` passes |
| 4 | AC-010.4 SMTP retries | PASS | Not touched; `ReviewNotificationIT` (CI) 16/16 |
| 5 | AC-010.5 no writer, others still queued | PASS | Not touched; unit test passes |
| 6 | AC-010.6 no private data in logs | PASS | The new catch logs nothing (`:100-102`); existing log lines unchanged. `logsTheCountAndExceptionClassButNeverTheMessage_BUG_164` passes |
| 7 | AC-010.7 HTML layout | PASS | Renderer untouched |
| 8 | AC-010.8 link, or no button and no link when the address cannot be built | PASS | Host null leads to `link = null` (`:111`). Null, empty and blank host: `omitsTicketLinkWhenSiteUnavailable_AC_010_8`. Throwing lookup: new `queuesEveryDecisionWithoutLinksWhenSiteLookupThrows_AC_010_8_BUG_223` asserts 3 emails queued, exactly one `siteHost` call, and no `https://` or `/tickets/` in `bodyText`, and no `<a `, `https://` or `/tickets/` in `bodyHtml` |
| 9 | AC-010.9 escaping, subject line breaks | PASS | Untouched; `queuesSubjectWithoutLineBreaks_AC_010_9` passes |

**The regression test is real.** Before the commit, `render` called `sites.siteHost` per decision. The exception was caught by the per-decision `catch` (old lines 57-68), so the old code makes 0 queue calls and 3 lookups. Both `verify(outbox, times(3))` and `verify(sites).siteHost(workspace)` (which means exactly once) fail against it. The bug fix and the test are in one commit, so this was checked by reading the old code, not by running it.

**Bug log** (`docs/bug-log.md:237-238`). BUG-223's summary, steps (one lookup expected; lookup per decision and no email actually) and fix description match the code and the test name. BUG-224 is a rename only: the assertions are unchanged, and the BUG-164 row (`:180`) now lists the new name. No other reference to the old name remains in `backend/` or `docs/`. The "Fixed in" column of both rows is wrong (issue 3).

## Traceability

```
  ✓ AC-010.1  ReviewNotificationEnqueuerTest, ReviewNotificationIT
  ✓ AC-010.2  ReviewNotificationEnqueuerTest, ReviewEmailRendererTest, ReviewNotificationIT
  ✓ AC-010.3  ReviewNotificationEnqueuerTest, ReviewNotificationIT
  ✓ AC-010.4  ReviewNotificationIT
  ✓ AC-010.5  ReviewNotificationEnqueuerTest, ReviewNotificationIT
  ✓ AC-010.6  ReviewNotificationEnqueuerTest, ReviewNotificationIT
  ✓ AC-010.7  ReviewEmailRendererTest, ReviewNotificationIT
  ✓ AC-010.8  ReviewNotificationEnqueuerTest, ReviewEmailRendererTest
  ✓ AC-010.9  ReviewNotificationEnqueuerTest, ReviewEmailRendererTest
trace ticket-review (STORY-010): 9/9 acceptance criteria have a test
```

## Issues

Blocking:

1. **CI red on the head that contains this commit.** `backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java:269` — `recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221` times out after 60 s. The row stays `status=PENDING`, `attempt_count=0`, `claimed_at=null`, so the recurring `worker-email-outbox` job never claimed it in the test context. This is the first CI run of that test (BUG-221 says "written and compiled only"). It belongs to worker task 6, not to this diff.
   Fix (worker owner): make the test pass or correct the recurring registration, then re-run PR #233. Once `it-other` is green, re-evaluate this feature as attempt 2. No change to `e9eec864` is needed for that.

Informational (do not block). Paths are under `backend/src/`.

2. `main/java/vn/tsolve/worker/service/impl/ReviewNotificationEnqueuer.java:100-102` — a throwing site lookup is swallowed without any log line, so an operator cannot see why every email of the batch lacks its button.
   Fix: log one WARN per event with the exception's simple class name only, as the other catches do (AC-010.6 safe).
3. `docs/bug-log.md:237-238` — the "Fixed in" column of BUG-223 and BUG-224 says `Uncommitted`, but the fix is committed in `e9eec864` and is in PR #233. BUG-220 to BUG-222 have the same problem.
   Fix: set it to `PR #233`.
4. `test/java/vn/tsolve/worker/service/impl/ReviewNotificationEnqueuerTest.java:121-140` — the throwing-lookup case covers only the bound-thread path. No test proves that the lookup reads the Workspace bound by `runIn` on the unbound path: every `ReviewNotificationEnqueueBugTest` case uses a `WorkspaceSiteService` mock that returns empty. It is correct by reading (`:56` is inside the Runnable).
   Fix: one case with `MockWorkerTenantService(Set.of(A))`, unbound, asserting `siteHost(A)` and the link.
5. `ReviewNotificationEnqueuerTest.java:127-129` — a `for` loop without braces (style only; Spotless accepts it).
6. Task 20 informational findings 4–8 (AFTER_COMMIT work on the request thread, the several-Workspace drop, wasted work when `context == null`, renderer-derived expectations, inline fully qualified annotations) are unchanged. They were not in this task's scope.

No security findings. The diff adds no log output, endpoint, dependency or user-supplied input path. The link is still built only from the catalog host and the ticket UUID.

## Assumptions made

These are judgment calls the reviewer could not put to the user. Please confirm or correct them.

1. **A red CI test outside the feature blocks this feature.** The evaluate rules ("any failing test → verdict fail") and the dispatcher's brief (no pass on a red or pending CI) were applied literally, even though the erroring test (`EmailOutboxIT`, worker BUG-221) was not changed by `e9eec864` and the feature's own `ReviewNotificationIT` passed. If the user rules that a worker-module failure should not block ticket-review, this review can be read as a pass on the code: all criteria and both fixes are verified.
2. **Swallowing a throwing lookup silently** is accepted under AC-010.8 ("the site address cannot be built"). The spec names only "no domain configured or no Workspace slug"; the task 21 description extends it to a throwing lookup, and no criterion changed.
3. **Regression evidence by reading:** the BUG-223 test was not run against the reverted code, because that would change the working tree. The before-fix behaviour is derived from the removed lines of the diff.
4. **Coverage:** no tool is configured and the spec sets no threshold, so coverage was not treated as a failure.

## Test Report

Full detail: `task-21-review-email-link-fallback-test-report.md`.

- Local, `backend/`: `mvn -q -Dtest='vn.tsolve.worker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` exits 0 with 115 tests, 0 failures, 0 errors and 0 skipped (`ReviewNotificationEnqueuerTest` 10, `ReviewNotificationEnqueueBugTest` 9). `mvn -q spotless:check` exits 0.
- CI, PR #233, head `2baa42820e1ab8644be5bb43ff3953465da61425`. It contains `e9eec864`, and `git diff e9eec864 2baa4282 -- backend` is empty. unit, it-keycloak-a, it-keycloak-b, gitleaks and pr-title pass. **it-other fails**: `Tests run: 536, Failures: 0, Errors: 1`, `BUILD FAILURE`, from `EmailOutboxIT.recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221`. In the same job `ReviewNotificationIT` ran: `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`.
- Not run: the full backend suite locally (Docker is off), any coverage tool, any dependency audit.
