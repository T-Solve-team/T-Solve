---
module: fe-foundation
feature: guidelines
task: 5
verdict: pass
attempt: 2
date: 2026-10-23
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: fe-foundation — Task 5: Frontend guidelines for Report3

## Summary
Pass. I checked `default: 4940f0d..03f9137` against the spec only. The fix commit `03f9137` corrects the major
finding of attempt 1 and nits 4–7. The new text agrees with Report3: GBR-23, GBR-26, UC-20, UC-21 and the text of
MSG37 ("Unsaved changes will be lost unless you save a draft"). It adds no new contradiction. All checks pass
(397/397 tests, first run), and the trace is 4/4. Then I went over the whole diff again. Every MSG code still
exists and means what its sentence says. The screens, paths, roles, menus, menu order (AC-004.2), guards and
file names match the code, Report3 and D1–D6. One new minor finding: the confirm table in guideline 09 §5 is
not complete for Report3 (issue 3). It does not block the task.

## Attempt 1
Attempt 1 failed on one major finding: 08 §7 said that an import is never saved as a Draft. It also had three
minor findings and three nits. The FE Lead decided on 23/10: fix finding 1 and nits 4–7 in this task, keep
finding 2 (the role check of the test) as it is, and keep finding 3 (the Project filter ban) until the
`ticket-review` spec. Commit `03f9137` resolved them:
- 1 (major, Draft) — fixed. `08-routing-and-access.md:151-154` now says that confirming MSG37 drops the
  unsaved changes and that a Draft saved with **Save as draft** is kept (GBR-26, UC-20, UC-21). The "lives only
  in that page's memory" sentence is gone. The redirect is now "with nothing to preview", so it does not rule
  out drafts. No other doc still says that an import has no draft.
- 4 (minor, npm script) — fixed: `AGENTS.md:83` lists `npm run messages:report3`. That matches `package.json`
  (`node scripts/report3-messages.mjs`).
- 5 (nit, App Shell) — fixed: `07-ui-and-styling.md:65` "(role variants)", and `:144` "(UI design v3
  decisions, D4)".
- 6 (nit, tree) — fixed: `01-project-structure.md:34` "useSession.ts (GET /me, all roles, useSignOut)".
  `useSignOut` is in `src/shared/auth/useSession.ts:112`.
- 7 (nit, `{count}`) — fixed: `13-decision-log.md:217` "of MSG14 / MSG16; MSG17 has none". That matches the
  catalog, and it agrees with FE-34 ("a count form of MSG17 … to be updated").
- 2 and 3 — kept by the FE Lead's decision. They are listed again below as kept items. They do not decide the
  verdict.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-010.1: AGENTS.md names "35 web screens (26 pages with a path, 9 popups)" and the five roles, and no doc has a retired word with that capitalisation. AC-010.2: guideline 03 defines the ten terms (plus Owner), and its Role row lists `ROLES` in order. AC-010.3: FE-40, FE-41 and the FE-34 row "kept by D1" are in place. AC-010.4: every MSG code, `paths.` key and role value exists. The Draft rule now follows GBR-23 / GBR-26. |
| Completeness | pass | Every item of gap report E is done, and so are the attempt-1 fixes the FE Lead asked for. The earlier FE Lead decisions are still followed: "SRS" stays, no left-out MSG code is named, the codes are renumbered in place with one note, and FE-40/41/42 are new. |
| Correctness | pass | format:check, lint, typecheck, test (397/397, 22 files), build and `generate:api` (no diff) pass on the first run. I printed the type and context of all 45 codes the docs name from `report3-messages.json`. Each one fits its sentence (for example MSG37 is a Confirmation dialog for leaving Import Preview, MSG17 has no `{count}`, and MSG15 / MSG19 / MSG21 / MSG23 / MSG36 / MSG45 / MSG51 / MSG53 / MSG55 are confirms). `MSG_NEW_2` is in `catalog.ts` (D3), not in the JSON, as intended. |
| Integration | pass | The docs match the code: `scripts/report3-messages.mjs`, `app/dev/DevUserSwitcher`, `shared/config/tracker.ts`, `SESSION_STORAGE_PREFIX = 'tsolve.session.'`, `OPEN_BEFORE_SETUP = [workspaceSetup, profile]` in `guards.tsx`, the `MENUS` of each role and `MENU_ORDER` (Admin, PM, KM, TR, Employee, as in AC-004.2), and the `'pm-km'` fixture user. The branch example in 12 (`feature/KAN-123-…`) follows README "Branching and conventions". |

