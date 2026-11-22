# Test report: jira-app — Task 25 ask-job-forge (attempt 1)

Date: 2026-11-18. The change is uncommitted in the working tree on `fix/KAN-294-jira-app-ask-job`. Scope: `git diff -- jira-app docs/bug-log.md`. I did not run Maven, as the dispatcher asked; the backend belongs to Task 26.

## Execution (in `jira-app/TSolve_JiraApp`)

| Command | Result |
|---|---|
| `npx jest` (full suite) | 15 suites, 221 tests, all pass (8.7 s) |
| `npx jest tsolveClient.test.js AskTsolvePage.test.jsx`, 3 runs | 76/76 pass each run (5.2–6.0 s), no `act(...)` warnings |
| `npm run lint` | ESLint: no issues |

Not run: `forge lint`, and the Task 25 dev-site check (a 30–50 s chatbot answer shows the answer, not MSG06). This repo has no Prettier.

## Timing
Every polling test uses Jest fake timers (`advanceTimersByTimeAsync`) or an injected `sleep`/`now`. No test waits on a real clock. The tests check `jest.getTimerCount() === 0` after the answer, after a timeout, at the deadline and after each abort, so no timers are left behind.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-010`: 18/18 acceptance criteria have a test. Also printed: "ids in tests but not in the spec: AC-001.77", which existed before this change.
