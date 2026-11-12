---
module: ticket-repository
feature: jira-import-verify
task: 10
verdict: pass
attempt: 1
date: 2026-11-08
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-repository — Task 10: Trace and end-to-end check

## Summary
Task 10 asks for three things: `trace` shows a test for every AC, `mvn verify` and the frontend checks pass, and one
manual run against a real Jira site is recorded in the session log. I re-ran the first two myself and both hold.
`trace` is 40/40, and the sampled tests check their criteria, not just name them. Backend: 981 unit + 220 IT tests,
0 failures, Spotless exit 0. Frontend: format, lint (0 errors), typecheck, 1480/1480 tests and build all pass, and
`generate:api` leaves no diff. The real-Jira run was **not done**: the session log
(`05-implementation/ticket-repository/task-10-jira-import-verify.md:22-27`) says there are no credentials on this
machine and the user chose to skip it on 2026-11-08.

**Does the skip make the feature fail? No.** The evaluation is against the spec. The spec's Definition of Done
(`spec.md:148-152`) has four items: an AC-named test for every criterion, `mvn verify` with Spotless, the frontend
checks with no generate diff, and the Jira variables in `.env.example` with no real value. All four are met and
checked here. No acceptance criterion requires a live run. Each criterion is a statement about what T-Solve sends
or does, and the mock-server tests check exactly that, e.g. AC-001.12 asserts the gateway URL and the
`Bearer` header on every request (`JiraRestIssueSourceTest.java:94-97, :295-304`). None of the fail rules applies:
no failing test, no AC without a test, no CRITICAL finding.

**What the skip leaves unproven.** Two things no test can show:
1. That the real Atlassian gateway accepts what the client sends: scoped token as Bearer, `search/jql` with
   `nextPageToken`, the JQL, the comment pages and real ADF. Nobody has tested this live yet. RESEARCH-003 says so
   itself: "Not verified by a live call in this spike: Bearer with a scoped token on `/rest/api/3/search/jql`"
   (main checkout `artifacts/01-research/RESEARCH-003-jira-import-auth-and-api.md:123-124`). If the assumption is
   wrong, every real pull answers MSG50 while every test stays green.
2. NFR 1 (`spec.md:49`, 100 issues with comments in ≤ 60 s). It has no test. The task 4 review deferred its
   measurement to this task (`task-4-jira-pull-review.md:40-43`).

