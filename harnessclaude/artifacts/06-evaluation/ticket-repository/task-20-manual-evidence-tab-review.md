---
module: ticket-repository
feature: manual-evidence-tab
task: 20
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 20: Evidence on the Manual entry tab

## Summary
Commit default@ad87c786 adds the kit component `shared/ui/EvidenceUpload`, which is controlled by
`{ keptFiles, newFiles }`. It uses antd `Upload.Dragger`, and `beforeUpload` returns `LIST_IGNORE`, so nothing is
uploaded automatically. It checks extension and size against MSG34 and the count against MSG72 (kept files included),
lists every file, and gives each file a Remove button. `ManualEntryForm` puts the field after the Solution sections.
Both writes send `files: newFiles` through the generated multipart client. `keepEvidenceIds` (the ids of the kept
files) is sent only when there is a `draftId`. A reopened Draft's `evidence` is listed as kept files. After a
successful Save as draft, the form reloads the Draft, so files just sent become kept ids and are never sent again; the
form blocks sending until that reload is done. The MSW handlers mimic MSG34 and MSG72 and keep the Draft's Evidence.

Everything in AC-006.22 behaves as the spec says, and the web app matches the Task 19 contract. All tests, typecheck,
Prettier and ESLint pass. No CRITICAL findings. **PASS.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | See the focus checks below. |
| Completeness | pass | Every file in the Task 20 list is created or changed. |
| Correctness | pass | 172/172 in `src/features/ticket-import` + `src/shared/ui`; typecheck, Prettier, ESLint clean on the changed paths. |
| Integration | pass | The generated `createManualTicket` / `saveManualTicketDraft` append `entry` as a JSON string and each file as `files`. The backend reads `@RequestPart("entry") String` (`ManualTicketController.java:55,70`). Server `fieldErrors` on `files` land on the `files` Form.Item. A 503 MSG06 has no fieldErrors, so it is shown in the Alert. |

### Focus checks
- **List / add / remove.** Kept files and new files are listed by name with `Remove <name>` buttons
  (`EvidenceUpload.tsx:97-124`). Several files at once work: `beforeUpload` checks the whole batch once, on its first
  file (`:86-89`).
- **MSG72 for a 6th file, kept files counted.** `value.keptFiles.length + newFiles.length >= maxFiles` (`:61`). In the
  test, 1 kept file + 5 picked → 4 accepted and MSG72 shown.
- **MSG34.** Extension check is case-insensitive against the D19 list, and `size > 10 MB` fails (exactly 10 MB passes,
  tested). The `filter: 'native'` accept means a dropped file of another type still reaches the check.
- **Reopened Draft.** `toValues` → `draftEvidence` maps `evidenceId/fileName/fileSize` to kept files (`ManualEntryForm.tsx:69-78`).
- **keepEvidenceIds after removing a kept file.** It is built from the current `keptFiles` (`:130-132`), so a removed id
  is dropped. The test checks `keepEvidenceIds` equals the earlier ids minus the removed one, and the reopened Draft no
  longer lists it.
- **New files never sent twice.** After a Save as draft, `reloadEvidence` replaces `files` with the stored Draft
  (`newFiles: []`, all ids kept). `evidenceReloadId` disables Save and Save as draft and blocks `send` until the reload
  succeeds. If the reload fails, an ErrorState with Retry is shown and sending stays blocked (`:182-194`, `:229-231`).
  Tested for Save as draft twice (second body has 0 files and 2 kept ids). Save as draft then Save uses the same path,
  but no test covers it (Info 3).
- **Cleared after Save.** `form.resetFields()`, and a reopened Draft remounts through the URL change. Tested: the
  file names are gone after MSG32. See Info 1 for a leftover client error message.
- **Kept after a rejected save (AC-006.14).** `onError` never touches `files`. Tested with a server MSG34: the file stays
  listed, and the error appears in the Evidence Form.Item with no Alert.
