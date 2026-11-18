# Test Report: chatbot — Task 16: conversation-store

- Date: 2026-11-18 · attempt 1 · range `default: da13f8ec..4967050a` (branch `feat/KAN-183-chatbot-persist-sessions`)
- Environment note: the shell's `JAVA_HOME` (`C:\Program Files\Java\jdk-21`) does not exist on this machine; the
  runs used `JAVA_HOME=C:\Program Files\Java\jdk-21.0.12` for the process only (no file changed).

## Backend (`backend/`, `mvn verify` — tests + `spotless:check`)

| Run | Result |
|-----|--------|
| 1 | BUILD FAILURE — surefire 2977 run, 1 failure: `vn.tsolve.review.service.impl.ReviewQueueEventServiceImplTest.sendPoolSaturatedByStuckStreamsLosesNoEventAndDoesNotBlockHeartbeats_AC_012_2` (`ReviewQueueEventServiceImplTest.java:269`, "Expecting value to be true but was false" on a 1 s `await`). Module `review`, not in the range; the machine was also running the full Vitest suite at the time. |
| 2 (rerun, same commit) | **BUILD SUCCESS** — surefire `Tests run: 2977, Failures: 0, Errors: 0, Skipped: 0`; failsafe `Tests run: 934, Failures: 0, Errors: 0, Skipped: 0`; spotless:check passed. |

Run 1's failure is a timing-sensitive test of another module under CPU load (flaky), not caused by this change.

Changed / task tests (all green in run 2):
- `ChatbotAnswerControllerTest` 15/15, `ChatbotAnswerServiceImplTest` 59/59, `ChatbotIssueControllerTest` 29/29,
  `ChatbotIssueServiceImplTest` 44/44 (surefire)
- `ChatbotConversationStoreIT` (6 tests: AC-013.11, AC-013.1/.3, AC-013.2/.3, AC-013.4, AC-013.3 rollback, AC-010.3),
  `ChatbotIssueIT`, `ChatbotAnswerTimeoutIT`, `IssueTargetRepositoryIT`, platform isolation ITs (failsafe, in the 934)

## Frontend (`frontend/`)

| Check | Result |
|-------|--------|
| `npm run format:check` | pass — "All matched files use Prettier code style!" |
| `npm run lint` | pass — 0 errors, 1 warning (pre-existing, `features/tickets/components/TicketListFilters.tsx:23`) |
| `npm run typecheck` | pass |
| `npm run test` (run 1, in parallel with Maven) | 40 failed / 1605 passed / 307 skipped — timeouts (15 s) in `my-profile`, `tickets` etc. under load; none in `features/chatbot` |
| `npx vitest run src/features/chatbot` | **4 files, 144/144 passed** |
| `npm run test` (run 2, machine idle) | 1 failed / 1951 passed (120 files) — `features/ticket-import/components/ManualEntryForm.test.tsx` "AC-006.18 AC-006.19 reopens Evidence…" timed out at 15.5 s; that file reruns alone **18/18 passed** and is not touched by the range (flaky timeout) |
| `npm run build` | pass |
| `npm run generate:api` | exit 0; `git status --porcelain src/shared/api/generated` empty — no diff |

## Traceability

`bun harnessclaude/hooks/sdd.ts trace chatbot STORY-013 STORY-007 STORY-010` → 19/25 have a test. Untested:
AC-013.5 – AC-013.10 (Tasks 17/18, out of this task). Every AC this task covers has a test id **by name**; see the
review for AC-007.3, whose only tagged test does not exercise its 400 clause.
