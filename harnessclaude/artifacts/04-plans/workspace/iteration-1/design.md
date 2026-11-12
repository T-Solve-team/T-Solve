---
module: workspace
date: 2026-11-11
iteration: 1
---

# Design: workspace, iteration 1

Spec: `artifacts/03-modules/workspace/spec.md`
Master: `artifacts/04-plans/workspace/design.md`
Tasks: `artifacts/04-plans/workspace/iteration-1/tasks.md`

Iteration 1 is all of today's master design: KAN-120, UC-12. The master holds the service body, the repository SQL, both changesets in full and the file plan. Build from it; this file does not repeat them.

## Architecture Overview

`DepartmentController` (`@PreAuthorize` Admin, `@Valid`, 201) calls `DepartmentService.create`. `DepartmentServiceImpl`, in one `@Transactional` method: checks the bound Workspace, strips the name, inserts the Department (`ON CONFLICT DO NOTHING` with no target; no row returned means MSG43 on `name`), inserts the Space, records the audit entry, returns `DepartmentRefResponse`.

`WorkspaceSettingsPage` shows the "New Department" button and a Name-only `DataTable` from `useGetUserDepartments`. `NewDepartmentModal` copies `RejectTicketModal`. `useAddDepartment` invalidates the Departments query and shows MSG05.

## Data Model

No new table or column. Changesets, appended to `db.changelog-master.xml` after 026 (full text in the master):

- `027-workspace-audit-department-change.sql` replaces `ck_audit_log_entry_action`, adding `DEPARTMENT_CHANGE`.
- `028-workspace-department-name-lower-unique.sql` adds `uq_department_name_lower` on `department (lower(name))`. `uq_department_name` stays.

## Key Decisions

Chosen, alternatives and rationale for D4 to D22 are in the master's Key Decisions table. Those that shape the code of this iteration:

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| D7 Same name | Unique index on `lower(name)` | Existing constraint only | Case-insensitive and safe under concurrent requests |
| D8 Whitespace | Strip leading and trailing | Keep as typed | Matches Add User |
| D9 Length | 50 with MSG02, counted as received | 100 | Screen 2.2 and guideline 09 |
| D10 Space name | The Department name | Name + " Wiki" | The only documented behaviour |
| D11 Audit action | `DEPARTMENT_CHANGE` with `details.operation` | `DEPARTMENT_CREATE`; existing value | Reusable by UC-13 and UC-15; truthful log |
| D13 Screen scope | Button, form, Name-only table | Button only; all five columns | The Admin sees the result; existing endpoint |
| D14 Form | `FormModal` titled "New Department" | Inline form | The kit's pattern |
| D15 Persistence | JDBC repository | JPA entity | Race-safe insert; deliberate deviation from `backend/AGENTS.md` section 3 |
| D17 CSRF header | Out of KAN-120 | Quick change first | Affects every write; `shared/` needs the FE Lead |
| D19 Invalid body from a non-Admin | `@Valid` on the controller: 400 | Parse in the service so 403 wins | Nothing is written either way |
| D20 Case-variant code | Change the `UserListIT` fixture and the seeder clause | Leave them | The new index would break both |
| D21 `ACTION_LABELS` | Task 1 | Task 2 | Each commit must pass `typecheck` |
| D22 Whitespace and character | `strip()` and `length()` on the server | One Unicode-aware rule | No source defines them; the sides differ only for rare characters |

## File Structure

See the master's File Structure and the exact lists in `tasks.md`.

## External Dependencies
None new.

## Edge Cases & Risks