So the feature passes as a check against the spec, but the module should not be completed or demoed until the real
run is done (Issue 1). That is the user's call; see Assumptions made.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | No new behavior in this task. All 40 ACs have a passing test. I read a sample: AC-001.3 (100 cap, exactly 100 gives no MSG35, 20-page scan cap), AC-001.7 (401 / 503 / broken JSON / reset / real read timeout via a local `HttpServer` / not configured, token absent from message and log), AC-001.9 (403 MSG08, no service call), AC-001.11 (429 without Retry-After = 1 s, 503 with 5 s, 503 without, 6 s, three 429s give exactly two sleeps, comment pages retried apart from search), AC-001.12, AC-002.10 (four Finish enabled/disabled cases), AC-002.11 (MSG37 cancel/confirm), AC-004.6 (cleanup query skips a two-day-old Draft; `deleteByOwner` keeps the Draft). Each one checks the behavior its AC describes. |
| Completeness | pass | `trace` 40/40. The spec's Definition of Done is met. Not done: the task's real-Jira run, which the user skipped (Issue 1), and NFR 1 has no evidence (Issue 2). |
| Correctness | pass | `mvn verify` BUILD SUCCESS (981 + 220, 0 failures, 0 skipped); Spotless exit 0 on a fresh clone; frontend 83 files / 1480 tests, format, lint (0 errors), typecheck and build pass; `generate:api` 0 diff lines. |
| Integration | pass | The generated client matches `tsolve-api.yaml` (no diff). The client sits behind `JiraIssueSource`, as the spec's Interface Contracts and D2 require. The real Atlassian API has not been tested (Issue 1). |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace ticket-repository` → `trace ticket-repository: 40/40 acceptance criteria have a
test` (AC-001.1 … AC-004.7, all ✓; full output in `task-10-jira-import-verify-test-report.md`).

## Issues
No blocking issues. Informational:

1. **Real-Jira run not done** — `04-plans/ticket-repository/iteration-1/tasks.md:146`;
   `05-implementation/ticket-repository/task-10-jira-import-verify.md:22-27`. Of the task's three steps, this is the
   one that tests the real system, and it was skipped by the user's choice. Every Jira test runs against
   `MockRestServiceServer` or a local server. The gateway + scoped-token path (D2, AC-001.12) has never been tested
   live (RESEARCH-003:123-124).
   Fix: before `completed` or the KAN-45 demo, run it once with `JIRA_SITE_URL`, `JIRA_CLOUD_ID`,
   `JIRA_API_TOKEN` **and** `TSOLVE_STUB_PROJECTS` / `TSOLVE_STUB_JIRA_KEYS` (`.env.example:40-46`; without the key
   the pull answers MSG50 "not connected"). Pull a small range, fill a Solution, Finish, save and reopen a Draft.
   Record it in the session log, including the pull time for NFR 1.
2. **NFR 1 has no evidence** — `spec.md:49`. No test and no measured run. The task 4 review already showed that the
   pull has no overall deadline (`task-4-jira-pull-review.md:40-43`).
   Fix: record the wall time of the real run (Issue 1). A full 100-issue measurement can be asked of the user.
3. **Token scopes not written down** — `.env.example:109-112`. It explains where to find the cloud id but not which
   scopes the service account's token needs. RESEARCH-003:48 names them: `read:jira-work`, or the granular
   `read:issue-details:jira` + `read:jql:jira` + `read:comment:jira`, plus Browse projects. A token made without
   them gets 401/403, which shows up only as MSG50.
   Fix: one comment line with the scopes in `.env.example` (a quick change).
4. **RESEARCH-003 is not on the branch** — `spec.md` (Changelog, D2), `design.md:85` and `tasks.md:158` cite
   RESEARCH-003. It exists only as an **untracked** file in the main checkout
   (`git status`: `?? harnessclaude/artifacts/01-research/RESEARCH-003-jira-import-auth-and-api.md`).
   Fix: commit it with the next harness commit of KAN-45.
5. **Stale row in the master design** — `04-plans/ticket-repository/design.md:77`. The "Jira access" Key Decision
   still says the client reads `.email` / `JIRA_EMAIL`. That contradicts D2 and the row at `:85`.
   Fix: reword `:77` to `JIRA_SITE_URL`, `JIRA_CLOUD_ID`, `JIRA_API_TOKEN`.
6. **Tracking** — `progress.json:160` has the module as `implementing` (the dispatch said `evaluating`). `:176` has
   branch `feat/ticket-repository`, which `audit` reports as missing; per the team rules the branch is
   `feat/KAN-45-ticket-repository-jira-import`. `audit` also lists the module's `spec.md`, `design.md`,
   iteration-1 `design.md` and `tasks.md` as missing from `_registry/artifact-index.md`. Not caused by task 10.
   Fix: main session, through `sdd.ts`.
7. **Branch behind main** — `origin/main` is at `19e7a30e`, several commits past the last merge `e7c6f050`. These
   results are for the branch alone. Re-run `verify` after the next merge of main, before the PR.

### Open judgment calls carried from earlier reviews (unchanged by task 10)
- MSG35 still shows a literal `{count}` until the Report3 / catalog change of D8 (task 9 review).
- On a reopened Draft, Fill solution and Remove are saved at once, so Cancel / MSG37 cannot undo them
  (AC-002.11 "without changing a saved Draft", AC-004.3) (task 7 and task 9 reviews).

### Assumptions made
- The spec is the contract, and the task's manual step is not part of it. I took the session log's statement that
  the user chose on 2026-11-08 to skip the real-Jira run as true; I cannot confirm it. If the user did not agree, or
  wants the live run to be a condition of this feature, the verdict should be **fail** until it is recorded.
- I count the missing NFR 1 evidence as informational, as the task 4 review did, not as a missing criterion. NFR 1
  has no AC id and the Definition of Done does not cover NFRs. The user may decide otherwise.
- Recommendation for the user: do not move the module to `completed` and do not demo KAN-45 until Issue 1 is done.
  This is a judgment call; this review does not enforce it.

## Test Report
See `task-10-jira-import-verify-test-report.md`. Backend 981 unit + 220 IT, 0 failures / 0 errors / 0 skipped,
BUILD SUCCESS; Spotless exit 0 (fresh clone, `origin/main` fetched); frontend format / lint (0 errors, 1 warning) /
typecheck / build pass, 83 files 1480/1480 tests; `generate:api` 0 diff; trace 40/40; audit 0 failures for this
module (warnings in Issue 6).
