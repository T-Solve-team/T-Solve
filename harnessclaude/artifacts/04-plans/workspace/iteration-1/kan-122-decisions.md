# KAN-122 and KAN-123 — decisions

Delete Department (UC-14 in Jira, UC-12 in SRS v3460) and Restore Department (UC-15 in Jira, UC-13 in SRS v3460). Source: SRS v3460 in `artifacts/00-baseline/` (UC-12, UC-13, screens 2.2, 2.3, 2.4; GBR-17; MSG45, MSG46, MSG51, MSG52), Jira KAN-122 and KAN-123.

| # | Date | Question | Options | Chosen | Why |
|---|------|----------|---------|--------|-----|
| D1 | 14/11 | What Delete does to Projects (asked) | SRS: Active Projects → Removed, default Project cleared, they stay Removed after Restore; leave Projects alone | SRS | The user chose it. GBR-17 and the Jira criteria both say so. Re-adding a Project (KAN-127) is not built, so restored Projects stay Removed in the demo |
| D2 | 14/11 | What `{count}` of MSG45 counts (asked) | every Project of the Department; Active ones only | every Project | The user chose it. All their tickets are hidden. `getDepartment` answers `projectCount` |
| D3 | 14/11 | How 2.2 shows Deleted Departments (asked) | "Show deleted" switch with a new `listDepartments`; a separate table | switch + `GET /api/v1/departments?includeDeleted` | The user chose it; it is the SRS 2.2 layout. The table reads one source, so AC-002.1, AC-002.4 and AC-002.9 are reworded. No "Migrate data" button (UC-66 has no story) |
| D4 | 14/11 | Endpoint shape | `DELETE /{id}` + `POST /{id}/restore`; `PUT` with a status field; `PATCH` | `DELETE` 204 and `POST .../restore` 200 `DepartmentRef` | REST verbs say what happens; restore is an action, not a field edit |
| D5 | 14/11 | Delete of a Deleted Department, restore of an Active one | 404 MSG04; 409 with a new code | 404 MSG04 | Same as K121-D8; no MSG exists for it and the state check lives in the same `FOR UPDATE` query |
| D6 | 14/11 | Two deletes or restores at once | `FOR UPDATE` on the row filtered by status; no lock | `FOR UPDATE` | Same as K121-D7: the second waits, then no longer matches and gets 404, so one audit entry |
| D7 | 14/11 | Audit details | `DEPARTMENT_CHANGE` with `operation` DELETE / RESTORE; new actions | `DEPARTMENT_CHANGE`, `{operation, name, spaceId, removedProjectIds}` for delete, `{operation, name, spaceId}` for restore | K120-D11 chose one action with `operation` so UC-14 and UC-15 need no changeset |
| D8 | 14/11 | Hiding tickets, Space, Search, Chatbot, AI Solution Suggestion | change each reader; rely on the readers' existing `status = 'ACTIVE'` filters | rely on the existing filters | Search (`TicketRankingRepository`), Chatbot and suggestion retrieval (`SolutionRetrievalRepository`), Dashboard, review expiry, scope checks and the Department and Space lists already filter on Active. Each is owned by its module; this story tests the Department and Space lists it can see (AC-005.8) |
| D9 | 14/11 | Access tokens of GBR-17 | build a credential table; note it | note it (Interim limit 7) | No token is stored anywhere yet |
| D10 | 14/11 | Delete button placement on 2.3 | next to Edit, hidden while editing; always shown | next to Edit, hidden while the rename form is open | One action at a time; SRS 2.3 lists Delete as a button of the screen |
| D11 | 14/11 | After a successful delete | stay on 2.3; go to 2.2 | go to 2.2 | 2.3 serves Active Departments only, so staying would show 404 |
| D12 | 14/11 | Deleted rows on 2.2 | link to 2.3; plain text | plain text with a "Deleted" tag | 2.3 answers 404 for a Deleted Department |
| D13 | 14/11 | Branch | `feat/workspace`; team rule | `feat/KAN-122-workspace-department-delete-restore`, both stories on it | Root CLAUDE.md team rule; one commit per feature, `(KAN-122)` / `(KAN-123)` in the header |
