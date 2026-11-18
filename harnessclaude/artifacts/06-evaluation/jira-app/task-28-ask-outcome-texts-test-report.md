# Test Report: jira-app — Task 28: ask-outcome-texts

- Date: 2026-11-18
- Reviewer: harnessclaude-reviewer
- Iteration: 2, attempt 1
- Code range: default repo `ae6fe9de..8dd140cf` (code commit `8dd140cf`; harness commit `a10df384`)

## Commands (run in `jira-app/TSolve_JiraApp`)

| Command | Result |
|---------|--------|
| `npx jest` (full suite) | 15/15 suites passed, 266/266 tests passed, 0 snapshots, 8.2 s |
| `npm run lint` (`eslint src/**/*`) | No issues found |

## Traceability (`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-010`)

`19/19 acceptance criteria have a test`. AC-010.19 is covered by `src/frontend/messages.test.js` and
`src/frontend/askTsolve/AskTsolvePage.test.jsx`. One warning, from earlier work: `ids in tests but not in the spec: AC-001.77`.

## Tests touched by the change

- `AskTsolvePage.test.jsx` — `it.each` "AC-010.19 shows the web chatbot text for a %s answer, with no buttons (BUG-347)", run for GREETING, OFF_TOPIC and INJECTION: the text is shown, MSG109 is absent and neither button is shown.
- `AskTsolvePage.test.jsx` — BUG-328: the GREETING answer shows MSG_NEW_5, no "Solved" or "Not solved, create ticket" button, and `rateAnswer` is not called.
- `messages.test.js` — "AC-010.19 has the web chatbot texts …": checks all three strings word for word.
- These regression tests still pass: AC-010.7 (MSG109 and both buttons for NO_SOURCE), AC-010.6, AC-010.9, AC-010.10, AC-010.15.

## Not run

- Coverage: the task did not ask for it.
- `forge lint` and the dev-site check after `forge deploy` (ask "hi"): these need Forge credentials and the dev site. They are still pending.
