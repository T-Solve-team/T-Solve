# Test Report: import-ticket-list — Task 32: apply-and-publish-popup

**Date:** 2026-11-19. Branch `feat/KAN-373-import-publish-row`, HEAD `2f785147`. Code commit `fc506bea` (range
`97824ea6..fc506bea`). No uncommitted change under `frontend/`.

**Commands** (Git Bash, in `frontend/`, `PATH=/c/Users/admin/AppData/Local/nvm/v24.22.2:$PATH`):
`npm run format:check`, `npm run lint`, `npm run typecheck`, `npx vitest run src/features/ticket-import`,
`npm run build`, `npm run generate:api` + `git status --short src/shared/api/generated`, and
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-017`.

## Results
| Check | Result |
|-------|--------|
| format:check | pass ("All matched files use Prettier code style!") |
| lint | exit 0. 0 errors, 1 warning: `TicketListFilters.tsx:23` react-refresh (known, not this task) |
| typecheck | pass |
| vitest `src/features/ticket-import` | 7 files, 138 tests, all passed (206.9 s; jsdom `getComputedStyle` notices only) |
| build | pass |
| generate:api | no diff in `src/shared/api/generated` |
| Traceability | AC-017.8, AC-017.9, AC-017.10 -> `ImportPreviewPage.test.tsx` (10/10 for STORY-017 overall) |

## New tests (`pages/ImportPreviewPage.test.tsx`)
| Test | Covers |
|------|--------|
| AC-017.8 shows Apply & publish next to Apply for a Needs Solution row | button present and enabled (placement not checked) |
| AC-017.8 AC-017.9 saves the Solution, publishes the row, shows MSG32 and refreshes the preview | call order fill -> publish, MSG32, popup closes, Needs Solution 19 / Valid 0 |
| AC-017.10 shows the publish rejection and keeps the popup open with what was written | publish 409 MSG40 after a fill that succeeded |
| AC-017.10 shows a rejected Solution at its field and publishes nothing | fill 400 field error; publish not called |

## Not covered
- Double click on Apply & publish.
- A Jira preview row.
- The page selection after a publish. It is wrong today: review issue 1.
- A 404 from publish.

## Known, not counted
`AddMemberModal.test.tsx` (timing-flaky) is outside this run's filter.

## Defects
No test failed. Review issue 1 (the published row stays selected) is a defect found by reading the code. It should
get a row in `docs/bug-log.md`.
