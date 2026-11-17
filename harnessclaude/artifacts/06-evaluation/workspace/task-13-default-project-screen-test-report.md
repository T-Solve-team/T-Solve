# Test Report: workspace — Task 13: default-project-screen

Date: 2026-11-17 · Commit range `db594997..030ed7b7` (code `030ed7b7`) · Branch `feat/KAN-121-workspace-default-project`

All commands were run from `frontend/` with Node 24.22.2 (`PATH` prefixed with `/c/Users/admin/AppData/Local/nvm/v24.22.2`).

| Command | Result |
|---------|--------|
| `npx vitest run src/features/workspace src/shared/messages` (one parallel run, cold) | 7/9 files passed, 93 tests passed. `catalog.test.ts` and `msg.test.ts` failed before any test ran: `beforeAll` hook timeout (10 s) at `src/test/setup.ts:133` while importing the MSW handlers under load, so 216 tests were skipped |
| `npx vitest run src/shared/messages --hookTimeout=60000` (rerun) | 3 files, 221/221 passed, including `AC-013.10` and `AC-006.7` |
| `npx vitest run src/features/workspace --hookTimeout=60000` (rerun) | 6 files, 88/88 passed, including AC-013.1–013.9 and AC-004.8 |
| `npm run typecheck` | clean |
| `npm run lint` | 0 errors, 1 warning in `features/tickets/components/TicketListFilters.tsx:23` (not this change) |
| `npm run format:check` | clean |
| `npm run generate:api` then `git status src/shared/api/generated` | no diff |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace workspace STORY-013` → 10/10 acceptance criteria have a test.

## Not run
- `npm run test` (full suite) and `npm run build`: not dispatched.
- Coverage: not measured.
