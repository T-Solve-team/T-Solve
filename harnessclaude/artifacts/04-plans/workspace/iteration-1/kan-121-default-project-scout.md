# KAN-121 scout, part 2: the default Project of a Department (UC-13)

Module `workspace` (status `evaluating` on `origin/main`). Written 2026-11-17 for the Planner. Follows `kan-121-scout.md` (Q1) and decision D4 in `kan-121-decisions.md`.
Code facts are from `origin/main` at `db594997` (read with `git show` / `git grep`; the checkout was not touched). `B/` = `backend/src/main/java/vn/tsolve/`, `F/` = `frontend/src/`. SRS, Messages file and Rules file are the same files as in `kan-121-scout.md`.

**Jira KAN-121 criteria:** not read. No Atlassian tool was reachable from this agent. D4 records that the issue's criteria (added 01/11 by Hiếu Trần) ask for the default Project with GBR-40 and MSG157; the main session should read them before the spec is written.

## 1. Requirement text (what is left of UC-13)

- UC-13 step 2: "for a Department that handles support requests, selects the default Project (GBR-40), then clicks Save." Step 3: the system checks "that the default Project has the issue type Task and requires no other field (MSG157), and that the service account has Create Issues and Modify Reporter in it (MSG156)." (SRS 1002). Postcondition: "A Department with a default Project is offered in the Knowledge Chatbot issue form." (SRS 1004). No alternative flow for MSG156 or MSG157.
- Screen 2.3, "Default Project": "Drop-down list of the Active Projects of this Department; optional. Leave it empty for a Department that does not handle support requests (for example Marketing, Finance): it is never offered in the Knowledge Chatbot. ... (MSG157) ... (MSG156); issues are created there with the type Task (GBR-40)." (SRS 1657).
- Screen 2.2, token: "Create Issues and Modify Reporter are checked in a default Project when the Admin selects it (UC-13, MSG156)." (SRS 1637).
- GBR-40 (SRS 2524, Rules file 47): "Only Departments that handle support requests ... have a default Project"; "must have the issue type Task and require no field other than summary and description (MSG157)"; the Chatbot sets the reporter (GBR-19) and the label `tsolve-chatbot`; "When the default Project is removed (GBR-21) or its Department is deleted (GBR-17), the Department has no default Project and is not offered until the Admin chooses another one (UC-13)."
- GBR-17 (SRS 2501): deleting a Department sets its Projects to Removed. GBR-21 (SRS 2505): a removed Project becomes Removed.
- UC-58 (SRS 1445, 1453, 1455): precondition "The Department of the issue has a default Project"; the form offers "only Departments with a default Project".
- Messages file: MSG157 line 57, "Red text below the field": "{project_name} cannot receive Chatbot issues: it has no issue type Task or requires other fields. Choose another Project." MSG156 line 76, "Red text below the field": "The service account could not connect to {tracker_name} or lacks the required permissions."
- **There is no "support" flag** in the SRS entity (SRS 403: "default Project (support Departments only)") or the schema. A support Department is simply one with a default Project.

## 2. Schema (origin/main)

- `department.default_project_id BIGINT`, nullable, comment "NULL = no Chatbot issues" (`db/changelog/changes/002-tenant-project-suggestion.sql:10`); FK `fk_department_default_project` to `project(project_id)` (`:28-30`), no `ON DELETE` (Projects are never deleted). Other columns: `department_id BIGSERIAL`, `name`, `status` (`:9-13`), `public_id UUID` (`008-search-public-ids.sql:13-15`).
- `project`: `project_id BIGSERIAL`, `department_id BIGINT NOT NULL` FK, `jira_project_key VARCHAR(20) NOT NULL UNIQUE`, `field_mapping JSONB`, `done_status` (JSONB since `036-project-done-statuses.sql:4`), `review_range INT`, `status VARCHAR(20) DEFAULT 'ACTIVE'` (`002-...sql:16-25`), `public_id UUID` unique (`008-...sql:9-11`). **No name column.** Status values `ACTIVE`, `REMOVED` (`B/project/entity/ProjectStatus.java:5-6`), no check constraint.
- The FK holds the internal `BIGINT`; the API uses `project.public_id` (UUID), so the service translates. The UUID key alignment (KAN-248) is still Out of Scope (workspace spec).
- Nothing in `B/` reads or writes `default_project_id`. Tests touch it only: `DepartmentRenameIT.java:88,267-286` (AC-003.6 keeps it on rename), and two ITs clear it in cleanup.
- Latest changeset: `042-workspace-jira-cloud-id.sql` (last include in `db.changelog-master.xml`). **No changeset is needed** for this feature; audit action `DEPARTMENT_CHANGE` exists (`027-...`).
- Demo data: `MockUserListSeeder.java:65-67` seeds `SUP` (Active, Customer Support), `OPS` (Active, IT Operations), `OLD` (Removed, IT Operations).

