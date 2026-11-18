---
module: jira-app
feature: ask-outcome-texts
task: 28
verdict: pass
attempt: 1
date: 2026-11-18
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 32: Ask T-Solve texts for a greeting, an off-topic question and an injection

## Summary
AC-010.19 is met. `AskTsolvePage.jsx` now picks the text for each answer outcome from a lookup table that matches the
web chatbot's `ChatAnswer.tsx` exactly. NO_SOURCE gets MSG109, GREETING gets MSG_NEW_5, OFF_TOPIC gets MSG_NEW_6 and INJECTION gets MSG_NEW_7.
The answer buttons now show only for a rateable outcome (`RATEABLE`, the same as the web's `canRate`). The strings in
`messages.js` are identical to the ones in `frontend/src/shared/messages/catalog.ts`. A non-ANSWERED turn shows no sources.
None of AC-010.6, AC-010.7, AC-010.9, AC-010.10, AC-010.15 or BUG-328 regressed. The full jest suite and lint pass, and
trace shows 19/19 criteria covered.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `AskTsolvePage.jsx:40` maps each outcome to its text. `:220` hides "Solved" and "Not solved, create ticket" unless the outcome is ANSWERED or NO_SOURCE. `:304-313` (the non-ANSWERED branch) shows only the text, with no sources. ANSWERED (AC-010.6) and NO_SOURCE (AC-010.7) still show both buttons. |
| Completeness | pass | All three outcomes, the three texts and the no-button rule are covered. The `docs/bug-log.md` row for BUG-347 has the test names. The spec change log row is dated 2026-11-18. |
| Correctness | pass | 266/266 tests pass and lint is clean. The three strings match `catalog.ts:276-287` word for word. An unknown outcome would call `message(undefined)`, which falls back to MSG06 (`messages.js` `message`), so the page cannot crash on it. |
| Integration | pass | The outcome names are the same as the web chatbot's and the backend's. The `rate()` guard (BUG-328) is still in place as a second check. The Create dialog prefill (AC-010.9) cannot be reached from a GREETING, OFF_TOPIC or INJECTION answer, so it cannot be drafted from one. |

## Traceability
```
  ✓ AC-010.1 … AC-010.18  (unchanged, all with tests)
  ✓ AC-010.19  jira-app/TSolve_JiraApp/src/frontend/messages.test.js, jira-app/TSolve_JiraApp/src/frontend/askTsolve/AskTsolvePage.test.jsx
  ! ids in tests but not in the spec: AC-001.77
trace jira-app (STORY-010): 19/19 acceptance criteria have a test
```
(The AC-001.77 warning comes from earlier work, not this task.)

## Issues
No blocking issues.

1. Low (observation, no change asked): `AskTsolvePage.jsx:220`. Say a conversation has an earlier ANSWERED turn and the user then types "thanks" or "hi". The last answer is now a GREETING, so the page shows no "Not solved, create ticket" button, and the user must ask a real question again to get one. This is what AC-010.19 says ("neither … button") and matches the web chatbot. Raise it with the user only if they want a create path after a greeting.
2. Low (test gap, not a failure): the AC-010.19 tests mock answers with `citations: []`. No test shows that sources are hidden when a GREETING answer carries citations. The code cannot show them, because the non-ANSWERED branch never renders citations. Fix: optionally add citations to one `it.each` case.

## Assumptions made
- Hiding "Not solved, create ticket" after a GREETING, OFF_TOPIC or INJECTION answer, even when an earlier turn was ANSWERED, is taken as intended. The spec text says "neither … button" with no exception. This is open for the user to confirm.
- The dev-site check (`forge deploy`, then ask "hi") is outside what a reviewer can run. It is still pending and is not counted against the verdict.

## Test Report
See `task-32-ask-outcome-texts-test-report.md`. `npx jest`: 15 suites and 266 tests, all passing. `npm run lint`: clean. Trace: 19/19.
