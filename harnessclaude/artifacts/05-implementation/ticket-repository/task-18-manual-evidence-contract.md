# Task 18: manual-evidence-contract

**Module:** ticket-repository · **Assignee:** Codex (gpt-6.1-sol, high) · **Status:** evaluating

## Session 2026-11-14
### Working on: manual-evidence-contract
- Contract: createManualTicket and saveManualTicketDraft take multipart/form-data (part `entry` = ManualTicketRequest JSON, part `files` 0-5 binaries); 503 Problem on both; ManualTicketRequest.keepEvidenceIds (uuid[] | null); ManualTicketDraft.evidence (ManualEvidence[], required). generate:api run.
- Frontend: ManualEntryForm sends `{ entry, files: [] }`; MSW handlers read formData; Draft answers carry `evidence: []`.
- Backend: changeset 031-manual-entry-evidence (evidence.storage_key, import_preview_evidence + index, rollback); DTOs ManualEvidence, keepEvidenceIds, ManualTicketDraft.evidence (getDraft returns List.of() until Task 19); TicketEvidence.of + file_type/upload_date/storage_key; ImportPreviewEvidence entity + repository. Controllers unchanged (Task 19).
- Orchestrator fix: ReviewStorageIT (BUG_145) replays main without the review changesets; 031 needs the review `evidence` table, so it is skipped there like 030.
- Note for Task 19: the generated client appends `entry` as a plain string (no part Content-Type), so the controller must take `@RequestPart("entry") String` and parse it with ObjectMapper.
- Verify (orchestrator): backend unit Manual*/Ticket*/ImportDraft* + ITs (ManualTicketControllerIT, ImportConfirmIT, review ITs, ReviewStorageIT) 0 failures; frontend format:check, lint, typecheck OK, vitest ticket-import + pages 123/123, build OK, generate:api no diff (Codex).
- Commit: default@ef484f0b