## 3. Workspace backend to extend

| Piece | Where | Now |
|---|---|---|
| `DepartmentController` | `B/workspace/controller/DepartmentController.java` | `GET /{departmentId}` `getDepartment` `:51-58`, `PUT /{departmentId}` `updateDepartment` `:60-70`, regex route for non-UUID ids `:72-80`; all Admin, return identity's `DepartmentRefResponse` |
| `DepartmentService` | `B/workspace/service/DepartmentService.java:14-24` | `create`, `get`, `update` |
| `DepartmentServiceImpl.update` | `B/workspace/service/impl/DepartmentServiceImpl.java:56-82` | `@Transactional`; `lockActive` (`FOR UPDATE`); same name returns without write (`:63-65`, AC-003.14); renames Department and Space; audit `{operation: UPDATE, before: {name}, after: {name}, spaceId}` |
| `DepartmentRepository` | `B/workspace/repository/DepartmentRepository.java` | JDBC; `Current(id, name)` `:44`; `findActive`/`lockActive` `:46-56`; `renameDepartment` maps 23505 to MSG43 `:66-75`; `renameSpace` `:78-87` |
| `UpdateDepartmentRequest` | `B/workspace/dto/UpdateDepartmentRequest.java:6-15` | `{ name }` only |
| `DepartmentRefResponse(UUID departmentId, String name)` | `B/identity/dto/DepartmentRefResponse.java:5` | **identity's record**, used in 38 places (identity user list, project list, workspace). Adding fields to it changes identity and project responses |
| Contract | `frontend/openapi/tsolve-api.yaml` | path `/api/v1/departments/{departmentId}` `:846` (get + put), `UpdateDepartmentRequest` `:3496`, `DepartmentRef` `:3720` (shared with `ProjectList` `:3692`) |

Must add: a request field for the default Project, a read model that carries the current default Project and the choosable Projects, the Jira checks run **before** the transaction (`backend/AGENTS.md:144`: no outbound call inside `@Transactional`; precedent `WorkspaceServiceImpl.java:92-99` checks Jira, then `transaction.execute`), then inside the transaction re-check the Project is still Active and of this Department, write, audit. `update` is `@Transactional` on the method today, so it has to become a `TransactionTemplate` like `WorkspaceServiceImpl` (`:48`, `:99`).

## 4. Jira client and service account

- `JiraClient` (`B/tracker/client/JiraClient.java`), gateway Bearer `https://api.atlassian.com/ex/jira/{cloudId}` (`:37`, `:190-202`); timeouts `tsolve.tracker.jira.connect-timeout:5s`, `read-timeout:10s` (`:47-48`). Error mapping in `call` (`:204-219`): 429/5xx and no answer → `JiraUnavailableException`; other 4xx → `JiraRejectedException(status)`; bodies never kept.
- `checkServiceAccount` (`:67-93`) calls `/rest/api/3/myself` and `/rest/api/3/mypermissions?permissions=BROWSE_PROJECTS,CREATE_ISSUES,MODIFY_REPORTER` **with no `projectKey`**: a global check ("has it somewhere"), not "in that Project". Port: `TicketTrackerClient.java:15`.
- Other calls: `addComment`, `findComment`, `findProject` (`GET /rest/api/3/project/{key}`, 404 → empty, `:130-145`), `projectStatuses` (`:147-172`). **No createmeta / issue-type / field call and no project-scoped permission call.**
- `JiraServiceAccount.current()` (`B/tracker/service/JiraServiceAccount.java:13`) is implemented by workspace: `B/workspace/service/impl/WorkspaceJiraServiceAccount.java:39-67` (empty when no row, no cloud id or undecryptable token).
- KAN-119's MSG156: `WorkspaceServiceImpl.java:92-97` and `:167-171` call `tracker.checkServiceAccount(...)` and turn **both** `JiraRejectedException` and `JiraUnavailableException` into `FieldMessageCodeException(400, "MSG156", {tracker_name: "Jira"}, "serviceAccountToken")` (helper `:219-221`). Note `WorkspaceServiceImpl` imports `tracker.client.TicketTrackerClient` directly (`:23`), which `backend/AGENTS.md:78-79` forbids for other modules; workspace's `paths` own `tracker/client/**` (`module.md:15-16`), which is how it got in.
- KAN-127 calls Jira only through `TrackerProjectService.findProject` (`B/tracker/service/TrackerProjectService.java:8`, impl `TrackerProjectServiceImpl.java:32-77`): failures come back as outcomes `FOUND/NOT_FOUND/DENIED/UNAVAILABLE` (`TrackerProjectLookup.java:23-28`), and `ProjectServiceImpl.java:197-206` maps UNAVAILABLE → `ProjectTrackerUnavailableException`, others → MSG48.