- **No auto upload.** `LIST_IGNORE` from `beforeUpload`. Tested with fetch and XHR spies.
- **Accessible remove buttons.** Real `Button`s, with visible text "Remove" and accessible name `Remove <file name>`.
  The visible label (`FormField label="Evidence"`) targets the file input through `id`.
- **MSG catalog only.** MSG34 and MSG72 come from `msg(...)`. Other text is UI labels ("Evidence", "Remove").
- **Kit conventions.** It lives in `shared/ui/EvidenceUpload` with `index.ts` and a re-export from `shared/ui/index.ts`.
  It does not fetch and has no business rules beyond the upload limits that guideline 07 places in the kit. Limits and
  `accept` are props with the D19 defaults. Unlike `CsvUpload`, it is a bare control placed inside `FormField`, so
  callers can choose their own field name and label. This is reasonable. See Info 2 for what keeps it from being fully
  generic.

## Traceability
`sdd.ts trace ticket-repository STORY-006`: **22/22**. AC-006.22 is traced in `EvidenceUpload.test.tsx` and
`ManualEntryForm.test.tsx`; AC-006.16/18/19 also gain web-app tests in `ManualEntryForm.test.tsx`. Pre-existing warning:
"AC-008.1 in tests but not in spec".

## Issues

CRITICAL: none.

Informational:
1. **The client-side MSG34/MSG72 text outlives a successful Save.** `validationError` is local state
   (`EvidenceUpload.tsx:48`), and only `add` and `remove` clear it. Here is how it stays: on a new (not reopened) form, pick 6 files,
   which shows MSG72 and keeps 5. Then Save succeeds. `ManualEntryFields` is not remounted, because it has no key when
   there is no `draftId`. `resetFields()` empties the list, but MSG72 is still shown under an empty Evidence field.
   That goes against "clear the form" in AC-006.14. A reload after Save as draft also leaves it. Suggested fix: clear
   the error when `value` changes from outside, or report it like `CsvUpload` does, as Form.Item errors through
   `form.setFields` (`resetFields` clears those). That second option also fixes the next point.
2. **The client error is not part of the field.** It is a plain `Typography.Text` (`:125-127`), not a Form.Item error, so it
   is not tied to the input (no `aria-describedby`/live region). A server error on `files` and a client error can also
   show at the same time. Smaller points that keep it from being fully generic: the drop zone text is hard-coded as
   "Evidence" (`:95`), so it repeats the field label; there is no hint with the limits, while `CsvUpload` has one; the `error` prop is
   used by no caller; and `accept` must be a list of extensions (a MIME entry such as `image/*` would fail every
   file). Guideline 05 §accessibility is advisory (no NFR), so none of these blocks the task. The FE Lead review the
   commit asks for (AGENTS.md rule 10) is the place to settle them.
3. **Test gaps.** No test covers Save as draft followed by Save (the second request should carry no `files` and should
   carry `keepEvidenceIds`). No test covers the reload-failure path either (ErrorState shown, sends blocked, Retry
   works). Both work when traced by hand.
4. **Guideline 09 contradicts the spec.** It says "Existing Evidence is read-only (never removable)". D20 / AC-006.19
   let a Draft's Evidence be removed before Save. A Ticket's Evidence stays locked. The code follows the spec.
   Suggested: reword guideline 09 to "a Ticket's Evidence is read-only; a Draft's kept files can be removed".

## Assumptions made
- AC-006.22 "refuse a 6th file" is read as "keep the first files up to 5 and refuse the rest of the selection with
  MSG72", not "refuse the whole selection". The spec does not say which. **Judgment call for the user** if the
  whole selection should be refused.
- Info 1 is not a FAIL. The cleared form holds no values and sends none; only an old message stays on screen. **Judgment
  call for the user:** fix it now or leave it for the FE Lead review.
- Guideline 09's "never removable" (Info 4) is taken to mean a Ticket's Evidence. The spec wins where they differ.
- The `progress.json` status was not changed. The dispatcher limited this review to the review and test-report files.

## Test Report
See `task-20-manual-evidence-tab-test-report.md`.
