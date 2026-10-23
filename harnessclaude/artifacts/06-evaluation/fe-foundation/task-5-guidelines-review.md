---
module: fe-foundation
feature: guidelines
task: 5
verdict: fail
attempt: 1
date: 2026-10-23
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: fe-foundation — Task 5: Frontend guidelines for Report3

## Summary
Fail, for one major finding. I checked `default: 4940f0d..02f8b86` against the spec only. All checks pass
(397/397 tests), the trace is 4/4, and the test really checks AC-010.1–.4: run on the old docs, every criterion
fails. I checked every MSG code in the docs against `report3-messages.json`: each one is in the catalog, and each
one means what its sentence says. I also checked every screen, path, page file, role, menu, landing rule, guard,
hook, file path and npm script against the code, and they all match. The glossary agrees with Report3 4.1,
GBR-16, GBR-28 and GBR-29…36.

The major finding: guideline 08 §7 still says that an import is never saved as a draft. Report3 (GBR-23, GBR-26,
UC-20, UC-21 and the text of MSG37) says the Project Manager can save the import as a Draft. A teammate who follows
08 would build Import Preview without drafts. The fix is two sentences in one file.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | fail | AC-010.1: AGENTS.md says "35 web screens (26 pages with a path, 9 popups)" and names the five roles. No doc has a retired word with that capitalisation. AC-010.2: guideline 03 defines the ten terms (plus Owner), and its Role row lists the five `ROLES` in order. AC-010.3: FE-40 (several roles), FE-41 (Report3 catalog) and the FE-34 row "kept by D1" are in place, with one renumbering note at the top. AC-010.4: every MSG code, `paths.` key and role value in the docs exists. But the story asks that the guidelines "describe the Report3 app", and 08 §7 contradicts GBR-23 / GBR-26 (issue 1). |
| Completeness | pass | Every item of gap report E is done: AGENTS.md, 01, 02, 03, 06, 07, 08, 09, 10, 12, 13, plus 05. The FE Lead decisions of 23/10 are followed: "SRS" stays and only "SRS v2/V.2" names changed (`SRS III` stays), no excluded MSG codes are named, the decision-log codes are renumbered in place with one note, FE-40/41/42 are new, FE-34 is kept by D1, and older entries keep their SRS v2 history. |
| Correctness | pass | format:check, lint, typecheck, test (397/397, 22 files), build and `generate:api` (no diff) all pass on the first run. The MSG meanings are right, for example MSG13 (empty Review Queue), MSG14 / MSG16 (approved / rejected toasts with `{count}`), MSG15 / MSG19 / MSG21 / MSG23 / MSG36 / MSG37 / MSG45 / MSG51 / MSG53 / MSG55 (the confirms in 09 §5), MSG18 (Request changes tooltip), MSG49 (tracker project already linked), MSG11 (duplicate email), MSG31 (existing ticket ID), MSG34 / MSG72 (Evidence type and size / 6th file), MSG27 / MSG28 (CSV type / size), MSG39 (date range), MSG40 (Project no longer active on Finish), MSG09 / MSG10 (not registered / deactivated), MSG60 (Workspace Setup) and MSG73 / MSG75 (the sign-in link from Jira). Each table in 06 §6 lists only codes of that Report3 type. |
| Integration | pass | The docs match the code: `paths.ts` (26 keys), every `features/*/routes.ts` (title, path, roles, page file), the planned popup files (`…Modal`, guideline 03 naming), `MENUS` and `menuFor` with `MENU_ORDER`, `landingPath`, `RequireWorkspace` with `OPEN_BEFORE_SETUP`, the guard order in `routes.tsx`, `useSession()` (`roles`, `hasRole`, `hasAnyRole`), `resetSession(client, { signedOut })`, `SESSION_STORAGE_PREFIX`, `DEV_USERS` with `'pm-km'`, `renderApp` (default Project Manager), `TRACKER_NAME` and the `messages:report3` script. |

## Traceability
```
  ✓ AC-010.1  frontend/src/test/guidelines.test.ts
  ✓ AC-010.2  frontend/src/test/guidelines.test.ts
  ✓ AC-010.3  frontend/src/test/guidelines.test.ts
  ✓ AC-010.4  frontend/src/test/guidelines.test.ts
trace fe-foundation (STORY-010): 4/4 acceptance criteria have a test
```

## Issues
1. **Major** — `frontend/guidelines/08-routing-and-access.md:151-154`: "confirming discards everything (an import is
   never saved as a draft) … The import data lives only in that page's memory". This contradicts Report3. GBR-23
   says "The Project Manager can save the import as a Draft (GBR-26); leaving the screen without saving a Draft
   imports nothing". GBR-26, UC-20 and UC-21 say the same, and MSG37 itself says "Unsaved changes will be lost
   unless you save a draft." (Gap report C also lists "Save as draft and drafts (MSG41, MSG42)" for 5.1–5.4.)
   Suggested fix: "leaving before Finish/Confirm asks **MSG37**; confirming discards the unsaved changes (a Draft
   saved with Save as draft is kept, GBR-26)". Drop or soften "lives only in that page's memory" so that it does not
   rule out drafts.