**Missing for this feature** (both inside workspace's `paths`, `tracker/client/**`):
1. A project-scoped permission call: `GET /rest/api/3/mypermissions?projectKey={key}&permissions=CREATE_ISSUES,MODIFY_REPORTER` (check `havePermission` of each) → MSG156.
2. Issue type and required fields: `GET /rest/api/3/issue/createmeta/{key}/issuetypes` (find Task, not a sub-task) then `GET /rest/api/3/issue/createmeta/{key}/issuetypes/{issueTypeId}` (fields with `required` and `hasDefaultValue`) → MSG157. Jira also marks `project`, `issuetype` and often `reporter` as required; the allowed set is Q6. These endpoints are from Jira Cloud REST v3 docs, not yet verified against the staging site; the Planner should confirm with one stub test and one manual call.

## 5. Which Projects may be chosen, and the module boundary

- SRS 1657: "the Active Projects of this Department". No other rule text.
- No endpoint lists another Department's Projects for an Admin: `GET /api/v1/projects` is Project Manager only and lists the caller's own Department (`B/project/controller/ProjectController.java:49-52`, `ProjectServiceImpl.java:301-306`). `ProjectService` (`B/project/service/ProjectService.java:11-27`) exposes `testConnection`, `create`, `list`, `findActivePublicIdByJiraProjectKey`; nothing by Department.
- Module graph: `project-integration` depends on `workspace` (`project-integration/module.md:5`, "A Project belongs to a Department"); `workspace` depends on `identity` only (`workspace/module.md:5`). `backend/AGENTS.md:80`: "No cycles between modules: if A calls B, B never calls A." Workspace `paths` do not include `B/project/**`.
- In code, project reads `department` with its own SQL (`ProjectLinkRepository.java:45-47`, `:69`), and identity reads `department` too; nothing in project calls a workspace service. So workspace reading `project` rows with its own JDBC is symmetric with existing practice; calling `ProjectService` would invert the declared dependency (Q4).
- Display name: `project` has no name; Search and the Projects table show `jira_project_key` (`SearchTicket.java:21`, `ProjectRowResponse.java:6-7`). `{project_name}` in MSG157 would be the key unless a Jira read of the name is added.

## 6. Consumers and clearing

- **Knowledge Chatbot issue form (UC-58): not built.** `B/chatbot/` has only `answer/**`; no issue form, no create-issue call, nothing reads `default_project_id`. Chatbot spec line 266 keeps KAN-241 (API) and KAN-242 (web form) out of scope. `chatbot/module.md:46` says it will consume "project-integration Tracker client" for UC-58.
- **GBR-21 remove a Project: no code.** No statement sets `project.status = 'REMOVED'`; only the reconnect sets `ACTIVE` (`ProjectLinkRepository.java:125-127`). UC-17/UC-18 follow later (`project-integration/module.md:26`).
- **GBR-17 delete a Department: no code.** No delete endpoint in `DepartmentController`; UC-14 (KAN-122) is Out of Scope in the workspace spec.
- So a stored default Project has no reader yet, and nothing clears it when its Project or Department goes away (Q8).

## 7. Frontend

- `F/features/workspace/pages/DepartmentDetailPage.tsx:9-23`: `useGetDepartment`, renders `DepartmentNameForm` in a "Department Detail" `SectionCard`.
- `F/features/workspace/components/DepartmentNameForm.tsx`: view mode `Descriptions` with Name and an Edit button (`:57-69`); edit mode `AppForm` with "Department name", submit ref guard, `applyApiErrors` (`:34-49`). A Default Project select fits into the same form and view.
- `F/features/workspace/hooks/useDepartmentMutations.ts:23-31` `useRenameDepartment`, invalidates every query.
- MSW `F/features/workspace/mocks/handlers.ts`: seeded `departments` list `:59`, GET `:134`, PUT `:139-152`, reset `:154`. Needs seeded Projects per Department to drive a select.
- Catalog `F/shared/messages/catalog.ts`: MSG156 present (`:272-275`, field); **MSG157 missing** (catalog paths are inside workspace's `paths`).
- No project hook usable: `jira-integration` exports only `jiraIntegrationRoutes` (`F/features/jira-integration/index.ts:2`), and `useProjects` (`hooks/useProjects.ts:4-6`) is the PM's own list. The options must come from a workspace endpoint.

## 8. Open questions for the Planner

**Q1. One Save or its own form?** SRS step 2: one Edit, name and default Project, one Save.
(a) Extend `PUT /departments/{id}` with `defaultProjectId` (UUID or null, always sent), one form; (b) separate `PUT /departments/{id}/default-project` and its own Edit on 2.3; (c) (a) but field optional, absent = unchanged.
Recommended: (a). It is what UC-13 describes and what D6 planned ("one more field of the same body"). Always sending it avoids absent-versus-null rules. AC-003.14 ("same name, no write") becomes "nothing changed, no write".

**Q2. Clearing.** (a) null clears it, no Jira call, audited; (b) no clearing once set. Recommended: (a); the field is "optional" and Marketing-type Departments have none.

**Q3. Which Projects are offered and accepted.** (a) Active Projects of this Department only, by key order; the server rejects any other id (Removed, other Department, unknown); (b) also show the current one when it became Removed, marked. Recommended: (a) as SRS 1657 says; a stored Removed default is shown as none (see Q8). Rejection code: 400 with MSG157? No message fits exactly; recommend 400 `MSG04` on `defaultProjectId` (stale list), or the Planner adds a provisional key.

**Q4. How workspace reads Projects.** (a) Its own JDBC in `DepartmentRepository` (`SELECT public_id, jira_project_key FROM project WHERE department_id = :id AND status = 'ACTIVE'`, plus a `FOR SHARE` re-read in the transaction); (b) a new `ProjectService.listActiveForDepartment` in project-integration; (c) project-integration serves the Admin endpoint and 2.3 calls it. Recommended: (a). (b) inverts the declared dependency (AGENTS.md:80) and needs project-integration's gate; (c) splits one screen across two modules. (a) mirrors how project reads `department`.

**Q5. Read model for 2.3.** `DepartmentRef` is identity's shared schema. (a) New `DepartmentDetail { departmentId, name, defaultProject: {projectId, jiraProjectKey} | null, projects: [...] }` returned by `getDepartment` and `updateDepartment`; (b) add the fields to `DepartmentRefResponse`; (c) keep `DepartmentRef` and add `GET /departments/{id}/projects`. Recommended: (a); (b) changes identity and project responses.

**Q6. "Requires no field other than summary and description."** Jira also lists `project`, `issuetype`, `reporter` as required, and UC-58 sets `labels`. (a) Fail when a field is `required` and has no default and is not in {summary, description, project, issuetype, reporter, labels}; (b) the same without `reporter`/`labels`; (c) ignore `hasDefaultValue`. Recommended: (a); it accepts exactly what UC-58 will send. Task matched by `name` "Task" (case-insensitive), `subtask` false; Planner to confirm against staging (team-managed projects).

**Q7. Jira failure mapping.** (a) Permission missing, 401/403, no account, timeout or 5xx → 400 MSG156 on `defaultProjectId`; Task missing or other required field → 400 MSG157 with `project_name` = key; Jira 404 on the project → MSG157; (b) timeout/5xx → 503 MSG06 instead. Recommended: (a), same as KAN-119 (`WorkspaceServiceImpl.java:94-96`), so the Admin always sees a field message.

**Q8. Removed Project / Deleted Department (GBR-40 last sentence).** Neither removal exists yet. (a) Nothing writes now; every reader (2.3 and later UC-58) treats a default whose Project is not Active as none; the remove-Project and KAN-122 stories get a note to set it to NULL; (b) add the clearing SQL now in workspace for future callers. Recommended: (a); (b) is code with no caller.

**Q9. Re-check when the default does not change.** The Admin renames only, and the stored default is kept. (a) No Jira call unless `defaultProjectId` changes; (b) re-check every save. Recommended: (a); a rename must not fail because Jira is down.

**Q10. Support flag / field visibility.** No flag exists. (a) Show the field for every Department, optional; (b) add a "handles support requests" switch and changeset. Recommended: (a); GBR-40 defines a support Department as one with a default Project.

**Q11. Where the Jira calls live.** (a) Two new `TicketTrackerClient` methods called from `DepartmentServiceImpl`, as `WorkspaceServiceImpl` does; (b) a `tracker/service` method returning outcomes like `TrackerProjectService`. Recommended: (b) if the Planner wants AGENTS.md:78-79 kept (needs `tracker/service/**` paths, owned by tracker); otherwise (a) is inside workspace's `paths` today. Lean (a) for the smallest diff and flag the boundary.

**Q12. Audit.** Recommended: same `DEPARTMENT_CHANGE` entry, `before`/`after` gain `defaultProjectId` and `jiraProjectKey` (D11 planned this); one entry per Save.

**Spec.** Change Mode on `workspace`: new STORY-012 (API) and STORY-013 (2.3 field), MSG157 to the catalog, the two Out of Scope lines (spec ~321 and ~333) leave, re-approval before code.
