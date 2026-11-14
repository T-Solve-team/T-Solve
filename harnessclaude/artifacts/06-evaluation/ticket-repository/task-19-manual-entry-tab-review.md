---
module: ticket-repository
feature: manual-entry-tab
task: 19
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-repository — Task 19: Manual entry tab and opening a Manual Draft

## Summary
- **The tab:** `ImportTicketsPage` now controls the active tab and shows `ManualEntryTab`.
- **The form:** `ManualEntryForm` has Project, Ticket ID (50), Title (200), Description (10,000), Resolved date
  (future days disabled against Vietnam "today"), the shared `SolutionFields`, and a native multi-file Evidence
  input. Before sending, it checks the D18 type, 10 MiB and 5 files in all (MSG34 by name, MSG72).
- **Save and Save as draft:** Save sends `saveManualEntry` (MSG32 toast, form reset). Save as draft validates only
  Project and files (MSG41) and stays on the saved Draft. Server field errors go under their fields through
  `applyApiErrors`, and values and files are kept. A 404 on a Draft returns to a new entry (edge 23).
- **Drafts list:** a Manual Draft shows type "Manual", and Open loads it into the tab with its kept files listed and
  removable. Every request with a `draftId` sends `keepEvidenceIds`. A double click is guarded by a ref.
- **Mocks:** MSW handlers cover the three endpoints.

Lint, typecheck, format and build pass, and the 12 ManualEntryForm tests and the DraftsList AC-007.3 test pass when
run alone. Verdict: pass, with two Medium findings for the user to judge.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass, with Issue 1 | AC-006.12: managed Projects, no future date, type, size and count checked before sending, rejection inline, values kept. AC-007.1/AC-007.2 on the web side. AC-007.4 Open fills the tab. AC-007.5 sends `draftId` + `keepEvidenceIds` and the new files only. AC-007.3 type Manual. "Active" Projects: see Issue 1 |
| Completeness | pass | Each criterion of the task has a named test in `ManualEntryForm.test.tsx` (12) or `DraftsList.test.tsx` (AC-007.3). Edge 23 is tested. Edge 22 is tested only as "several MSG01 at once", not MSG01 together with an MSG02 of another field |
| Correctness | pass, with Issue 2 | The full `npm run test` failed once on this task's own test `AC-006.12 checks the Evidence type and size before sending (MSG34)`: a 15 s timeout. Two solo reruns of the file pass 12/12, so it is a load timeout (BUG-213). But alone the test takes 11.99 s of its 15 s |
| Integration | pass | It uses the generated hooks (`useSaveManualEntry`, `useSaveManualDraft`, `useGetManualDraft`) and the shared `applyApiErrors`, `rules`, `FormField` and `AppForm`. The native `<input type="file">` is explained (no kit multi-upload; raw antd `Upload` is banned by guideline 07). `SolutionFields` is extracted unchanged from `SolutionFormModal` |

## Traceability
`sdd.ts trace ticket-repository STORY-006`: 12/12, AC-006.12 in `ManualEntryForm.test.tsx`. `STORY-007`: 9/9, with
AC-007.1, AC-007.2, AC-007.4 and AC-007.5 in `ManualEntryForm.test.tsx` and AC-007.3 in `DraftsList.test.tsx`.

## Issues
1. **[MEDIUM] The Project list is not limited to *Active* Projects (AC-006.12).**
   `frontend/src/features/ticket-import/hooks/useManagedProjects.ts:6-8` keeps the session's projects where
   `roleInProject === 'PROJECT_MANAGER'`.
   - Expected: "offer only the caller's Active managed Projects".
   - Actual: the session's `ProjectMembership` has no status field, so the web app cannot filter by Active. A
     non-Active managed Project would be offered, and Save would answer 403 MSG08. The test
     `AC-006.12 offers only the managed Projects…` checks only the role.
   - Context: the backend currently has no `ProjectMembershipLookup` implementation, so the session list comes only
     from the mocks. The CSV and Jira tabs share the same hook.
   - Suggested fix: either `/me` lists only Active Projects (and a test says so), or `ProjectMembership` gets a
     status the hook filters. Otherwise change the spec wording to "managed" plus the backend 403.
2. **[MEDIUM] A new test runs close to its timeout.**
   `ManualEntryForm.test.tsx:199` (`AC-006.12 checks the Evidence type and size before sending (MSG34)`) takes
   11,993 ms with only 3 test files running, against the 15,000 ms limit. It timed out in the full suite of this
   review. The other tests of the file take 1.7–9.97 s. BUG-213 already tracks suite-wide timeouts, but this test
   adds one more likely failure to every full run. Suggested fix: split it into one test per check (type, size and
   case), or give it a longer timeout with a comment pointing to BUG-213.
3. **[LOW] After "Save as draft" on an opened Draft, files can briefly show wrongly** (`ManualEntryForm.tsx:193-203`).
   `removedIds` is cleared and the new files are dropped from the form before the refetched Draft arrives. Until it
   does, the old Draft's file list is shown, including files just removed, and new files are missing. A Save clicked
   in that window sends the removed ids in `keepEvidenceIds`, gets 404, and resets the form (edge 23 path), losing the
   entered values. Suggested fix: set the Draft query data from the response, or keep the form disabled until the
   refetch ends.
4. **[LOW] A Draft's date that is not valid is lost when the Draft is saved again.** `ManualEntryForm.tsx:81-88`
   shows such a date (edge 25) as empty, and saving the Draft again then sends `null`, so the text is dropped. The
   web app cannot create such a date itself, so this matters only for Drafts made through the API.
5. **[LOW] The Solution total of 10,000 characters is not checked in the form.** A too-long Solution is found only by
   the server (MSG02 with no field), and shown as a toast, not next to a field. AC-006.12 lists only the Evidence
   checks as required before sending, so this is not a breach.

## Assumptions made
- **Open judgment call for the user (Issue 1):** the "Active" clause of AC-006.12 is treated as Medium, not blocking,
  because the session contract gives the web app no status to filter on and the backend rejects with 403. If the
  user reads the clause strictly, this feature should become `fixing`, or the spec should be changed.
- **Open judgment call for the user (Issue 2):** the full-suite failure is counted as a load timeout, not a failing
  test, because the file passed in two solo reruns (rule of the dispatch and of BUG-213). Given the 12 s solo time,
  the user may prefer the test be split before the commit.
- Edge 22 (blank Ticket ID and a 300-character title shown together) is counted as covered by the MSG01 and MSG02
  rules of the form, though no single test asserts both at once. Edge cases are not acceptance criteria.

## Test Report
`task-19-manual-entry-tab-test-report.md`, which refers to the shared run in
`task-14-manual-entry-contract-test-report.md`.