Dimensions analyzed: User types, Input extremes, Timing, Scale, State transitions, Error cascades, Authorization, Data integrity, Integration, Compliance · Skipped: Environment (`FormModal` and `DataTable` carry the labels, focus and keyboard behaviour), Business logic (no amounts, quotas or limits beyond the name rules)

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | Authorization | Signed-in non-Admin sends an allowed name | Critical | 403 MSG08, no rows (AC-001.12) | Task 1 |
| 2 | STORY-001 | Authorization | No signed-in user | High | 401 MSG07, service not called (AC-001.13) | Task 1 |
| 3 | STORY-001 | Authorization | Non-Admin sends an invalid body | Low | 400 from `@Valid`, no rows (D19) | none |
| 4 | STORY-001 | Authorization | Real browser POST has no CSRF header | Medium | 403 until the quick change (D17) | none |
| 5 | STORY-001 | Input extremes | Name missing, `""`, spaces only, or 51 spaces | High | 400 MSG01 on `name` (AC-001.5) | Task 1 |
| 6 | STORY-001 | Input extremes | Exactly 50 characters, then 51 | High | 201, then 400 MSG02 with `max_length` 50 (AC-001.1, AC-001.6) | Task 1 |
| 7 | STORY-001 | Input extremes | `"  IT  "` when `IT` exists | High | Stripped first, 409 MSG43 (AC-001.7) | Task 1 |
| 8 | STORY-001 | Input extremes | SQL text in the name | Medium | Stored as text through named parameters | none |
| 9 | STORY-001 | Data integrity | Name of a `DELETED` Department | High | 409 MSG43 (AC-001.8) | Task 1 |
| 10 | STORY-001 | Data integrity | Case-only difference, ASCII and Vietnamese (`Phòng IT`, `PHÒNG IT`) | High | 409 MSG43 (AC-001.9) | Task 1 |
| 11 | STORY-001 | Data integrity | Changeset 028 meets case-variant rows already stored | Medium | Liquibase stops at start; fix the data and restart. No production Workspace exists yet | none |
| 12 | STORY-001 | Data integrity | Admin creates `customer support`, then the demo profile restarts | Medium | Seeder's clause has no target, so the boot continues (D20) | none |
| 13 | STORY-001 | Timing | Two requests with one name at once | High | One 201, one 409 MSG43, one Department, Space and audit entry (AC-001.10) | Task 1 |
| 14 | STORY-001 | Error cascades | Audit write fails after both inserts | Critical | All rolled back, 500 MSG06 (AC-001.11) | Task 1 |
| 15 | STORY-001 | State transitions | Workspace not bound | High | 500 MSG06, no rows (AC-001.15) | Task 1 |
| 16 | STORY-001 | State transitions | Space is created with the Department | Critical | One Active Space, same name, no `space_manager` row (AC-001.2) | Task 1 |
| 17 | STORY-001 | User types | Same name in another Workspace | Critical | Allowed; the row is only in the bound database (AC-001.14) | Task 1 |
| 18 | STORY-001 | Compliance | Successful create | High | One audit entry with actor, action, target and details (AC-001.3) | Task 1 |
| 19 | STORY-001 | Compliance | Refused create (400, 403, 409) | High | No audit entry (AC-001.5 to AC-001.9, AC-001.12) | Task 1 |
| 20 | STORY-001 | Integration | `DEPARTMENT_CHANGE` missing from one of the four places | High | The IT write fails at the check; `typecheck` fails on the label (AC-001.16) | Task 1 |
| 21 | STORY-001 | Integration | New Department in the Add User list | High | `GET /users/departments` returns it (AC-001.4) | Task 1 |
| 22 | STORY-002 | Timing | Save clicked twice | High | One request; button busy (AC-002.8) | Task 2 |
| 23 | STORY-002 | Timing | Cancel while the request runs | Medium | Ignored until it settles, as `RejectTicketModal` (AC-002.14) | Task 2 |
| 24 | STORY-002 | Input extremes | Empty, spaces only, 51 characters in the browser | High | MSG01 or MSG02 below the field, no request, text kept (AC-002.6, AC-002.7) | Task 2 |
| 25 | STORY-002 | Input extremes | Name with spaces around it | High | Trimmed in the request (AC-002.8) | Task 2 |
| 26 | STORY-002 | Error cascades | 409 MSG43 from the server | High | Below the field, popup open, text kept (AC-002.10) | Task 2 |
| 27 | STORY-002 | Error cascades | 403 or 500 from the server | High | Message by code, popup open, text kept (AC-002.11) | Task 2 |
| 28 | STORY-002 | Error cascades | The Departments query fails | High | Error state with retry; button stays (AC-002.3) | Task 2 |
| 29 | STORY-002 | State transitions | Cancel, then reopen | High | No request, empty field (AC-002.12) | Task 2 |
| 30 | STORY-002 | State transitions | Success | High | Popup closed, MSG05, new row in the table (AC-002.9) | Task 2 |
| 31 | STORY-002 | Scale | No Department | Medium | MSG04 (AC-002.4) | Task 2 |
| 32 | STORY-002 | Scale | Many Departments | Low | The endpoint is unpaged and the table lists all; a Workspace has few Departments | none |
| 33 | STORY-002 | User types | Non-Admin opens the page | High | MSG08 page, no button (AC-002.13) | Task 2 |
| 34 | STORY-002 | Integration | Browser mock mode after a create | Low | MSG05 shows, the table does not change (see File Structure) | none |

Open questions for the spec owner: none.
