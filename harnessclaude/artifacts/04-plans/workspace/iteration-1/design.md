---
module: workspace
date: 2026-11-11
iteration: 1
---

# Design: workspace, iteration 1

Spec: `artifacts/03-modules/workspace/spec.md`
Master: `artifacts/04-plans/workspace/design.md`
Tasks: `artifacts/04-plans/workspace/iteration-1/tasks.md`

Iteration 1 is all of today's master design: KAN-120 (UC-12, Tasks 1 and 2) and KAN-121 (UC-13 rename, Tasks 3 and 4). The master holds the service bodies, the repository SQL, both changesets in full and the file plan. Build from it; this file does not repeat them. KAN-121's decisions are written `K121-D6` and so on (`kan-121-decisions.md`).

## Architecture Overview

`DepartmentController` (`@PreAuthorize` Admin, `@Valid`, 201) calls `DepartmentService.create`. `DepartmentServiceImpl`, in one `@Transactional` method: checks the bound Workspace, strips the name, inserts the Department (`ON CONFLICT DO NOTHING` with no target; no row returned means MSG43 on `name`), inserts the Space, records the audit entry, returns `DepartmentRefResponse`.

`WorkspaceSettingsPage` shows the "New Department" button and a Name-only `DataTable` from `useGetUserDepartments`. `NewDepartmentModal` copies `RejectTicketModal`. `useAddDepartment` invalidates the Departments query and shows MSG05.

KAN-121: `DepartmentController` gains `get` (GET `/{departmentId}`, 200) and `update` (PUT `/{departmentId}`, `@Valid UpdateDepartmentRequest`, 200), plus a regex route that answers a non-UUID id with 404 MSG04. `DepartmentServiceImpl.update`, in one `@Transactional` method: checks the bound Workspace, strips the name, locks the Active row (`FOR UPDATE`; none: 404 MSG04), returns at once if the name is unchanged, updates the Department (23505: MSG43 on `name`), updates the Space (no row: 500), records the audit entry with `before` and `after`. On the web, the 2.2 Name cell links to 2.3; `DepartmentDetailPage` reads `getDepartment` and holds `DepartmentNameForm` (inline Edit, Save, Cancel); `useRenameDepartment` shows MSG05 and invalidates every query.

## Data Model

No new table or column. Changesets, appended to `db.changelog-master.xml` after 026 (full text in the master):

- `027-workspace-audit-department-change.sql` replaces `ck_audit_log_entry_action`, adding `DEPARTMENT_CHANGE`.
- `028-workspace-department-name-lower-unique.sql` adds `uq_department_name_lower` on `department (lower(name))`. `uq_department_name` stays.

