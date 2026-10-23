# Task 5: Frontend guidelines for Report3

**Module:** fe-foundation
**Assignee:** Trần Trung Hiếu
**Status:** fixing
**Iteration:** 1

---

## Session 2026-10-23 10:30

### Working on: guidelines (KAN-63, branch `chore/KAN-63-fe-foundation-docs`)
- Test first: `src/test/guidelines.test.ts` (AC-010.1–AC-010.4) failed in 14 places, then passed after the edits.
- MSG codes in `AGENTS.md` and the guidelines renumbered in one pass by meaning: each SRS v2 code to the Report3
  code with the same text (e.g. MSG16→13, MSG10→09, MSG65→10, MSG92→37). The test only catches codes missing
  from the catalog, so the mapping was checked text by text.
- `AGENTS.md`: 35 web screens, five roles (several per user), Report3 as business truth, rule 1 banned words.
- 01 tree and the 15 features · 02 registration counts, example, CODEOWNERS · 03 role values, roles table, Wiki /
  triage / chatbot terms, banned words, §6 MSG codes · 06 catalog, Report3 types, `useSession()` API,
  `tsolve.session.` prefix, sign-out · 07 menus and `menuFor(roles)` · 08 route map (35), landing, Workspace
  Setup, guards, `hasRole` / `hasAnyRole`, popups · 09, 10 codes and `renderApp` options · 12 walkthrough
  (`KAN-123`, `TICKET_RESOLVER`) · 13 renumbering note, FE-34 kept by D1, FE-40, FE-41, FE-42.
- Commit: default@02f8b86

### Decisions (FE Lead, 23/10)
- Keep "SRS" as the name of the requirements (Report3 is the SRS; screen layouts are SRS v3460 §III); only
  "SRS v2" / "SRS V.2" names change. `ScreenPlaceholder` keeps "SRS III".
- No left-out code numbers in the guidelines; the test allows no exception.
- Decision log: codes renumbered in place with one note; new entries FE-40–FE-42.
- `.claude/tsolve-project.md` follows in this PR as a quick change.

### Deviations from the plan
- Two old UC ids fixed by meaning (08: GBR-02 for another Department's ticket; 09: Report3 UC-30).
- Not taken from the gap report: a Triage History → Ticket Detail link (GBR-35: triage never links tickets).
- Footers say "verified 2026-10-23, KAN-63"; the guideline 12 walkthrough was not re-run (only its role, key and
  codes changed).

### Verify
- `npm run format:check`, `lint`, `typecheck`, `build`: exit 0; `generate:api`: no change.
- `npm run test`: 397/397 (22 files).
- `bun harnessclaude/hooks/sdd.ts trace fe-foundation`: 48/48 acceptance criteria have a test.

### Blockers
none

### Next up
Evaluate guidelines; the `.claude/tsolve-project.md` quick change.

## Session 2026-10-23 11:30

### Working on: guidelines — evaluation, attempt 1
- Independent review (harnessclaude-reviewer, attempt 1): **fail** — `06-evaluation/fe-foundation/task-5-guidelines-review.md`.
  Major: guideline 08 §7 says an import is never saved as a draft; Report3 GBR-26, UC-20, UC-21 and MSG37 allow
  a Draft (the sentence was already in the old docs).
- FE Lead (23/10): fix it in this task, with the docs nits 4–7 (`messages:report3` in the AGENTS.md scripts, 07
  kit and source wording, 01 `useSignOut`, 13 `{count}` of MSG17). Kept: the role check of the test as is; the
  Project filter ban until the `ticket-review` spec.
- `.claude/tsolve-project.md` updated to Report3 as a quick change: b638e04.
- Status: fixing.

### Next up
Fix the findings, then review attempt 2.
