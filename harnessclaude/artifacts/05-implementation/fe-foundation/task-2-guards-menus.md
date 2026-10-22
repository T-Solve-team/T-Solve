# Task 2: Guards, landing page, Workspace Setup redirect and menus

**Module:** fe-foundation
**Assignee:** Trần Trung Hiếu
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-23 02:00

### Working on: guards-menus (KAN-60, branch `feature/KAN-60-fe-foundation-guards`)
- `landingPath(roles)` (`shared/auth/landing.ts`): first match Admin → User List, PM → Review Queue, KM → Wiki Home,
  TR → Search, otherwise → Knowledge Chatbot. Task 1's temporary `mainRole` / `LANDING_PATHS` removed.
- `RequireWorkspace` (`app/router/guards.tsx`): an Admin is sent to `/workspace-setup` while `workspaceReady` is
  false, except on Workspace Setup and My Profile; non-Admins are not redirected. It wraps only the routed screens
  and `/`, so unknown paths (404) and `/dev/kit` are not redirected (design: edge rules).
- Menus (`app/layout/menu-config.ts`): per-role menus in the v3 order (PM: Expired Tickets before Review History,
  plus Triage History; Admin: Knowledge Dashboard before Audit Log); new Wiki (`ReadOutlined`), Knowledge Chatbot
  (`MessageOutlined`), Triage History (`BranchesOutlined`); `menuFor(roles)` joins them Admin › PM › KM › TR ›
  Employee without duplicates. `SideMenu` uses it.
- Tests: `app/router/guards.test.tsx`, `shared/auth/landing.test.ts` (new), `app/layout/menu-config.test.ts`
  (rewritten); existing tests in `routes.test.tsx` named with their AC ids.
- BUG-004 (folded into KAN-60 by the FE Lead): `ReviewQueuePage.test.tsx` loads the lazy page module once in
  `beforeAll`, so the first test's 5 s wait covers only the render (no longer timeout, guideline 10 §6); the
  test is named with the bug id; bug-log row set to Fixed.
- Commit: default@03f8f8c

### Deviations from the plan
- Order: done after Task 3 (swapped 22/10); the new menu entries came here with AC-004.1 instead of Task 3.
- BUG-004 added to the task's scope.

### Verify
- `npm run format:check`, `lint`, `typecheck`, `build`: exit 0; `generate:api`: no diff.
- `npm run test`: 155/155 in 3 of 3 full runs (BUG-004 had failed 2 of 6 before the fix).
- `bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-002 STORY-003 STORY-004`: 13/13 acceptance criteria
  have a test.

### Blockers
none

### Next up
Evaluate guards-menus; then Task 4 catalog-tags (KAN-62).