KAN-121 adds no changeset.

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
| K121-D6 Endpoint | `PUT /api/v1/departments/{departmentId}` | `PATCH`; `PUT .../name` | Every update in the contract is a `put` |
| K121-D7 Concurrent saves | `SELECT ... FOR UPDATE` | `version` column; no lock | True audit `before`; no changeset |
| K121-D8 Unknown, Deleted, malformed id | 404 MSG04, regex route | 409 for Deleted | Precondition "Active"; identity precedent |
| K121-D9 Taken name | Catch 23505 on `UPDATE` | Pre-check | Race-safe; the row never conflicts with itself |
| K121-D10 Space | Renamed in the same transaction; 0 rows is 500 | Create it; ignore | GBR-29 |
| K121-D11 Audit | `UPDATE`, `before`, `after`, `spaceId` | New action | No changeset |
| K121-D12 No change | 200, no write | Write anyway | No empty audit entries |
| K121-D13 DTO | `UpdateDepartmentRequest` | Reuse the create record | Naming rule |
| K121-D14 Refresh | `invalidateQueries()` | Known keys | No cross-feature knowledge |
| K121-D15 Read | `getDepartment` in `workspace` | Cached list; router state | Deep link and reload |
| K121-D16 Form | Inline on 2.3 | `FormModal` | "Name *" is a field of 2.3 |
| K121-D18 MSW | Seed copies of IT and HR | Own list only | 2.3 opens from 2.2 in mock mode |

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
| 35 | STORY-003 | Authorization | Non-Admin reads, or renames with an allowed name | Critical | 403 MSG08, no change (AC-003.18) | Task 3 |
| 36 | STORY-003 | Authorization | No signed-in user | High | 401 MSG07, service not called (AC-003.19) | Task 3 |
| 37 | STORY-003 | Authorization | Non-Admin sends an invalid body | Low | 400 from `@Valid`, no change (KAN-120 D19) | none |
| 38 | STORY-003 | Input extremes | Name missing, `""`, spaces only, 51 spaces, Unicode-blank | High | 400 MSG01 on `name` (AC-003.8) | Task 3 |
| 39 | STORY-003 | Input extremes | Exactly 50 characters, then 51 | High | 200, then 400 MSG02 with `max_length` 50 (AC-003.3, AC-003.9) | Task 3 |
| 40 | STORY-003 | Input extremes | Path id `abc` | High | 404 MSG04 for GET and PUT, not 400 or 500 (AC-003.2) | Task 3 |
| 41 | STORY-003 | Input extremes | `"  IT  "` sent for the Department already named `IT` | Medium | Stripped, equal: 200, no write, no audit (AC-003.14) | Task 3 |
| 42 | STORY-003 | Data integrity | Rename to another Active name, padded with spaces | High | 409 MSG43, old name kept, no audit (AC-003.10) | Task 3 |
| 43 | STORY-003 | Data integrity | Rename to a Deleted Department's name | High | 409 MSG43 (AC-003.11) | Task 3 |
| 44 | STORY-003 | Data integrity | Rename to `PHÒNG IT` when another is `Phòng IT` | High | 409 MSG43 (AC-003.12) | Task 3 |
| 45 | STORY-003 | Data integrity | Rename own `it support` to `IT Support` | High | 200, stored, Space renamed, audited (AC-003.13) | Task 3 |
| 46 | STORY-003 | State transitions | Rename a Deleted or unknown Department | High | 404 MSG04, no change (AC-003.2) | Task 3 |
| 47 | STORY-003 | State transitions | Rename keeps status, default Project and Projects | High | Columns and `project` rows unchanged (AC-003.6) | Task 3 |
| 48 | STORY-003 | Timing | Two renames of two Departments to one name at once | High | One 200, one 409 MSG43, one audit entry (AC-003.15) | Task 3 |
| 49 | STORY-003 | Timing | Two renames of the same Department at once | High | Both 200 in order, second `before` equals first `after` (AC-003.16) | Task 3 |
| 50 | STORY-003 | Error cascades | Department has no Space, or the audit write fails | Critical | Old name kept, 500 MSG06 (AC-003.17) | Task 3 |
| 51 | STORY-003 | State transitions | No Workspace bound | High | 500 MSG06, no change (AC-003.21) | Task 3 |
| 52 | STORY-003 | User types | Name held only in another Workspace | Critical | Allowed; only the bound database changes (AC-003.20) | Task 3 |
| 53 | STORY-003 | Compliance | Successful rename | High | One audit entry with `before`, `after` and `spaceId`; the create entry keeps the old name (AC-003.5, AC-003.22) | Task 3 |
| 54 | STORY-003 | Compliance | Refused rename (400, 403, 404, 409) | High | No audit entry (AC-003.2, AC-003.8 to AC-003.12, AC-003.18) | Task 3 |
| 55 | STORY-003 | Integration | Renamed Department in the Add User list and on 2.3 | High | Both GETs return the new name (AC-003.7) | Task 3 |
| 56 | STORY-003 | Data integrity | Demo seeder boots after a rename | Low | A fresh seeded Department is added (K121-D20) | none |
| 57 | STORY-004 | Timing | Save clicked twice | High | One PUT (AC-004.8) | Task 4 |
| 58 | STORY-004 | Timing | Cancel while the PUT runs | Medium | Ignored (AC-004.13) | Task 4 |
| 59 | STORY-004 | Input extremes | Empty, spaces only, 51 characters | High | MSG01 or MSG02 below the field, no request, text kept (AC-004.6, AC-004.7) | Task 4 |
| 60 | STORY-004 | Input extremes | Name with spaces around it | High | Trimmed in the PUT (AC-004.8) | Task 4 |
| 61 | STORY-004 | Error cascades | 409 MSG43 | High | Below the field, form open, text kept (AC-004.10) | Task 4 |
| 62 | STORY-004 | Error cascades | 404 (deleted meanwhile), 403 or 500 on Save | High | Message by code, form open (AC-004.11) | Task 4 |
| 63 | STORY-004 | Error cascades | GET fails or answers 404 | High | Error state with the code's message and retry, no Edit (AC-004.4) | Task 4 |
| 64 | STORY-004 | State transitions | Cancel, then Edit again | High | No request; field holds the current name, not the typed one (AC-004.12) | Task 4 |
| 65 | STORY-004 | State transitions | Success | High | Form closed, MSG05, new name on 2.3, every query refetched (AC-004.9) | Task 4 |
| 66 | STORY-004 | Environment | Deep link or reload on 2.3 | High | Reads by id from the URL (AC-004.2) | Task 4 |
| 67 | STORY-004 | User types | Non-Admin opens 2.3 | High | MSG08 page, no Edit (AC-004.14) | Task 4 |
| 68 | STORY-004 | Integration | Browser mock mode after a rename | Low | 2.3 shows the new name; the 2.2 table does not (Interim limit 6) | none |

Rows 35 to 68 (KAN-121): Business logic and Scale skipped (no amounts, and one row at a time); Environment covers the deep link only.

Open questions for the spec owner: none.
