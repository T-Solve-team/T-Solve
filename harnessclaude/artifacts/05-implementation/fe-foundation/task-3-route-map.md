# Task 3: Route map with the new screens as placeholders

**Module:** fe-foundation
**Assignee:** Trần Trung Hiếu
**Status:** passing
**Iteration:** 1

---

## Session 2026-10-23 00:45

### Working on: route-map (KAN-61, branch `feature/KAN-61-fe-foundation-routes`)
- `paths.ts`: the 26 routed screens of the 35; new keys `workspaceSetup`, `triageHistory`, `wikiHome`,
  `wikiDocument`, `wikiUpload`, `spaceSettings`, `chatbot`; 2.1 / 2.2 renumbered to 2.2 / 2.3.
- Roles: every `routes.ts` matches the spec table (AC-005.1); Employee screens use `['EMPLOYEE']`;
  `ALL_SIGNED_IN_ROLES` removed.
- Popups named in their feature's `routes.ts`: 1.3.3 Hand Over Responsibilities (users), 2.4 Delete Department
  (workspace, renumbered), 10.2 Triage Result Detail (triage), 11.5 Folder (wiki).
- New features `triage`, `wiki`, `chatbot`: `routes.ts`, `index.ts`, `mocks/handlers.ts`, placeholder pages;
  registered in `app/router/feature-routes.ts` and `src/mocks/handlers.ts`. Workspace Setup placeholder.
- Test: `app/router/route-table.test.tsx` (AC-002.5, AC-005.1–AC-005.4); the existing 404 test named AC-005.5.
- Commit: default@02e9fc3

### Deviations from the plan
- Order: the FE Lead swapped Tasks 2 and 3 (22/10), so KAN-60's landing pages and menus can be tested on real
  placeholders and `main` never shows a dead link. The new menu entries (Wiki, Knowledge Chatbot, Triage History)
  move to KAN-60 with AC-004.1.

### Verify
- `npm run format:check`, `lint`, `typecheck`, `build`: exit 0; `generate:api`: no change.
- `npm run test`: 132/132 in two of three full runs. One run failed two tests on their first `findBy` (timing
  under full-suite load); the same global-errors test also failed once on `main`. Logged as BUG-003, fixed in its
  own PR.
- `bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-005`: 5/5 acceptance criteria have a test.

### Blockers
none

### Next up
Evaluate route-map; BUG-003; then Task 2 guards-menus (KAN-60).

## Session 2026-10-23 01:20

### Working on: route-map — evaluation
- Independent review (harnessclaude-reviewer, attempt 1): **pass** — `06-evaluation/fe-foundation/task-3-route-map-review.md`.
  Minor findings left as they are: the AC-005.4 test checks the handler import by text, not `FEATURE_MOCKS`;
  the screen number in the AC-005.3 regex is not escaped; `ScreenPlaceholder` still says "SRS III" (Task 5).
- The reviewer's note on AC-005.5: a signed-out user on an unknown path goes to Login first (AC-002.3), then
  sees the 404 once signed in — as specified.
- Status: passing.

### Next up
BUG-003 (KAN-65), then Task 2 guards-menus (KAN-60).
