---
module: jira-app
feature: rewrite-job-forge
task: 27
verdict: pass
attempt: 1
date: 2026-11-18
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 27: Rewrite with AI job — Forge client and form

## Summary
The Ask polling now lives in one shared helper, `pollJob` (`tsolveClient.js:130`). It is not copied. `askQuestion` and `rewriteSolution` both call it.
- **Timing.** The 75 s deadline is counted from the POST. Polls run every 1.5 s, and each call has a 20 s limit cut to the time left. `remaining()` is checked before each GET, so no GET starts at or after the deadline.
- **Abort.** An abort is passed through as an `AbortError`.
- **Ask errors.** POST 4xx codes are kept, and POST 404/5xx give MSG06. Every GET failure gives MSG06, including 401/403. This fixes finding 1 of the Task 25 review, and `tsolveClient.test.js` tests it.
- **Rewrite errors.** Every failure except an abort gives MSG68. That covers POST and GET status codes, a Forge rejection, a missing `jobId`, FAILED, an unknown status, the 20 s and 75 s timeouts, and a DONE with missing or non-string sections.
- **Form.** The form aborts the rewrite on Close, on the dialog's close control, on unmount and on a new click. An aborted rewrite shows no preview and no MSG68, and it does not clear the newer run's busy state. Close during a rewrite still saves the current sections as the Draft (AC-005.1). Accept and Discard are unchanged. The resubmission form uses the same path, and a test covers `resubmit=true`.
- **Logging.** Nothing is logged.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-007.1: POST, then a GET every 1.5 s (polls at 1500/3000/4500), with the encoded job id, until the preview. AC-007.2 and AC-007.3 are unchanged. AC-007.4: every failure path gives MSG68 and leaves the sections unchanged. AC-012.7: the resubmit form takes the same path. AC-010.3, AC-010.8 and AC-010.15 behave as before, and the GET 401/403 case is now MSG06. |
| Completeness | pass | All code items of Task 27 are done. The dev-site check is still open (Issue 3). |
| Correctness | pass | jest 260/260 pass. A second and third run of api/form/askTsolve gave 193/193 each time. Lint is clean. Fake timers or an injected sleep/now are used everywhere, and the timer count is 0 at the end. |
| Integration | pass | Matches the OpenAPI contract: `rewriteSolution` POST 202 `{jobId}`, and `getSolutionRewriteJob` GET `{status, sections?}`, 404/503 MSG68. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-007 STORY-010 STORY-012` gives 33/33 acceptance criteria with a test. AC-007.1 and AC-007.4 have Forge tests in `solutionClient.test.js` and `SolutionFormModal.test.jsx`. AC-012.7 is tested in `SolutionFormModal.test.jsx`. The trace also prints "AC-001.77 in tests but not in the spec"; that line was there before this change.

## Issues
1. `jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.jsx:211` — Low — The Rewrite with AI button now stays enabled while a rewrite runs, because it shares `busy && !rewriteController.current` with Close. Before, it was disabled. Each extra click aborts the poll, but the backend cannot cancel its job. So repeated clicks start several AI jobs, use up job capacity and can hit 503 MSG68 (AC-007.8). Fix: keep `isDisabled={busy}` on Rewrite, leave Close as it is, and keep the abort-previous code in `rewrite()` to cover the late Forge re-render (BUG-237). This is a judgment call; the spec says nothing on it.
2. `docs/bug-log.md` — Low — No row exists for "Rewrite with AI shows MSG68 when the AI rewrite takes more than 18 s". The Ask case was logged as BUG-343. The project rule is "log every bug". Fix: add a row (KAN-294) that names the AC-007.4 tests, or record it in the Task 26 bug row if that one covers it.
3. Task 27 / `jira-app/AGENTS.md` rule 6 — Info — The dev-site check has not been done: a rewrite that takes 30–50 s should show the preview. Run it in `forge tunnel` before the commit or the PR.

## Assumptions made
- I treated Issue 1 as Low rather than a fail. No acceptance criterion says whether Rewrite stays clickable during a run, and the dispatcher's brief names "abort on new click" as expected behaviour. The user should say whether a second click should be possible at all.
- I did not count Issue 2 against the verdict. The rewrite change was made as a spec change (changelog 2026-11-18, STORY-007), so whether it also needs a bug row is for the user to decide.
- I did not count the missing dev-site check against the verdict, because a reviewer cannot run `forge tunnel`.

## Test Report
See `task-27-rewrite-job-forge-test-report.md`.