2. **Minor** — `frontend/src/test/guidelines.test.ts:37-46`: the AC-010.4 role check only reads `role: 'X'`,
   `roles: [...]`, `roles={[...]}`, `hasRole(...)` and `hasAnyRole(...)`. A wrong role value in backticks (a table),
   in `roles.includes('X')` (as in 08 §4), in `=== 'X'`, or in prose like `` `['EMPLOYEE']` `` would pass. Today
   there is none: I listed every quoted upper-case value in the docs, and all role values are in `ROLES`.
   Suggested fix: also match `` `[A-Z_]+` `` and `'[A-Z_]+'` that equal an old role (`STAFF`,
   `DEPARTMENT_MANAGER`), or every `includes('…')` / `=== '…'`.
3. **Minor** — `frontend/AGENTS.md:37`, `frontend/guidelines/03-naming.md:162`, `frontend/guidelines/12-workflow.md:242`:
   "never add a Project filter to any screen". Gap report B.2 says that Figma 7.1 now has a Project filter (also
   7.4, 7.5). The spec leaves that filter to `ticket-review` (Out of Scope), so this is not a contradiction of this
   module's spec. But the ban will mislead whoever builds it. See "Assumptions made".
4. **Minor** — `frontend/AGENTS.md:72-82`: the npm scripts table does not list the new `npm run messages:report3`
   (guidelines 01, 02, 06 and 13 name it). Suggested fix: add one row.
5. **Nit** — `frontend/guidelines/07-ui-and-styling.md:65`: "Web/App Shell (Role=4)" still reads like the old four
   roles. Kit v4 has five Role variants (UI design v3 decision 5). At line 144, "(Report3 v3 design, D4)": the menus
   come from the UI design v3 decisions (brief §3) and D4, not from Report3. Suggested fix: "(Role = 5 variants)" and
   "(UI design v3, D4)".
6. **Nit** — `frontend/guidelines/01-project-structure.md:34`: "sign-out" is listed like a file of `shared/auth`. There
   is no such file: `useSignOut` is in `useSession.ts` (only `sign-out.test.tsx` exists). Suggested fix:
   "useSession.ts (GET /me, all roles, useSignOut)".
7. **Nit** — `frontend/guidelines/13-decision-log.md:217`: "the `{count}` of MSG14 / MSG16 / MSG17". MSG17 has no
   `{count}`. The old MSG21 had none either, so this is old text, renumbered correctly. Suggested fix: "of MSG14 /
   MSG16".

## Assumptions made
- **Issue 1 decides the verdict.** AC-010.1–.4 pass word for word. I failed the task because STORY-010 asks that
  the guidelines "describe the Report3 app so that I build the right thing", and the dispatcher asked me to check
  that the docs do not contradict Report3. The sentence was already in the old docs, and gap report E does not
  name it. Judgment call for the user: accept this as a fix for Task 5 (my recommendation, two sentences), or pass
  the task and log the fix as a quick change.
- **Project filter (issue 3).** The ban comes from UI decisions 04/10. Gap report B.2 says that the v3 Figma adds a
  Project filter to 7.1, 7.4 and 7.5. The ban stays a project rule until the FE Lead changes it. Judgment call for
  the user: keep the ban until `ticket-review`, or narrow it now (for example, "no Department filter").
- AC-010.1 says that "the files" name 35 web screens and the five roles. I read this as the set of files. The test
  checks AGENTS.md, and guidelines 01, 02 and 08 say 35 as well.
- The MSG test (AC-010.4) checks only that a code is in the catalog. An old SRS v2 number that is still a Report3
  code (MSG16, MSG18, MSG90…) would pass with the wrong meaning. So I checked every meaning by hand against
  `report3-messages.json`, with the old catalog of `a5ee1a7` beside it. All are right.
- "SRS v3460" (AGENTS.md:21) is a real file name (`Report/T-Solve_Report3_Software_Requirement_Specification_EN_v3460.docx`),
  so I did not count it.
- 08 §1 lists Workspace Setup and My Profile as the screens open before setup. AC-003.2 also names Login. A
  signed-in Admin who opens Login goes to the landing page and then to Workspace Setup, so this is not a finding.

## Test Report
See `task-5-guidelines-test-report.md`. Results: format:check pass · lint pass · typecheck pass · test 397/397
pass (22 files, 36.2 s, first run, no timeout) · build pass · `generate:api` no diff · trace STORY-010 4/4 ·
the doc test's regexes fail on the old docs for each criterion · coverage not measured (the project has no
coverage script).
