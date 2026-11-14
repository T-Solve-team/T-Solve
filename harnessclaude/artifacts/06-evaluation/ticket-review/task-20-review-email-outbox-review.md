---
module: ticket-review
feature: review-email-outbox
task: 20
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: reviewer
iteration: 1
---

# Review: ticket-review — Task 20: review-email-outbox

## Summary

Commit `5cf5c10b` moves the STORY-010 review email from a per-decision JobRunr job to the worker email outbox: `ReviewNotificationEnqueuer` now reads each committed decision after commit, renders subject, plain text and HTML with `ReviewEmailRenderer`, and queues one `OutboundEmail` per writer. `ReviewNotificationJobHandler` and its test are deleted.

All nine acceptance criteria are met by the code and each has a test that checks it. Local worker unit tests (106) and Spotless pass; the full backend suite and `ReviewNotificationIT` (16 tests) passed in the CI of PR #233 on the head commit that contains this change. No CRITICAL code finding and no Critical or High security finding. Eight informational findings and two Low security notes are listed below; none blocks.

Two points are verified less strongly than the rest: "in the decision's Workspace" (AC-010.1) is proven only against the mock tenant port, the only one that exists; and everything that needs a real database is proven only by CI, not on this machine.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-010.1–010.9 checked one by one against the code (table below) |
| Completeness | pass | 9/9 criteria have a test that names and checks them; no behaviour outside the spec |
| Correctness | pass | Local: 106 worker unit tests, 0 failures; Spotless clean. CI (PR #233, head `29405ede`): all backend parts pass, `ReviewNotificationIT` 16/16 |
| Integration | pass | Uses only `EmailOutboxService.queue(OutboundEmail)`, `ReviewNotificationService` and `WorkspaceSiteService` interfaces; no remaining reference to the removed handler type in `src/main` |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-010.1 after commit, in the Workspace, one email per decided ticket; none for Approve | PASS | `ReviewNotificationEnqueuer.java:46-82`; `TicketDecisionServiceImpl.java:100-104` publishes only for non-Approve. IT: `enqueuesOneEmailPerCommittedDecision`, `enqueuesNothingForApprove`, two rollback tests |
| 2 | AC-010.2 load committed data, compose English email to the writer, HTML + plain, subjects | PASS | `ReviewNotificationEnqueuer.java:93-117`; IT `queuesTheStoredDecisionToTheWriter` compares the stored row; subjects asserted literally in `ReviewEmailRendererTest` and `ReviewNotificationEnqueuerTest:115`. Sender display name is worker AC-001.1, not in this commit |
| 3 | AC-010.3 queue failure or disabled queueing leaves the decision and response unchanged | PASS | `:50-53` (no bean), `:57-68`, `:83-90`; unit `skipsAbsentOutbox`, IT `keepsTheDecisionWhenEnqueueFails…` (returns 1, ticket REJECTED) |
| 4 | AC-010.4 SMTP failure retried by the outbox, decision unaffected | PASS | No delivery code left in this feature; IT `retriesAndFailsQueuedMailWithoutChangingDecision` (3 attempts → FAILED, decision and ticket rows unchanged). CI only |
| 5 | AC-010.5 no writer account → no email, others still queued | PASS | Inner join on `app_user` gives an empty Optional; `:58-60`. IT `sendsNothingForApproveMissingDecisionOrNoWriter` |
| 6 | AC-010.6 no recipient, reason or comment in logs | PASS | Three log statements (`:63`, `:78`, `:85`) carry counts and `getClass().getSimpleName()` only, no throwable. Unit and IT capture output; `ReviewNotificationEnqueueBugTest:169-172` asserts no throwable attached |
| 7 | AC-010.7 HTML layout | PASS | Template and renderer unchanged from KAN-333; `ReviewEmailRendererTest` plus IT reviewer-name cases through the outbox row |
| 8 | AC-010.8 link to Ticket Detail; none when the site cannot be built | PASS | `:99-103`; unit `passesRenderedContentAndTicketLink` (exact URL), `omitsTicketLinkWhenSiteUnavailable` (null, empty, blank host) |
| 9 | AC-010.9 HTML-escape stored values, keep line breaks, no line break in subject | PASS | `ReviewEmailRenderer.java:38-40, 51-58`; `escapesStoredValuesAndPreservesLineBreaks`, `keepsTemplateSyntaxInStoredValuesLiteral`, `queuesSubjectWithoutLineBreaks` |

No MISSING, no EXTRA.

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

CRITICAL (blocks pass): none.

Informational (does not block). Paths are under `backend/src/`.

1. `test/java/vn/tsolve/worker/service/impl/ReviewNotificationEnqueueBugTest.java:145` — `stopsAtTheFirstFailedEnqueue_BUG_164` now asserts three queue calls, the opposite of its name; `:48-49, 102, 119` keep `scheduler` and `handler` mocks that the enqueuer no longer touches, so `verifyNoInteractions(scheduler, …)` proves nothing.
   Fix: rename to `continuesAfterAFailedQueueCall_BUG_164` and drop the unused mocks from the enqueuer cases.
2. `main/java/vn/tsolve/worker/service/impl/ReviewNotificationEnqueuer.java:99-102` — the site host is looked up once per decision inside `render`, so a 500-ticket batch makes 500 identical catalog lookups.
   Fix: resolve the host once per event, before the loop.
3. Same lines — if `WorkspaceSiteService.siteHost` throws, the per-decision catch drops the whole email. AC-010.8 asks for an email without the button when the address cannot be built; a lookup failure is not named there, so this is not a spec breach, but the writer then gets nothing.
   Fix: catch the lookup failure and render without the link, or state in the spec that a lookup failure sends no email.
4. `ReviewNotificationEnqueuer.java:46-82` — reads, rendering and all Jobs-database inserts run on the request thread in AFTER_COMMIT, before the HTTP response returns. In that phase `findNotification`'s `@Transactional(readOnly = true)` joins the already-committed transaction's still-bound resources (it does not open "its own short transaction", as design "Reading after commit" says), so the business connection is held for the whole loop. Reads work (the IT proves it); the cost is response time and connection hold time on large batches or a slow Jobs database.
   Fix: none needed for the demo; correct the design sentence, and consider `REQUIRES_NEW` or an async hand-off if batch latency matters.
5. `ReviewNotificationEnqueuer.java:72-81` — with a tenant port, no bound Workspace and more than one Active Workspace, every email of the event is dropped with one WARN. This is the BUG-164 behaviour kept as is; the only `WorkerTenantService` in `src/main` is `MockWorkerTenantService`, whose `enqueueWorkspaceId` picks the single allow-listed Workspace rather than the request's Workspace. See "Assumptions made".
6. `ReviewNotificationEnqueuer.java:54, 72` — when no `WorkerTenantService` bean exists the loop runs unbound, and `EmailOutboxServiceImpl.queue` then rejects every email (no bound Workspace). Harmless (counts logged), but the reads and rendering are wasted work.
   Fix: return early when `context == null`.
7. `test/java/vn/tsolve/worker/service/impl/ReviewNotificationEnqueuerTest.java:77-99` and `ReviewNotificationIT.java:249-272` — expected content is produced by calling the same renderer, so these tests prove wiring, not content. Content and escaping are asserted literally only in `ReviewEmailRendererTest`. Adequate, but no test pushes hostile HTML through the enqueuer path.
   Fix: add one enqueuer case with `<script>` in title and feedback asserting the queued `bodyHtml` has no `<script>`.
8. `ReviewNotificationEnqueuerTest.java:102-103`, `ReviewNotificationIT.java:84` — fully qualified annotation names inline instead of imports (style only; Spotless accepts it).

### Security Review — ticket-review / review-email-outbox — 2026-11-14

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 1 |
| STRIDE | 0 | 0 | 0 | 1 |

- **HTML escaping at composition:** every placeholder value, fixed or stored, passes through `HtmlUtils.htmlEscape` in `ReviewEmailRenderer.java:51` (escapes `& < > " '`), including the `href` value. Conditional sections are resolved on the template before any stored value is inserted and each placeholder is replaced once, so stored text holding `{{…}}` or `$1` stays literal (tested). The link is built from the catalog site host and a UUID, not from user text. The plain-text part is correctly not escaped.
- **Header injection:** CR and LF in the subject become spaces (`:38-40`); the outbox also rejects a subject with a line break (worker AC-004.2).
- **Logs:** only counts and exception class names; no throwable is passed to the logger, so no message or cause text with mail or SQL values is printed. Verified by output capture in unit tests and the IT.
- **Secrets:** no credential pattern in the diff; tracked env files are only `.env.example` and `frontend/.env.example`; CI `gitleaks` passes.
- **Dependencies:** `pom.xml` is unchanged by the commit. No audit tool was run (none is set up for Maven here).
- **Authorization:** no new endpoint; the listener acts only on ids published by `TicketDecisionServiceImpl.decide` inside its own transaction.

Findings:
1. **[LOW][CODE]** `ReviewNotificationEnqueuer.java:115-116` — a subject over 255 characters (a very long source ticket key) is rejected by the outbox, so that writer gets no email and only a count is logged. Fix: truncate the key in the subject, or accept as is.
2. **[LOW][STRIDE: information disclosure]** Recipient address, reason and comment are now stored in the Jobs database `email_outbox` until retention deletes them. Worker spec NFR-5 and AC-004.10 allow this explicitly; recorded so the data owner knows where the text now rests.

## Assumptions made

These are judgment calls the reviewer could not put to the user; please confirm or correct.

1. **"In the decision's Workspace" (AC-010.1)** is accepted as met by the existing binding rule: use the bound Workspace, otherwise the tenant port's `enqueueWorkspaceId`. Only the mock port exists, so this is not proven for a deployment with several Active Workspaces, where the current code would queue nothing (finding 5). Treated as the known BUG-164 limitation of the platform adapter, outside this task.
2. **A site-host lookup that throws** sends no email (finding 3). Treated as outside AC-010.8, which names only "no domain configured or no Workspace slug".
3. **Coverage:** no coverage figure exists and the spec sets no threshold; not treated as a failure.
4. **Old `review-notification` jobs** already in JobRunr storage will fail as unknown handlers. The design and `backend/README-worker.md` call this acceptable for demo data; accepted on that basis.
5. **CI as evidence:** Docker is off locally, so AC-010.4 and the database-backed parts of AC-010.1–010.3, 010.5 and 010.7 rest on the CI run of PR #233 (run `43600347361`, head `29405ede`), not on a local run.
6. **Scope:** the outbox, dispatcher and SMTP sender (worker STORY-004, AC-001.1 display name) were not reviewed here; another reviewer has them.

## Test Report

Full detail: `task-20-review-email-outbox-test-report.md`.

- Local, `backend/`: `mvn -q -Dtest='vn.tsolve.worker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` → exit 0, 106 tests, 0 failures, 0 errors, 0 skipped (11 classes). `mvn -q spotless:check` → exit 0.
- CI, PR #233, head `29405ede5570b51ae21b0b4365a31f15859938d6` (contains `5cf5c10b`; no backend change after it): backend-tests unit, it-keycloak-a, it-keycloak-b and it-other all pass; gitleaks and pr-title pass.
- `ReviewNotificationIT` in job `132463579534`: `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`; the part ended `Tests run: 533, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Not run: the full backend suite locally, any coverage tool, any dependency audit.
