---
module: ticket-repository
feature: manual-ticket-drafts
task: 16
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 16: Manual entry Drafts

## Summary
Manual Drafts (2bd4f203) are stored as an `import_preview` of source MANUAL with one row (D14): Save as draft creates
or rewrites it after only the Project check, reopen returns every field as typed, a successful save with `draftId`
deletes it in the save's transaction, and another user's / missing / non-Manual Drafts answer 404 MSG04. The new
MANUAL filters keep Manual Drafts out of the preview endpoints and Confirm; Jira/CSV behaviour is unchanged
(regression tests green). No CRITICAL findings. **PASS.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | See Stage 1 table. |
| Completeness | pass | AC-006.9–AC-006.13 all implemented and tested; no MISSING. |
| Correctness | pass | Tests green; transaction and 403/404 ordering reviewed below. |
| Integration | pass | Drafts list / Delete (STORY-004) serve MANUAL; preview endpoints, Fill solution, Remove rows, Confirm, POST `/previews/{id}/draft` answer 404 for MANUAL; CSV previews still 404 on Delete. |

### Stage 1 — spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 9 | AC-006.9 Save as draft, only Project checked, MSG41 + id | PASS | `ManualTicketServiceImpl.java:151-183`, `ImportPreview.manualDraft` (`ImportPreview.java:91-97`); unit `AC_006_9_…` ×3; IT `AC_006_9_to_AC_006_12_…` (blank id, 201-char title, "soon" date accepted; list shows MANUAL, rowCount 1) |
| 10 | AC-006.10 update same Draft, fields, Project, save time | PASS | `:177-181`, `ImportPreview.moveManualDraft`, `ImportPreviewRow.rewrite`; unit `AC_006_10_…`; IT asserts same id and `count(import_preview)=1` |
| 11 | AC-006.11 reopen returns Project + every field as saved | PASS | `:185-208`; unit `AC_006_11_…` ×2; IT reopens and checks fields |
| 12 | AC-006.12 delete Draft in the save transaction; keep it on rejection | PASS | `:100-102,136-139` (Draft locked, deleted after Ticket/decision, before commit); unit `AC_006_12_…` ×2 (409, 400, 403 keep it); IT: 400 keeps it, success removes preview and rows |
| 13 | AC-006.13 404 MSG04 for another user's / missing / non-Manual; Delete as AC-004.4/4.5 | PASS | `:210-214`; `ImportDraftServiceImpl.java:71-77`; `ImportPreviewServiceImpl.java:139-142`; `ImportConfirmServiceImpl.java:101-104`; unit `AC_006_13_…` ×3; IT other-PM GET/DELETE 404, owner DELETE 204 |

### Ordering / id-probing
- `getDraft`: owner+MANUAL filter (404) before the managesActive check (403) — another user cannot learn that an id
  exists. `saveDraft` / `create`: 403 on the requested Project before the Draft lookup; Draft lookup owner-filtered.
- Confirm: the outer `findByIdAndOwner` filter rejects MANUAL before the inner locked `importRows`
  (`ImportConfirmServiceImpl.java:236`), so Confirm can never import an unvalidated Manual Draft.
- `ImportDraftServiceImpl.delete`: was "JIRA_IMPORT and Draft", now "not CSV and Draft" — identical for Jira and CSV.
- Preview cleanup and "new pull/upload replaces the open preview" act only on `draft_saved_at IS NULL`; a Manual Draft
  is born with it set, so the partial unique index `uq_import_preview_owner_open` never counts it.

### Entity mutability
- `ImportPreviewRow` `ticket_id`, `title`, `description`, `resolved_date` now updatable (`ImportPreviewRow.java:32-44`);
  `ImportPreview.project_id` updatable (`ImportPreview.java:30-31`). Only `rewrite` / `moveManualDraft` change them;
  `moveManualDraft` guards source MANUAL. For Jira/CSV rows Hibernate now repeats the unchanged values in UPDATEs —
  no behaviour change (Jira/CSV fill-solution and remove-row tests green). `author` stays non-updatable.

## Traceability
```
✓ AC-006.9 … ✓ AC-006.13 (ManualTicketDraftTest, ManualTicketControllerIT)
trace ticket-repository (STORY-006): 13/14 — AC-006.14 (web app, Task 17) missing as expected
```

## Issues

CRITICAL: none.

Informational:
1. `ManualTicketServiceImpl.java:178` — Save as draft with `draftId` reads the Draft without a lock. If it races a
   successful Save of the same Draft (another tab), the Save deletes the row first and the Save-as-draft UPDATE then
   hits 0 rows → Hibernate stale-state → 500 instead of 404 MSG04 (design edge case "two tabs" covers Save vs Save
   only). Fix: use `previews.lockByIdAndOwner` here as `create` does.
2. `ManualTicketServiceImpl.java:162-172` — Draft fields are stored with no length bound (by design they are not
   validated). A PM can store arbitrarily large text in `import_preview_row`. Low risk (authenticated PM, own data).
   Fix (later): a generous cap (e.g. the Save limits ×N) or rely on a request-size limit.
3. `ManualTicketServiceImpl.java:195-207` — `ManualTicketDraft` is built by hand from entities; AGENTS.md §3 says
   entity → response goes through a MapStruct mapper. Same pattern as the existing `ImportDraftServiceImpl.list`.
   Fix: optional `ManualTicketDraftMapper`.
4. Between this task and Task 17, the web app's Drafts list "Open" for a MANUAL row still routes to the preview page,
   which now answers 404 — expected until Task 17 lands; do not release Tasks 14–16 without Task 17.
5. `ManualTicketDraftTest.AC_006_12_keepsTheDraftWhenTheSaveIsRejected` checks only that `delete` was not called;
   rollback of a delete is proven by the IT's 400 case only (rejections there happen before any write), which is
   sufficient given the delete comes after every rejecting check.

## Assumptions made
- Reopening a Manual Draft of a Project the caller no longer manages answers 403 (`:190-193`); the spec says nothing
  for Manual Drafts — taken from the design and the Jira precedent (AC-002.9). Judgment call, open.
- `saveManualTicketDraft` answers 400 MSG01 for a missing `projectId`; AC-006.9 implies a Project is required, the
  status/code is the developer's choice, reflected in the contract.

## Test Report
`task-16-manual-ticket-drafts-test-report.md`: scoped `mvn verify` — 297 unit + 29 IT, 0 failures, 0 errors,
Spotless clean; includes `ManualTicketDraftTest`, `ManualTicketControllerIT`, `ImportDraft*`, `ImportPreview*`,
`ImportConfirm*`, `Jira*`.
