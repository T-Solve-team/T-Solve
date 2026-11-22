# Test report: jira-app — Task 27 rewrite-job-forge (attempt 1)

Date: 2026-11-18. The change is uncommitted on `fix/KAN-294-jira-app-ask-job`, and the scope is `git diff -- jira-app`. Maven was not run, as the dispatcher asked; the backend belongs to Task 26.

## Execution (in `jira-app/TSolve_JiraApp`)

| Command | Result |
|---|---|
| `npx jest` (full suite) | 15 suites, 260 tests, all pass (10.4 s) |
| `npx jest src/frontend/api src/frontend/form src/frontend/askTsolve`, 2 more runs | 193/193 pass on each run |
| `npm run lint` | ESLint: no issues |

Not run: `forge lint`, and the dev-site check (a 30–50 s rewrite shows the preview).

## Timing
Every new polling test uses Jest fake timers (`advanceTimersByTimeAsync`) or an injected `sleep`/`now`. No test waits on a real clock. The tests check `jest.getTimerCount() === 0` after DONE, after a timeout, at the 75 s deadline, and after an abort at each of 4 stages, a Close and an unmount.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-007 STORY-010 STORY-012`: 33/33 acceptance criteria have a test. The trace also prints "AC-001.77 in tests but not in the spec"; that line was there before this change.
