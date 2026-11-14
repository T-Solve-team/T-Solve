---
module: ticket-repository
feature: manual-entry-drafts
task: 18
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-repository — Task 18: Drafts of a manual entry

## Summary
- **Save as draft:** `POST /imports/manual/drafts` (`ManualEntryServiceImpl.saveDraft`, `:171-206`) checks only that
  the Project is present (MSG01) and that the caller manages it and it is Active (403), plus the Evidence rules. It
  then creates `ImportPreview.manualDraft` with one row of raw values, or updates the Draft under
  `lockByIdAndOwner` (`:256-265`). Files removed from the Draft are deleted after the commit.
- **Reopen:** `GET /imports/manual/drafts/{id}` returns the fields as saved and the Evidence without storage keys.
- **Save from a Draft:** Save with `draftId` re-reads the Draft under the lock inside the transaction (`:150`). It
  makes the kept and new files the Ticket's Evidence, deletes the Draft in the same transaction, and deletes the
  removed files after the commit.
- **Delete:** `ImportDraftServiceImpl.delete` accepts MANUAL, takes the same lock, and deletes the files in
  `afterCommit`.
- **Preview endpoints:** `ImportPreviewServiceImpl.owned` and both lock reads, `ImportConfirmServiceImpl`
  (`:101`, `:235`) and `ImportDraftServiceImpl.save` (JIRA only) all answer 404 for a MANUAL preview.

Cross-user access is closed: every Manual Draft read and write looks the Draft up by id and owner.
`ManualEntryIT.aSaveWaitsForAnotherRequestOnTheSameDraftThenFindsItsKeptFileGone_AC_007_9` proves the lock on real
PostgreSQL. Verdict: pass, with one Medium finding.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass, with Issue 1 | AC-007.1 values kept as entered (edge 25: "2026-13-40" stays text); AC-007.2 MSG01, 403, MSG34 and MSG72 save nothing; AC-007.3 listed as MANUAL with 1 row; AC-007.4; AC-007.5 update, add and remove; AC-007.6 Draft deleted in the transaction, and a failed save leaves it; AC-007.7 files deleted after commit; AC-007.8; AC-007.9 |
| Completeness | pass | Every criterion has a named test: `ManualDraftServiceTest` (14), the AC-007.8 tests added to `ImportPreviewServiceImplTest` (preview, rows, row, remove-rows, fill-solution), `ImportConfirmServiceImplTest` (confirm) and `ImportDraftServiceImplTest`, plus `ManualEntryIT` (AC-007.3, edge 27 cleanup keeps the Draft, AC-007.6, AC-007.7, AC-007.9) |
| Correctness | pass | Every test passes in the full verify. `open-in-view: false` (`application.yml:25`), so the lock read in the transaction really reloads the Draft; it is not served stale from a request-wide persistence context |
| Integration | pass | The Drafts list and delete of STORY-004 now serve MANUAL. `CsvImportServiceImpl` and `JiraImportServiceImpl` `deleteByOwner` keep Drafts, and a Manual Draft is a Draft from its first save |

## Security (cross-user access, AC-007.8)
- `findByIdAndOwner` and `lockByIdAndOwner` (`ImportPreviewRepository.java:16-21`) scope every lookup by owner. That
  covers `getDraft` (`:210`), both `kept(…)` reads (`:133`, `:150`, `:179`, `:256`), and `delete` (`:78`). Another
  user's Draft id is 404 MSG04, the same answer as a missing one, so ids cannot be probed.
- A Manual Draft id on the Import Preview endpoints, Confirm or `POST /imports/previews/{id}/draft` is 404. A Jira or
  CSV preview id on the Manual endpoints is 404 (`manualDraft(...)`, `:296-298`).
- No Critical or High finding.

## Traceability
`sdd.ts trace ticket-repository STORY-007`: 9/9 acceptance criteria have a test (output in the test report).

## Issues
1. **[MEDIUM] The 5-file limit is not checked again under the lock when `keepEvidenceIds` is null (AC-007.5,
   AC-007.9).** `ManualEntryServiceImpl.java:133-134` and `:179-180` count the kept files from the Draft read without
   a lock. The lock reads at `:150` and `:256-260` then take every file of the locked Draft, which may hold more by
   then, without counting again.
   - Example: a Draft has 3 files. Two requests each add 2 files with `keepEvidenceIds` null. Both pass the pre-check
     (3 + 2 = 5). The lock runs them one after the other, and the Draft (or the Ticket made from it) ends with 7 files.
   - Expected: "the kept and new files together SHALL obey AC-006.7".
   - The web app always sends `keepEvidenceIds` with a `draftId` (`ManualEntryForm.tsx:148`). With an explicit list,
     the kept count can only shrink, so only direct API clients can do this.
   - Suggested fix: inside the transaction, after `kept(…, lockByIdAndOwner)`, throw MSG72 when
     `kept.files().size() + stored.size() > 5`. The existing catch already deletes the new keys.
2. **[LOW] Reopening a Manual Draft does not re-check the Project (NFR 3).** `ManualEntryServiceImpl.getDraft`
   (`:209-231`) checks only the owner. A PM who no longer manages the Draft's Project can still read their own Draft.
   A Jira Draft reopened through `GET /imports/previews/{id}` answers 403 in that case (AC-002.9). The data is the
   caller's own entry, so the risk is low. Suggested fix: `requireAccess(user, draft.getProjectId())` in `getDraft`,
   or a spec note that a Draft stays readable by its owner.
3. **[LOW] No test for a Manual Draft on `POST /imports/previews/{id}/draft`.** `ImportDraftServiceImpl.save` uses
   `jiraPreview(…)` (`:99-101`), which filters JIRA_IMPORT, so the code is right; only this one endpoint of the
   AC-007.8 list has no assertion. Suggested fix: one case in `ImportDraftServiceImplTest`.
4. **[LOW] Saving a Draft whose files cannot be stored answers MSG33 without a batch** (`:185-190`). That is correct,
   since no Import Batch is defined for Drafts. It is noted only because the contract table lists 500 for Save only.

## Assumptions made
- **Open judgment call for the user:** Issue 1 is Medium, not blocking: it needs concurrent requests that the web app
  never sends (it always names the files to keep). If the user wants the AC-007.5 limit guaranteed for every API
  client, this feature should become `fixing` with the check above.
- `saveDraft` requires an *Active* managed Project (`managesActive`), while AC-007.2 says only "managed". This is
  accepted as consistent with NFR 3 and the other import requests.

## Test Report
`task-18-manual-entry-drafts-test-report.md`, which refers to the shared run in
`task-14-manual-entry-contract-test-report.md`.
