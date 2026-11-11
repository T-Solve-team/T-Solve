---
module: ticket-repository
feature: jira-import-preview-page
task: 9
verdict: pass
attempt: 1
date: 2026-11-08
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-repository — Task 9: Web — Import Preview and popup for a Jira preview

## Summary
Commit `default@69f05767` makes the shared Import Preview handle a Jira preview. It shows "Jira import" and the
date range instead of the file name, hides the Error card, labels the button Finish (disabled per AC-002.10), adds
Save as draft (MSG41), shows MSG38 after Finish, and returns to Import Tickets on a 404 (design Edge 10). The popup
and the Solution form show the comments and the Jira link. The CSV paths do not change. Every new branch depends on
`source === 'JIRA_IMPORT'` or on `sourceUrl`, the CSV test file is unchanged and passes 53/53, and the CSV trace is
33/33. All checks pass, and the trace shows 28/28 criteria with a test. There is no CRITICAL finding and no
High/Critical security finding. The verdict is **pass**.

Two Major points need a decision from the user. I did not count either as a failure, because the spec makes the
first wait for a team change and does not clearly cover the second (see Assumptions):
- MSG35 shows a raw `{count}` to the user.
- On a reopened Draft, Fill solution and Remove change the saved Draft at once, while MSG37 says unsaved changes will
  be lost.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.1: the Summary shows Jira import, the date range, and the Valid/Removed/Needs Solution counts, and each row shows its key, summary, assignee, date and result. AC-002.2: the popup shows comments (author · date, then the text) and the link, both read-only and while writing. AC-002.6: MSG36 shows the replace count, and nothing is sent before OK. AC-002.10: Finish is disabled while `needsSolutionCount > 0` or `validCount === 0`. This works because the backend gives a Jira preview `importType = WITHOUT_SOLUTION` (`ImportPreview.java:83`). AC-002.11: Cancel and leaving ask MSG37, and OK sends no request. AC-003.1 and AC-003.2: MSG32, then MSG38 with the keys. AC-004.1: MSG41, and the user stays on the page. Edge 10: a 404 on a re-read or on any of the four mutations goes back without MSG37. Issues 1 and 2 are open judgment calls |
| Completeness | pass | Task 9 is fully delivered. Every web criterion of STORY-002/003/004 has a test that names it. Issue 3 is a small gap in Edge 10 (row reads) |
| Correctness | pass | format:check, lint (0 errors), typecheck and build pass. The full test suite had 1479/1480 pass: the one failure is the known load timeout in `KnowledgeChatbotPage`, which passes 61/61 when run alone. ticket-import passes 106/106, and generate:api leaves no diff |
| Integration | pass | The fields used (`source`, `resolvedFrom/To`, `sourceUrl`, `comments`, `notice`, `useSaveImportDraft`, `getListImportDraftsQueryKey`) match the generated client and the backend DTOs (`ImportConfirmServiceImpl.java:134-145` sends `notice` MSG38 with `count` and `issue_keys`). The shared `SolutionFormModal` gains only an optional `onError`, and `jiraSnapshotItems` returns `[]` for a CSV row, so KAN-31 behaviour does not change |