## Traceability
```
  ✓ AC-010.1  frontend/src/test/guidelines.test.ts
  ✓ AC-010.2  frontend/src/test/guidelines.test.ts
  ✓ AC-010.3  frontend/src/test/guidelines.test.ts
  ✓ AC-010.4  frontend/src/test/guidelines.test.ts
trace fe-foundation (STORY-010): 4/4 acceptance criteria have a test
```

## Issues
1. **Minor (kept, FE Lead 23/10)** — `frontend/src/test/guidelines.test.ts:37-46`: the AC-010.4 role check reads
   only `role: 'X'`, `roles: [...]`, `roles={[...]}`, `hasRole(...)` and `hasAnyRole(...)`. This is the same as
   in attempt 1, and it is not a reason to fail. Today every role value in the docs is in `ROLES`.
2. **Minor (kept until `ticket-review`, FE Lead 23/10)** — `frontend/AGENTS.md:37`,
   `frontend/guidelines/03-naming.md:162`: "never … a Project filter". Gap report B.2 says that Figma 7.1, 7.4 and
   7.5 now have one. It is not a reason to fail.
3. **Minor (new)** — `frontend/guidelines/09-forms-and-validation.md:98-107` and `:134`: the confirm table lists
   only the codes of the old template. The checklist says "Confirm dialogs exactly where the table above says".
   Report3 also has these web Confirmation dialogs, and all of them are in `catalog.ts`: **MSG42** (delete a
   Draft), **MSG57** (deactivate an account), **MSG78** (remove the Ticket Resolver role), **MSG100** (move a
   Wiki document to the trash), **MSG103** (delete a document permanently) and **MSG106** (remove Space
   access). A reader who follows the table word for word would leave out these confirms. Line 95 ("Hard-to-undo
   actions ask first") and gap report C (MSG42, MSG57, MSG78 in the screen tickets) soften this.
   Suggested fix: add the rows "Delete a Draft | MSG42 | yes", "Deactivate account · Remove Ticket Resolver
   role | MSG57 · MSG78 | yes · yes", "Move document to trash · Delete permanently · Remove Space access |
   MSG100 · MSG103 · MSG106 | yes · yes · yes". The FE Lead chooses the danger column. Or change the checklist
   to "… the table above, or where the screen's MSG list names a Confirmation dialog".
4. **Nit (new)** — `frontend/guidelines/01-project-structure.md:34`: the `shared/auth` line does not name
   `session.ts` (`toSession`, the `Session` type) or `roles.ts` (it names `ROLES`, not the file). The tree is a
   summary, so this is only for completeness.

## Assumptions made
- **Issue 3 does not decide the verdict.** AC-010.1–.4 pass word for word. The table is an old list that was
  renumbered correctly but never grown. It does not state a false rule the way the attempt-1 Draft sentence did,
  and line 95 gives the general rule. Judgment call for the user: add the six rows now as a quick change (my
  recommendation, one table and no acceptance criterion changes), or leave it to the screen tickets that use
  these codes.
- Findings 2 and 3 of attempt 1 are kept as the dispatcher passed on the FE Lead's decision. I did not judge
  them again.
- 08 §7 now says that opening `/import/preview` "with nothing to preview" goes to `/import`. Report3 does not
  say how a saved import Draft is opened again. I read the sentence as leaving that open, so it is not a
  finding.
- Guideline 03 has no glossary row for "Draft" (GBR-26). AC-010.2 and gap report E do not ask for one, so this
  is not a finding.
- `.claude/tsolve-project.md` (commit `b638e04`) is a separate quick change and is not reviewed here.

## Test Report
See `task-5-guidelines-test-report.md`. Results: format:check pass · lint pass · typecheck pass · test 397/397
pass (22 files, 35.9 s, first run, no timeout) · build pass · `generate:api` no diff · trace STORY-010 4/4 ·
coverage not measured (the project has no coverage script).
