---
module: ticket-repository
feature: manual-entry-tab
task: 19
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Test Report: ticket-repository — Task 19: Manual entry tab and opening a Manual Draft

Shared run: `harnessclaude/artifacts/06-evaluation/ticket-repository/task-14-manual-entry-contract-test-report.md`.

## Frontend checks (Node v24.22.2)

| Command | Result |
|---------|--------|
| `npm run format:check` | pass |
| `npm run lint` | pass (1 warning, in a file outside this range) |
| `npm run typecheck` | pass |
| `npm run build` | pass |
| `npm run generate:api` | no diff in `src/shared/api/generated` |
| `npm run test` (full) | exit 1: 1636 passed, **1 failed**: `ManualEntryForm.test.tsx > AC-006.12 checks the Evidence type and size before sending (MSG34)`, `Test timed out in 15000ms` (15,270 ms) |

## Solo reruns of the failing file

| Run | Result |
|-----|--------|
| `npx vitest run src/features/ticket-import/components/ManualEntryForm.test.tsx` (1) | 12/12 pass |
| same (2) | 12/12 pass |
| verbose, 3 files (`ManualEntryForm`, `DraftsList`, `ImportTicketsPage`) | 40/40 pass |

So the failure is a load timeout (BUG-213), not a broken assertion. But the test is slow even alone (review Issue 2).

## Task 19 test times (verbose solo run)

| Test | Time |
|------|------|
| AC-006.12 offers only the managed Projects and no future resolved date | 5,652 ms |
| AC-006.1 Save sends the entry and its files, shows MSG32 and empties the form | 9,738 ms |
| AC-006.12 shows MSG01 at each empty required field and sends nothing | 2,513 ms |
| AC-006.12 checks the Evidence type and size before sending (MSG34) | **11,993 ms** |
| AC-006.12 checks the Evidence count before sending (MSG72) | 9,834 ms |
| AC-006.12 shows a rejection next to its field and keeps every value and file | 9,971 ms |
| AC-007.1 Save as draft needs only the Project, shows MSG41 and lists the Draft | 5,862 ms |
| AC-007.2 Save as draft without a Project shows MSG01 and sends nothing | 2,638 ms |
| AC-007.4 Open on a Manual Draft fills the Manual entry tab with its fields and files | 1,741 ms |
| AC-007.5 saving a reopened Draft sends its id and the files it keeps | 4,910 ms |
| AC-007.5 kept and new files together are at most five (MSG72) | 2,712 ms |
| edge 23 a Draft gone meanwhile shows MSG04 and goes back to a new entry | 3,058 ms |
| `DraftsList.test.tsx` AC-007.3 lists a Manual Draft with type Manual and 1 row beside the other Drafts | 753 ms |

The other `DraftsList` tests (AC-004.2–AC-004.5, BUG-194, BUG-195) also pass with the new `onOpenManual` prop.
