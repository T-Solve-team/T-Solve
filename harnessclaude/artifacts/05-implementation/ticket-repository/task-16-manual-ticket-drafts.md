# Task 16: manual-ticket-drafts

**Module:** ticket-repository · **Assignee:** harnessclaude-developer (Claude) · **Status:** evaluating

## Session 2026-11-14
### Working on: manual-ticket-drafts
- POST /imports/manual/drafts (create / update by draftId), GET /imports/manual/drafts/{id}; save with draftId deletes the Draft in its transaction.
- Manual Draft = ImportPreview source MANUAL + row 1; ImportPreviewRow.rewrite(Values) (ticket_id, title, description, resolved_date now updatable); ImportPreview.moveManualDraft lets a Manual Draft change Project.
- ImportDraftServiceImpl.delete accepts any source but CSV; preview endpoints and Confirm answer 404 for a MANUAL preview (otherwise Confirm would import a Draft unchecked).
- Contract: 400 added to saveManualTicketDraft (missing projectId → MSG01).
- Verify: mvn verify 1550 unit + 490 IT, 0 failures (developer); orchestrator re-run green (see Task 15).
- Commit: default@2bd4f203
