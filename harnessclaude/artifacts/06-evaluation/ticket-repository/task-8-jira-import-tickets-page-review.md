---
module: ticket-repository
feature: jira-import-tickets-page
task: 8
verdict: pass
attempt: 2
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-repository — Task 8: Import Tickets: Jira tab and Drafts list

## Summary
Code under review: `default@0de9c9f8`. The frontend has not changed since then (`git diff HEAD --stat` has no
`frontend/` file). Spec change under review: the uncommitted edit of
`harnessclaude/artifacts/03-modules/import-ticket-list/spec.md`, approved by the user. `sdd.ts diff import-ticket-list`
says "the spec is unchanged since it was approved (2026-11-07T14:06:32.236Z)".

**Attempt 1 (fail):** MAJOR (spec). `import-ticket-list` AC-008.1 still said "Jira and Manual entry disabled", and D13
said "No Drafts list". The code enables the Jira tab and shows the Drafts list, and the test named `AC-008.1`
asserted the opposite of its own criterion. Seven informational findings (issues 2–8) were also raised.

**Attempt 2 (pass):** the blocking finding is resolved by a spec change only, as recommended.
- AC-008.1 (`spec.md:153`) now reads "…with CSV selected and Manual entry disabled; the Jira tab and the Drafts list
  behave as `ticket-repository` STORY-001 and STORY-004 say".
- D13 (`:47`) says that Manual entry is disabled and that the Jira tab and the Drafts list come from
  `ticket-repository` (KAN-45). It also says "No Save as draft for a CSV preview".
- Out of Scope (`:247`) was updated to match, and the Changelog row of 2026-11-07 (`:26`) records the change and the
  user decision.

The frontend test `ImportTicketsPage.test.tsx:63-72` now matches the reworded criterion. Its title is "AC-008.1 shows
the tabs Jira, CSV and Manual entry with CSV selected and Manual entry disabled". It asserts that CSV is
`aria-selected`, that Jira is **not** `aria-disabled`, and that Manual entry is `aria-disabled`. The test no longer
contradicts the criterion it names. The second clause of AC-008.1 (the Jira tab and the Drafts list) points to
`ticket-repository` STORY-001 and STORY-004. Those have 19/19 coverage (attempt 1), including `JiraPullForm.test.tsx`
and `DraftsList.test.tsx`. The new D13 also agrees with `ticket-repository` AC-004.1, which allows Save as draft only
on a Jira preview, and with its Out of Scope ("Drafts of a CSV import … are a later Change").

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Unchanged since attempt 1. Every web-side criterion of STORY-001 and STORY-004 is met (the Stage 1 table of attempt 1). The page (`ImportTicketsPage.tsx:9-13, 19-23`) now matches AC-008.1 as reworded. |
| Completeness | pass | The specs and the code now agree. `import-ticket-list` STORY-008 has 8/8 criteria with tests, and `ticket-repository` STORY-001 and STORY-004 have 19/19. |
| Correctness | pass | `ImportTicketsPage.test.tsx`: 16/16 on Node 24 (reviewer run). The full frontend suite passed in attempt 1 (1431/1431), and no frontend file has changed since. |
| Integration | pass | Unchanged: only generated hooks are used (`usePullJiraImport`, `useListImportDrafts`, `useDeleteImportDraft`), and nothing under `src/shared` or `src/app` was edited. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-008`
```
  ✓ AC-008.1  frontend/src/features/ticket-import/pages/ImportTicketsPage.test.tsx
  ✓ AC-008.2  frontend/src/features/ticket-import/pages/ImportTicketsPage.test.tsx
  ✓ AC-008.3  frontend/src/features/ticket-import/pages/ImportTicketsPage.test.tsx
  ✓ AC-008.4  frontend/src/features/ticket-import/pages/ImportTicketsPage.test.tsx
  ✓ AC-008.5  frontend/src/features/ticket-import/pages/ImportTicketsPage.test.tsx
  ✓ AC-008.6  frontend/src/features/ticket-import/pages/ImportTicketsPage.test.tsx
  ✓ AC-008.7  frontend/src/features/ticket-import/pages/ImportTicketsPage.test.tsx
  ✓ AC-008.8  frontend/src/features/ticket-import/pages/ImportTicketsPage.test.tsx
trace import-ticket-list (STORY-008): 8/8 acceptance criteria have a test
```
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-001 STORY-004` → 19/19 (attempt 1, no code change since).

## Issues
None blocking.

Informational (none blocks the verdict; all are carried over from attempt 1 and are still open, because the frontend
has not changed):
1. `frontend/src/features/ticket-import/components/JiraPullForm.tsx:70-71`: every server error of type `field` is put
   under **Resolved to**, and `params.field_name` is ignored. Fix: call `applyApiErrors(form, error)` first (guideline 09 §3).
2. `JiraPullForm.tsx:88`: `tracker_name: 'Jira'` is hard-coded. `msg()` already fills it from `TRACKER_NAME`
   (guideline 06 §6).
3. `frontend/src/features/ticket-import/components/JiraPullForm.test.tsx:90-99`: the "date after today (Vietnam
   time)" half of AC-001.5 has no web test. Fix: freeze the clock with `vi.setSystemTime`.
4. `frontend/src/features/ticket-import/components/DraftsList.tsx:31-35`: after a 404 on Delete (AC-004.5), the
   list is not refreshed, so the row stays. Fix: invalidate in `onSettled`, inside a feature hook (guideline 06 §3).
5. `DraftsList.tsx:42-44`: `ellipsis: { showTitle: false }` hides the tooltip of a long Project label only to keep
   test selectors unique.
6. `DraftsList.tsx:46`: when the Project is no longer one the user manages, the Project cell shows the raw UUID.
7. `frontend/src/features/ticket-import/components/DraftsList.test.tsx:82`: the MSG04 assertion would also pass if
   the empty state showed MSG04.
8. New, minor: `ImportTicketsPage.test.tsx` does not check that the page renders the Drafts list or the Jira form
   inside its tab. Each of them is tested on its own, and the page is 28 lines of plain composition. Optional: add one
   assertion for the Drafts section heading in the AC-008.1 test.

## Assumptions made
1. The user approved the reworded `import-ticket-list` spec (`approvals.json`, `sdd.ts diff` shows no change since
   then). I take that approval as the user's decision on judgment call 1 of attempt 1.
2. I judge only the web part of AC-001.4 to AC-001.8, as in attempt 1.
3. **Open judgment call (from attempt 1, not answered):** is the raw UUID acceptable in the Drafts Project column
   when the Project is no longer managed (informational issue 6)? I did not count it as a failure.
4. **Open judgment call (from attempt 1, not answered):** no coverage was measured, because the frontend has no
   coverage script and the spec sets no target. I accepted that.
5. Keeping CSV as the default tab is correct, because the reworded AC-008.1 still says "CSV selected".

## Test Report
See `task-8-jira-import-tickets-page-test-report.md`. Reviewer run of attempt 2:
`npx vitest run src/features/ticket-import/pages/ImportTicketsPage.test.tsx` on Node v24.22.2 → 1 file, 16 tests,
16 passed. The other frontend checks (format, lint, typecheck, the full suite of 1431 tests, build, generate:api with
no diff) passed in attempt 1 on the same frontend code.