## Traceability
```
bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-002 STORY-003 STORY-004
  ✓ AC-002.1  JiraImportPreviewPage.test.tsx, JiraPreviewServiceTest.java
  ✓ AC-002.2  JiraImportPreviewPage.test.tsx, JiraPreviewServiceTest.java
  ✓ AC-002.3  JiraPreviewServiceTest.java, JiraPreviewControllerTest.java
  ✓ AC-002.4  JiraPreviewControllerTest.java
  ✓ AC-002.5  JiraPreviewServiceTest.java, JiraPreviewControllerTest.java
  ✓ AC-002.6  JiraImportPreviewPage.test.tsx
  ✓ AC-002.7  JiraPreviewServiceTest.java
  ✓ AC-002.8  JiraImportPreviewPage.test.tsx, JiraPreviewServiceTest.java, JiraPreviewControllerTest.java
  ✓ AC-002.9  JiraPreviewServiceTest.java, JiraPreviewControllerTest.java
  ✓ AC-002.10 JiraImportPreviewPage.test.tsx
  ✓ AC-002.11 JiraImportPreviewPage.test.tsx
  ✓ AC-003.1  JiraImportPreviewPage.test.tsx, JiraFinishTest.java
  ✓ AC-003.2  JiraImportPreviewPage.test.tsx, JiraFinishTest.java, JiraFinishIT.java
  ✓ AC-003.3 … AC-003.10  JiraFinishTest.java (+ JiraFinishIT.java for 003.4, 003.8, 003.10)
  ✓ AC-004.1  JiraImportPreviewPage.test.tsx, ImportDraftServiceImplTest.java, ImportDraftControllerTest.java
  ✓ AC-004.2 … AC-004.5  DraftsList.test.tsx, ImportDraftServiceImplTest.java (+ controller)
  ✓ AC-004.6  ImportDraftRepositoryIT.java
  ✓ AC-004.7  ImportDraftServiceImplTest.java
trace ticket-repository (STORY-002, STORY-003, STORY-004): 28/28 acceptance criteria have a test
```
Scout (impact on the CSV screens): `trace import-ticket-list STORY-009 STORY-010 STORY-013 STORY-014 STORY-015 STORY-016` → 33/33.
The commit does not change `ImportPreviewPage.test.tsx` (CSV), and it passes 53/53. The mock handlers' mutation
endpoints now use `findPreview`, which checks `isCurrent` first, so the CSV mock behaviour does not change.

## Issues
Pass, so none of these block. They are ordered by severity.

1. **MAJOR (open judgment call): MSG35 shows a raw `{count}` placeholder.**
   - Where: `frontend/src/features/ticket-import/pages/ImportPreviewPage.tsx:161-163` renders
     `msg(message.code, message.params)`. The backend sends MSG35 with no params
     (`backend/.../ticket/dto/ImportPreviewResponse.java:44`), and the catalog text
     (`frontend/src/shared/messages/catalog.ts:97-100`) still contains `{count}`.
   - Expected (AC-001.3, D8): MSG35 with no count.
   - Actual: the user reads "Showing the first 100 issues. {count} more match this date range; finish this import,
     then pull again." The test at `JiraImportPreviewPage.test.tsx:124-133` asserts `msg('MSG35')`, so it locks the
     placeholder in.
   - Why it is not a failure: D8 says the team changes Report3 first, then `report3-messages.json` and the catalog
     follow, and `catalog.test.ts` keeps the catalog equal to Report3. The team docs
     (`SEP490_G89/Report/DocumentWorkGuide/baseline/System_Messages.md:53`) still have `{count}`.
   - Suggested fix: the team updates Report3 MSG35, then run `npm run messages:report3` and update `catalog.ts`.
     Alternatively, the user allows a web-only text until then. This should be closed before Task 10 / module
     completion.
2. **MAJOR (open judgment call): edits on a reopened Draft are saved at once, so Cancel cannot "leave the Draft as
   saved".**
   - Where: Fill solution and Remove call `fill-solution` and `remove-rows` on the Draft's own rows (design: "Draft =
     the preview with `draft_saved_at` set"). Cancel (`ImportPreviewPage.tsx:197`) then sends nothing.
   - The text problem: MSG37 says "Unsaved changes will be lost unless you save a draft", but on a reopened Draft
     those changes are already in the Draft.
   - The test gap: `JiraImportPreviewPage.test.tsx:281-293` covers only a Cancel with no edits.
   - The two readings of AC-002.11: "without changing a saved Draft" can mean the Cancel itself sends nothing (met).
     It can also mean the Draft stays as it was last saved (not met, and the web app alone cannot meet it under the
     current design).
   - Suggested fix: a spec decision through Change Mode. Either state that edits on a reopened Draft are kept at once
     (and review the MSG37 text with the team), or require reopen to work on a copy (backend + web change).
3. **MINOR: Edge 10 does not cover a 404 on the row reads.**
   - Where: `leaveIfGone` / `isGone` (`ImportPreviewPage.tsx:72-81`) cover the preview query and the four mutations.
     They do not cover the rows query (`ImportPreviewTable.tsx:92`, e.g. going to page 2 of up to 5 pages) or the
     popup's row read (`ImportTicketPreviewModal.tsx:38`). Both show an in-place ErrorState with a useless Retry.
   - Expected (design Edge 10): "old tab gets 404 → web app returns to Import Tickets". The comment at line 73 says
     "any 404 about it".
   - Mitigation: refetch on window focus (`query-client.ts:66-69`, staleTime 30 s) usually re-reads the preview when
     the user comes back to the tab.
   - Suggested fix: pass an `onGone` callback to `ImportPreviewTable` and the popup, or check `rows.error?.status ===
     404` in the page.
