# Task 31: Publish one preview row

**Module:** import-ticket-list
**Assignee:** Đặng Nhật Minh (with Claude)
**Status:** evaluating

## Session 2026-11-19

### Working on: publish-preview-row
- Change Mode for KAN-373: STORY-017 (AC-017.1 to AC-017.10), D21 — one Import Batch per published row (user decision).
- Contract first: `POST /imports/previews/{previewId}/rows/{rowNo}/publish` (`publishImportPreviewRow`); client regenerated.
- `ImportConfirmServiceImpl.publishRow`: MSG40 then 403 before the transaction; under the preview lock a WITHOUT_SOLUTION preview and a Valid row or MSG06; a taken Ticket ID → 409 MSG33 with no Failed batch; one Completed batch (1, 0, 0), Ticket, approve decision, row deleted, audit, `TicketsPublishedEvent`.
- Branch: `feat/KAN-373-import-publish-row` (team rule: one branch per Jira issue) instead of `feat/import-ticket-list`.
- Commit: default@80cece71

### Verification
- `mvn test -Dtest='vn.tsolve.ticket.**.*Test'`: 413 run, 0 failures. `mvn spotless:check`: OK.
- `mvn -Dapi.version=1.44 verify`: not proven — Docker Desktop was not running, so 185 Testcontainers tests (identity, session) could not load their context; no `vn.tsolve.ticket` test failed. ITs to re-run with Docker up.
- `sdd.ts trace import-ticket-list STORY-017`: 10/10 criteria have a test.

### Next up
apply-and-publish-popup (Task 32)
