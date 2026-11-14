# Task 17: manual-entry-tab

**Module:** ticket-repository · **Assignee:** harnessclaude-developer (Claude; Codex was out of quota) · **Status:** evaluating

## Session 2026-11-14
### Working on: manual-entry-tab
- ManualEntryForm on the enabled Manual entry tab; tab from ?tab=, Draft from ?draftId= (useGetManualTicketDraft); Save → MSG32 + reset + draftId cleared; Save as draft → MSG41, draftId kept; fieldErrors under fields (solution → under Symptoms), other errors as an Alert like the Jira/CSV tabs.
- SolutionFields exported from SolutionFormModal and reused (behavior unchanged). DraftsList: MANUAL Open → /imports?tab=manual&draftId=N. MSW handlers for the three endpoints.
- import-ticket-list AC-008.1 said "Manual entry disabled": reworded in its spec (Changelog 2026-11-14, approved by the user) and its test updated through a quick change (closed).
- Verify (orchestrator): format:check OK, lint 0 errors (1 old warning), typecheck OK, test 89 files / 1546 passed, build OK, generate:api no diff.
- Commit: default@1f040bed

## Session 2026-11-14 (fixing, attempt 1 → 2)
- Developer: Codex (gpt-6.1-sol, high). Review attempt 1 FAIL: stale Manual Draft cache (CRITICAL, AC-006.11).
- Fixed: removeQueries of the Draft after Save as draft and after Save; form kept when a Draft refetch fails; field errors of unknown fields shown in the Alert; unknown ?tab falls back to CSV; manual save mock answers 201. Not fixed (decided): a Draft that answers 404 leaves the error screen (fixing it needs UI text the SRS does not give).
- Files opened by a quick change (closed): the 4 files are also under the completed import-ticket-list paths.
- Verify (orchestrator): format:check, lint, typecheck OK; vitest src/features/ticket-import 123/123; build OK; generate:api no diff.
- Commit: default@d6975188