4. **MINOR (judgment call): MSG38 is shown as an acknowledgement dialog, but the catalog types it `inline`.**
   - Where: `hooks/useImportPreview.ts:70`.
   - Guideline: guideline 06 §6 maps Inline to an in-page `Alert`. After Finish the page leaves, so inline would need
     the notice carried to Import Tickets.
   - The dialog does keep the skipped keys visible until the user closes it.
5. **MINOR (judgment call): MSG32 also appears when every issue was skipped.**
   - Where: `hooks/useImportPreview.ts:69` always toasts MSG32 ("0 ticket(s) published, N skipped").
   - Expected (AC-003.3): "return MSG38", while AC-003.2 says "together with MSG32".
   - The web app follows what the backend sends (it always sends MSG32), so this is consistent with Task 6, but the
     spec text differs.
6. **INFO: no text when an issue has no comments.**
   - Where: `components/jira-snapshot.tsx:17-30`.
   - Actual: an issue with no comments shows a "Comments" label with nothing next to it. No criterion covers this, but
     "No comments" or an empty-value dash would be clearer.
7. **INFO: CSS Module reused from another component.**
   - Where: `components/jira-snapshot.tsx:4` imports `ImportTicketPreviewModal.module.css` from another component.
   - Guideline 03: a CSS Module carries its component's name. This is acceptable because the helper is part of
     that popup, but it is a coupling.
8. **INFO (security, outside this commit): the issue key is not URL-encoded in the Jira link.**
   - Where: `backend/.../ticket/client/JiraRestIssueSource.java:140` joins `siteUrl + "/browse/" + key` without
     encoding. Triage encodes it (`SuggestionResultServiceImpl.java:156`).
   - Risk: none in practice. Jira keys are `[A-Z][A-Z0-9_]*-\d+`, and the site URL must be https (line 75).

**Security review** (link and comment rendering):
- Pass. `sourceUrl` comes from the backend, built from the https-only configured site + `/browse/{key}`, so the
  scheme is fixed.
- React 19 also blocks `javascript:` hrefs.
- The link uses `target="_blank"` with `rel="noreferrer"`, which implies `noopener`.
- Comment author, date and body are React text nodes (escaped, `white-space: pre-wrap`), with no
  `dangerouslySetInnerHTML`.
- No Critical or High finding.

**Assumptions made**
1. I took the reading of AC-002.11 where "without changing a saved Draft" means that the Cancel / leave action sends
   nothing. Edits made earlier on a reopened Draft are separate actions. **Open for the user** (issue 2).
2. I did not count the visible MSG35 `{count}` as a failure for Task 9, because spec D8 makes the catalog change
   wait for the team's Report3 change. **Open for the user** (issue 1): accept it until Report3 changes, or allow a
   web-only text now.
3. MSG38 as a dialog instead of inline is acceptable, because the page leaves on Finish (issue 4). **Open for the
   user.**
4. Moving silently to Import Tickets when a re-read answers 404 (no MSG04 toast) is acceptable. The spec does not
   say which message, and the design says only "returns to Import Tickets".
5. Keeping the CSV behaviour (ErrorState with MSG04, no return) when the first read answers 404 is acceptable. The
   page cannot know the preview was a Jira one, and a re-read in an open tab is the case that Edge 10 describes.
6. The labels "Resolved from:" / "Resolved to:", "Comments" and "Source link" are acceptable. The spec does not give
   label texts.
7. Not listing the Jira keys of the selected issues in the multi-issue Fill solution popup is acceptable. No
   criterion asks for it (SRS III.5.3 only).
8. No coverage figure: the frontend has no coverage script, and the spec sets no target. This is the same as the
   earlier reviews of this module.

## Test Report
See `task-9-jira-import-preview-page-test-report.md`.
- format:check: pass. lint: 0 errors, and 1 warning in an unrelated file. typecheck: pass. build: pass.
  generate:api: no diff.
- Full test suite: 1479/1480. The one failure is the `KnowledgeChatbotPage` load timeout, which passes 61/61 alone.
- `src/features/ticket-import`: 106/106. The Jira preview file passes 22/22, and the CSV preview file 53/53.
- Coverage: not measured.
