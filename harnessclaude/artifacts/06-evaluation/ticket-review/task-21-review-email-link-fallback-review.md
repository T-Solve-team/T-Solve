---
module: ticket-review
feature: review-email-link-fallback
task: 21
verdict: pass
attempt: 2
date: 2026-11-14
reviewer: reviewer
iteration: 1
---

# Review: ticket-review — Task 21: review-email-link-fallback

## Summary

Attempt 2. Attempt 1 failed for one reason only: the backend CI of PR #233 was red (`backend-tests (it-other)`, 1 error in `EmailOutboxIT.recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221`, worker task 6). That is now resolved. On head `cd8186b4` all backend jobs pass, `it-other` reports `Tests run: 536, Failures: 0, Errors: 0` and `BUILD SUCCESS`, and `ReviewNotificationIT` ran 16/16 with no failures.

The code of this feature is still commit `e9eec864`. `git diff e9eec864..HEAD --stat` on `ReviewNotificationEnqueuer.java` and `ReviewNotification*` tests is empty. The only backend changes since then are test-only (`EmailOutboxIT.java` +1 property `spring.mail.host` and a single-bean assertion; `WorkerMailpit.java` +`host()`), from `e6570be1` (BUG-225). No production code changed, so **attempt 1's code conclusions are reused here unchanged** (Stage 1 table below, same line numbers).

Bug log rows BUG-223 and BUG-224 (`docs/bug-log.md:237-238`) now show "Fixed in" `PR #233 (e9eec86)` and still match the code and test names. Attempt 1's issue 3 is resolved for these rows.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Both task-20 findings fixed; AC-010.1–010.9 met (reused from attempt 1, code unchanged) |
| Completeness | pass | 9/9 criteria have a test (trace re-run); no EXTRA behaviour |
| Correctness | pass | Local: 115/115 worker unit tests, Spotless clean. CI on `cd8186b4`: unit, it-keycloak-a, it-keycloak-b, it-other all pass; `ReviewNotificationIT` 16/16, `EmailOutboxIT` 9/9 |
| Integration | pass | Interfaces unchanged (`WorkspaceSiteService.siteHost`, `EmailOutboxService.queue`, `WorkerTenantService`) |

### Stage 1 — spec compliance (reused from attempt 1; `ReviewNotificationEnqueuer.java` unchanged since `e9eec864`)

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-010.1 after commit, in the decision's Workspace | PASS | Lookup is first statement of the `enqueue` Runnable (`ReviewNotificationEnqueuer.java:56`), run inside `context.runIn(workspaceId, enqueue)` (`:82`) or on the bound thread (`:73-74`) |
| 2 | AC-010.2 compose and queue per writer | PASS | `render` takes the resolved host (`:105-124`); exact-email test passes |
| 3 | AC-010.3 failure never reaches the API response | PASS | Inner lookup catch (`:100-102`) inside outer catch (`:84-91`) |
| 4 | AC-010.4 SMTP retries | PASS | Not touched; `ReviewNotificationIT` 16/16 on CI `cd8186b4` |
| 5 | AC-010.5 no writer, others still queued | PASS | Not touched; unit test passes |
| 6 | AC-010.6 no private data in logs | PASS | New catch logs nothing; existing logs unchanged |
| 7 | AC-010.7 HTML layout | PASS | Renderer untouched |
| 8 | AC-010.8 link, or no button/link when address cannot be built | PASS | Null host → `link = null` (`:111`); `queuesEveryDecisionWithoutLinksWhenSiteLookupThrows_AC_010_8_BUG_223` asserts 3 queued, one lookup, no link in text or HTML |
| 9 | AC-010.9 escaping, subject line breaks | PASS | Untouched; `queuesSubjectWithoutLineBreaks_AC_010_9` passes |

### Regression check (attempt 2)

- `e6570be1` touches only `backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java` and `WorkerMailpit.java` — test setup for the worker recurring-job IT. It cannot affect this feature's behaviour. All 536 it-other tests and the unit and Keycloak jobs pass on `cd8186b4`.
- `cd8186b4` touches only `docs/bug-log.md` and harness files.
- Local worker unit test counts are identical to attempt 1 (115; `ReviewNotificationEnqueuerTest` 10, `ReviewNotificationEnqueueBugTest` 9).

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

Blocking: none. Attempt 1's blocking issue 1 (red CI) is resolved on `cd8186b4`.

Informational (carried over from attempt 1, still open, do not block). Paths are under `backend/src/`.

1. `main/java/vn/tsolve/worker/service/impl/ReviewNotificationEnqueuer.java:100-102` — a throwing site lookup is swallowed without any log line. Fix: one WARN per event with the exception's simple class name only (AC-010.6 safe).
2. `test/java/vn/tsolve/worker/service/impl/ReviewNotificationEnqueuerTest.java:121-140` — the throwing-lookup case covers only the bound-thread path; no test proves the lookup reads the Workspace bound by `runIn` on the unbound path. Fix: one case with `MockWorkerTenantService(Set.of(A))`, unbound, asserting `siteHost(A)` and the link.
3. `ReviewNotificationEnqueuerTest.java:127-129` — `for` loop without braces (style only).
4. Task 20 informational findings 4–8 remain unchanged and out of scope.

Resolved since attempt 1: issue 3 (bug-log "Fixed in" for BUG-223/224 now `PR #233 (e9eec86)`).

No security findings; no new log output, endpoint, dependency or input path.

## Assumptions made

These are judgment calls the reviewer could not put to the user. Please confirm or correct them.

1. **Reuse of attempt 1's code review:** the production and test files of this feature are byte-identical to `e9eec864` (empty `git diff --stat`), so the Stage 1 analysis was not repeated line by line.
2. **Swallowing a throwing lookup silently** is accepted under AC-010.8 ("the site address cannot be built"), as in attempt 1. The spec names only "no domain configured or no Workspace slug".
3. **Regression evidence for BUG-223 by reading** (not by running reverted code), as in attempt 1.
4. **Coverage:** no tool is configured and the spec sets no threshold, so it was not treated as a failure.

## Test Report

Full detail: `task-21-review-email-link-fallback-test-report.md`.

- Local, `backend/`: worker unit tests exit 0, 115 tests, 0 failures/errors/skipped. `mvn -q spotless:check` exits 0.
- CI, PR #233, head `cd8186b484fee04ecaf6778e988d2793a471b798`, run `43603714210` (conclusion success): unit, it-keycloak-a, it-keycloak-b, it-other, backend, changes, frontend, gitleaks, pr-title pass. it-other log: `ReviewNotificationIT` 16/0/0/0, `EmailOutboxIT` 9/0/0/0, total `536, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
- Not run: full backend suite locally (Docker off), coverage, dependency audit.
