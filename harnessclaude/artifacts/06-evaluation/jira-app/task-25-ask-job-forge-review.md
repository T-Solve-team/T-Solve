---
module: jira-app
feature: ask-job-forge
task: 25
verdict: pass
attempt: 1
date: 2026-11-18
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 25: Ask T-Solve job — Forge client and page

## Summary
`askQuestion` now sends a POST, reads the `jobId`, then polls every 1.5 s with 20 s per call. The 75 s deadline is counted from the POST. Each wait and each GET gets only the time left, so no GET starts at or after the deadline, and a GET already in flight is cut at the deadline. A missing or bad `jobId`, `FAILED`, an unknown status, 404, 5xx or a Forge rejection all give MSG06. POST 4xx codes (MSG01, MSG02, MSG08, MSG124) are kept. An `AbortSignal` stops the wait and stops waiting for the call. A late `invokeRemote` result is thrown away. The page aborts on unmount and before a new question, and it returns before any state update once its controller is aborted. Mock mode is unchanged: the signal is ignored there and the page guard still applies. Nothing is logged. The answer of a DONE job is the `ChatbotAnswer`, and its `answerId` is what AC-010.15 rates; a page test checks this.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-010.3: polling at 1.5 s, at most 10 history turns, encoded job id. AC-010.4: the answer comes from DONE. AC-010.8: 20 s per call, 75 s overall, and FAILED/404/5xx give MSG06. AC-010.12: abort on unmount, no logs. AC-010.15: `answerId` of the shown answer. AC-010.16: 404 gives MSG06. |
| Completeness | pass | All Task 25 items are done, including the fixed comment on `ASK_TIMEOUT_MS`. The dev-site check is still open (see Issue 3). |
| Correctness | pass | 221/221 jest tests pass, 3 runs are stable, lint is clean, fake timers are used and no timers are left behind. |
| Integration | pass | Matches the OpenAPI contract: POST 202 `{jobId}`, GET `{status, answer?}`, 401/404/503. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-010`: 18/18 acceptance criteria have a test (AC-010.1 … AC-010.18). The id AC-001.77 appears in tests but not in the spec; it existed before this change.

## Issues
1. `jira-app/TSolve_JiraApp/src/frontend/api/tsolveClient.js:122-131` — Low — A GET 4xx other than 404 keeps the server's code. For example, a 401 or 403 body carrying MSG08 or MSG124 would switch the page to "not connected" in the middle of polling. AC-010.8 says that any failed call of AC-010.3 shows MSG06. Fix: map every GET failure except an abort to MSG06, and keep the POST mapping as it is. This is a judgment call (see Assumptions).
2. `docs/bug-log.md:357` — Low — BUG-343 "Fixed in" holds the branch name. Other rows hold the PR (`#284`). Fix: put the PR number there when the PR is opened.
3. Task 25 / `jira-app/AGENTS.md` rule 6 — Info — The dev-site check has not been done: a question the chatbot answers in 30–50 s should show the answer. The bug-log row says so too. Run it before the commit or the PR.

### Reuse for Task 27 (advisory)
The polling cannot be reused as it is. It lives inside `askQuestion` as closures (`remaining`, `callJob`, the loop), with the `QUESTIONS` path, the `job.answer` check and the 404/5xx→MSG06 mapping written into it. A small extraction would make it reusable: `pollJob(path, body, { signal, sleep, now })` sends the POST, polls `${path}/${jobId}`, and returns the DONE job (or throws a `TsolveError` or an `AbortError`). `askQuestion` would then check `job.answer` and map 404/5xx to MSG06. `rewriteSolution` would check `job.sections` and map every failure except an abort to MSG68. The MSG06 values inside `remaining()`, `within()` and the `jobId` check can stay, because each caller maps them again. `within()` and `wait(ms, signal)` can be reused as they are.

## Assumptions made
- GET 4xx other than 404 (Issue 1): I read AC-010.8 "a call … fails" as covering it, but AC-010.5 and AC-010.11 name only the POST codes, so I rated it Low rather than a fail. The user should confirm whether a mid-poll 401/403 must show MSG06.
- I did not count the missing dev-site check against the verdict, because a reviewer cannot run `forge tunnel`.

## Test Report
See `task-25-ask-job-forge-test-report.md`: jest 221/221 pass, lint clean, trace 18/18.
