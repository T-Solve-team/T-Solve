# Task 4: Report3 message catalog and triage status tags

**Module:** fe-foundation
**Assignee:** Trần Trung Hiếu
**Status:** passing
**Iteration:** 1

---

## Session 2026-10-23 08:05

### Working on: catalog-tags (KAN-62, branch `feature/KAN-62-fe-foundation-catalog`)
- `scripts/report3-messages.mjs` (`npm run messages:report3`) reads `Report3/System_Messages.md` from
  `TSOLVE_DOCS` and writes `src/shared/messages/report3-messages.json` (103 messages); a second run gives the
  same file.
- `catalog.ts`: the 91 Report3 web codes with their exact texts and types, plus `MSG_NEW_2` (AC-006.3). Left out:
  the Jira App codes (MSG64, 74, 76, 77, 80–83) and MSG20, MSG70 (D2), MSG58, MSG79 (D3). `banner` type dropped;
  toast level error for MSG06, MSG33, MSG69, success for the rest.
- `shared/config/tracker.ts` `TRACKER_NAME = 'Jira'`; `msg()` fills `{tracker_name}` unless the caller passes it.
- Codes remapped in one pass across the app, the review feature and `openapi/tsolve-api.yaml` (client regenerated):
  MSG16→13, 18→14, 19→15, 20→16, 21→17, 90→19, 29→21, 47→49, 53→64.
- Status tags `SIMPLE` and `COMPLEX` (GBR-35).
- BUG-005 (folded in by the FE Lead): `RequireWorkspace` compares the path lower-cased; test
  `guards.test.tsx` `AC-003.2 treats a path in other letter case as the same screen (BUG-005)`; bug-log row Fixed.
- Tests: `catalog.test.ts` (AC-006.1, .2, .3, .7), `msg.test.ts` (AC-006.4–.6), `StatusTag.test.tsx` (AC-007.1–.3).
- Commit: default@f67ead7

### Deviations from the plan
- The plan named the review feature's codes only; MSG29, MSG47 and MSG53 were also in use and are remapped to
  their Report3 codes (MSG21, MSG49, MSG64). MSG64 is the Jira App banner, so it appears only in descriptions
  (Request Changes popup, `tsolve-api.yaml`), not in the web catalog.
- BUG-005 is in this task by the FE Lead's choice (23/10).

### Verify
- `npm run format:check`, `lint`, `typecheck`, `build`: exit 0; `generate:api`: no change.
- `npm run test`: 352/352 in two full runs; the KAN-62 files plus `mock-mode.test.tsx`: 257/257. An earlier run
  was stopped by Claude Code for low system memory before its tests ran.
- `bun harnessclaude/hooks/sdd.ts trace fe-foundation`: every AC of STORY-006 and STORY-007 has a test; only
  AC-010.x (Task 5) has none yet.

### Blockers
none

### Next up
Evaluate catalog-tags; then Task 5 guidelines (KAN-63).

## Session 2026-10-23 08:20

### Working on: catalog-tags — evaluation
- Independent review (harnessclaude-reviewer, attempt 1): **pass** — `06-evaluation/fe-foundation/task-4-catalog-tags-review.md`.
  Minor findings left as they are: no test checks the toast levels; `msg()` uses `name in values`
  (`Object.hasOwn` is safer); `useReviewDecisions.ts` passes an unused `count` to MSG17; the StatusTag test name
  still says "17 statuses of the Figma kit"; design.md names the `_context` copy as the catalog test source, the
  script reads `TSOLVE_DOCS`.
- The guidelines and `frontend/AGENTS.md` still name SRS v2 codes (e.g. rule 5 `MSG18`) until Task 5.
- The review said "92 web codes"; corrected to 91 plus `MSG_NEW_2` (92 entries).
- Status: passing.

### Next up
Task 5 guidelines (KAN-63).
