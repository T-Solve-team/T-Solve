---
module: triage
date: 2026-11-02
owner: BA
---

# Spec: triage

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-02 | STORY-001..STORY-002 | Initial frontend-only spec for 10.1 Suggestion History and read-only 10.2 Triage Result Detail on MSW | KAN-149, current module map and tracker record; the 01/11 Jira update removed the obsolete class/routing behavior |
| 2026-11-04 | STORY-003..STORY-004 | Add the backend history and detail APIs; ids become BIGINT, the issue summary is dropped and the Project is shown by its Jira project key; define the `sources_used` JSON shape and Wiki availability through a wiki port; reword AC-001.7, AC-001.12 and AC-002.1 | KAN-149 is done only with FE and BE (user, 04/11); BIGINT ids per "T-Solve Database Tables" 31/10 and the tables on main; no table stores an issue summary or Project name |
| 2026-11-04 | STORY-001..STORY-004 | Ids in the API follow the team's shared id type (today the session `ProjectMembership.projectId`) instead of int64 | The session `/me` contract still uses UUID; a triage-only int64 would not match the Project filter. The team decides the id type for all tables and APIs (user, 04/11) |
| 2026-11-04 | STORY-003..STORY-004 | "Required from other modules" names the triage ports `ManagedProjects`, `WikiDocumentAccess` and `JiraSite` and their fail-closed stand-ins; the backend no longer waits for KAN-117 | User chose on 04/11 to build the backend now with ports; neither `main` nor KAN-117 has `project_membership` or `jira_site_url`. No acceptance criterion changes |
| 2026-11-07 | STORY-005 (new) | **Backend.** New STORY-005: a `triage-suggestion` tenant job, queued when the tracker records a new issue, embeds the issue summary and description, retrieves the Published tickets of the Project's Department (search STORY-011) and Company-wide Wiki documents (port `WikiSourceRetrieval`, stand-in `NoWikiSources`), keeps at most 5 sources at or above `SUGGESTION_MIN_SIMILARITY` (default 0.7) and fills the existing `suggestion_result` row. Builder check: issue text capped at 8000 characters, AC-005.6/005.9 worded on what a test can see, single-Workspace queueing until Jira App requests are bound to a Workspace (user, 07/11). Functional 5, Non-Functional 4 and 5, Out of Scope, Interface Contracts and Required from other modules follow | KAN-47 backend service only (user, 07/11): API-06 is in tracker (KAN-22); tuning the threshold on KAN-49 data is a later round; on AI failure JobRunr retries and the row stays NULL/[] with a log, no status column |

## Requirements

### Functional
1. A Project Manager views the AI Solution Suggestion results of Projects they manage in screen 10.1.
2. A Project Manager opens a result in the read-only 10.2 detail popup, including its suggested solution, source summary and reporter answer.
3. History and detail use the existing Workspace site and role model. The server decides Project access; the web app never supplies a Workspace selector.
4. The backend serves the history and detail from the tenant `suggestion_result` table, scoped to the Projects the signed-in user manages.
5. When the tracker records a new Jira issue, a background job writes the AI Solution Suggestion into that issue's existing `suggestion_result` row, from the Published tickets of the Project's Department and Company-wide Wiki documents that pass the relevance threshold; otherwise the row stays without a suggestion (STORY-005).

### Non-Functional
1. Each acceptance criterion has a test named with its id, and `sdd.ts trace triage` passes.
2. The frontend checks `format:check`, `lint`, `typecheck`, `test`, `build` and deterministic `generate:api` pass; the backend `mvn verify` (tests and `spotless:check`) passes.
3. Use the existing UI kit, generated API client, TanStack Query and MSW; add no npm dependency or shared/app source edit.
4. The backend code lives in `vn.tsolve.triage` and adds no Maven dependency. It never changes the tracker's entity or table; its only write is STORY-005's update of `sources_used` and `suggested_solution` on an existing row.
5. STORY-005 calls the embedding and chat models outside any database transaction, runs only as a `worker` job, and never logs or puts in a job payload the issue text, source text or suggested solution.

## User Stories

### STORY-001: View suggestion history
- As a Project Manager, I want to review suggestion results of my Projects so that I can see which suggestions helped reporters.
- Acceptance:
  - AC-001.1: WHEN a Project Manager opens `/triage-history`, THE frontend SHALL show screen 10.1 with columns Issue, Sources, Suggested solution and Reporter's answer, ordered by newest result first.
  - AC-001.2: WHEN the list first opens, THE frontend SHALL request page 0, size 20 and newest-first order from the proposed history API without a Project, answer or date filter.
  - AC-001.3: THE Project filter SHALL offer only Projects the signed-in Project Manager manages; THE answer filter SHALL offer All answers, Solved, Not solved and No answer; THE date filter SHALL offer a paired inclusive calendar-day range.
  - AC-001.4: WHEN the user changes a filter, THE frontend SHALL combine the selected filters, return to page 1 and retain them in the URL across reload and browser Back navigation.
  - AC-001.5: IF the date range in the URL is incomplete, malformed or reversed, THEN THE frontend SHALL show MSG39 beside the date filter and prevent a request with that range.
  - AC-001.6: WHEN more than 20 results match, THE frontend SHALL show Total N items and paginate by 20 while retaining the current filters.
  - AC-001.7: THE Issue cell SHALL show the Jira issue key and its Project's Jira project key; a safe HTTPS issue link SHALL open Jira, and a separate View details action SHALL open the 10.2 popup.
  - AC-001.8: THE Sources cell SHALL show the count of Ticket and Wiki sources used; THE Suggested solution cell SHALL show a plain-text excerpt or a dash when no suggestion was posted; THE Reporter's answer SHALL show Solved, Not solved or No answer.
  - AC-001.9: WHILE the list loads, THE frontend SHALL show LoadingState; IF no results match, THEN THE frontend SHALL show MSG04; IF the list request fails, THEN THE frontend SHALL show ErrorState with retry that retains the filters.
  - AC-001.10: IF the user lacks Project Manager, THEN THE existing route guard SHALL deny the page and the mock history API SHALL refuse a direct request without returning results.
  - AC-001.11: WHEN the mock history API receives a Project filter outside the user's managed Projects, THE mock API SHALL return 403/MSG08 rather than widening the list; THE mock API SHALL exclude results of another Workspace before filtering, ordering and pagination.
  - AC-001.12: THE frontend SHALL render issue keys, Project keys, solution excerpts and all other API text as plain text, never as HTML, and SHALL reject non-HTTPS Jira URLs.

### STORY-002: View one suggestion result
- As a Project Manager, I want to open a suggestion result so that I can read its full suggestion and the reporter's answer.
- Acceptance:
  - AC-002.1: WHEN View details is chosen on a result, THE frontend SHALL open the read-only 10.2 Triage Result Detail popup for that Jira issue, showing issue key, Project key, date, full suggested solution, source summary and reporter's answer.
  - AC-002.2: WHEN the result has no suggested solution, THE popup SHALL show a dash for that field and No answer when the reporter has not answered.
  - AC-002.3: THE popup SHALL show the number of Ticket sources without linking or exposing their details; THE popup SHALL link only available Wiki sources through the existing Wiki Document route.
  - AC-002.4: IF a Wiki source is no longer available to the viewer, THEN THE popup SHALL show MSG110 for that source without a usable link.
  - AC-002.5: WHILE the detail request loads, THE popup SHALL show LoadingState; IF it fails, THEN THE popup SHALL show ErrorState with retry; IF the result no longer exists, THEN THE popup SHALL show MSG04.
  - AC-002.6: WHEN the popup closes and another result is opened, THE frontend SHALL show only the newly selected issue's detail, with no stale suggestion or source data.
  - AC-002.7: IF the requested issue belongs to a Project the user does not manage or another Workspace, THEN THE mock detail API SHALL return 404/MSG04 without revealing whether a suggestion exists.
  - AC-002.8: THE popup SHALL provide no class correction, routing or other mutation controls.

### STORY-003: Serve the suggestion history from the backend
- As a Project Manager, I want the history list to come from the real results of my Projects so that the 10.1 screen shows what the AI actually posted.
- Acceptance:
  - AC-003.1: WHEN a signed-in user holding PROJECT_MANAGER calls `GET /api/v1/suggestion-results`, THE backend SHALL return only the `suggestion_result` rows of Projects where that user's `project_membership.role_in_project` is `PROJECT_MANAGER`, newest `created_at` first, as a paged list with page 0 and size 20 by default.
  - AC-003.2: WHEN `projectId`, `answer` or a `from`/`to` pair is given, THE backend SHALL combine them with AND; `answer=NONE` SHALL match rows with no reporter answer; `from` and `to` SHALL include both whole calendar days in Asia/Ho_Chi_Minh.
  - AC-003.3: IF the caller has no session, THEN THE backend SHALL return 401/MSG07; IF the caller lacks PROJECT_MANAGER or `projectId` is a Project they do not manage, THEN THE backend SHALL return 403/MSG08 without returning results.
  - AC-003.4: IF `answer` is not `YES`, `NO` or `NONE`, `page` is negative or `size` is outside 1..100, THEN THE backend SHALL return 400/MSG06; IF only one of `from`/`to` is given, either is not a calendar date, or `from` is after `to`, THEN THE backend SHALL return 400/MSG39.
  - AC-003.5: THE backend SHALL return for each row the issue key, `jiraIssueUrl` built as the Workspace `jira_site_url` plus `/browse/` and the key, the Project id and Jira project key, `createdAt`, the Ticket and Wiki source counts from `sources_used`, the reporter answer, and a plain-text excerpt of at most 200 characters of `suggested_solution` (null when none was posted).
  - AC-003.6: IF the Workspace has no `jira_site_url`, THEN THE backend SHALL return `jiraIssueUrl` as null and still return the row.

### STORY-004: Serve one suggestion result from the backend
- As a Project Manager, I want the detail popup to load the full result from the backend so that I read the posted suggestion and its Wiki sources.
- Acceptance:
  - AC-004.1: WHEN a Project Manager calls `GET /api/v1/suggestion-results/{issueKey}` for an issue of a Project they manage, THE backend SHALL return the AC-003.5 fields plus the full `suggested_solution` and one `wikiSources` entry (`documentId`, `title`, `isAvailable`) per `WIKI` item in `sources_used`, and no Ticket source details.
  - AC-004.2: IF the issue has no result, belongs to a Project the caller does not manage, or the caller lacks PROJECT_MANAGER, THEN THE backend SHALL return 404/MSG04 without revealing whether a result exists.
  - AC-004.3: THE backend SHALL set `isAvailable` from the wiki module's document-access port for the caller; WHILE the wiki module provides no implementation, THE default SHALL answer false for every document.
  - AC-004.4: IF `sources_used` holds an item it cannot read (unknown `type` or missing `id`), THEN THE backend SHALL skip that item in the counts and the list and still return the result.

### STORY-005: Generate the suggestion for a new issue
- As a reporter, I want T-Solve to prepare a suggested solution from the knowledge of my Department when I create an issue, so that I may solve it without waiting for Staff (KAN-47, UC-36).
- Acceptance:
  - AC-005.1: WHEN the tracker publishes `IssueRecordedEvent`, THE backend SHALL queue one tenant job of type `triage-suggestion` for that `suggestion_id` through `TenantJobService`, in the Workspace bound to the request or, when none is bound, the one `WorkerTenantService.enqueueWorkspaceId()` names.
  - AC-005.2: IF jobs are disabled in the process, no Workspace can be bound, or the job cannot be queued, THEN THE backend SHALL log the suggestion id and the error type, leave the row unchanged and let the event still be answered 202.
  - AC-005.3: WHEN the job runs for a row that has an `issue_summary` and no `suggested_solution`, THE backend SHALL embed the issue text: the summary, a blank line and the description (summary alone when the description is null or blank), cut to its first 8000 characters.
  - AC-005.4: WHEN the issue is embedded, THE backend SHALL retrieve the Department Solutions of the row's Project through `SolutionRetrieval.nearestInProjectDepartment` and the Company-wide Wiki documents through `WikiSourceRetrieval`, each at or above `SUGGESTION_MIN_SIMILARITY`, and SHALL keep at most 5 of them together, most similar first.
  - AC-005.5: WHEN at least one source is kept, THE backend SHALL ask the chat model for a plain-text suggested solution built only from the issue text of AC-005.3 and the kept sources, and SHALL store it as `suggested_solution` and the kept sources as `sources_used` in one update.
  - AC-005.6: THE backend SHALL write each kept ticket as `{"type": "TICKET", "id": <ticket public_id>, "similarity": <s>}` and each kept Wiki document as `{"type": "WIKI", "id": <document id>, "title": <title>, "similarity": <s>}`, and SHALL give the chat model no ticket id, ticket Jira key or ticket title (only the five Solution sections), and Wiki documents by title only.
  - AC-005.7: IF no source reaches `SUGGESTION_MIN_SIMILARITY`, THEN THE backend SHALL call no chat model and leave `suggested_solution` NULL and `sources_used` `[]`.
  - AC-005.8: IF the embedding or chat model fails or answers a blank text, THEN THE job SHALL fail so that JobRunr retries it, and SHALL log only the suggestion id, the issue key and the error type.
  - AC-005.9: IF a run fails, THEN THE job SHALL write nothing to the row, so that after the last JobRunr retry it keeps `suggested_solution` NULL and `sources_used` `[]`, with no new column or status.
  - AC-005.10: IF the row no longer exists, already has a `suggested_solution`, or has no `issue_summary` (recorded before this change), THEN THE job SHALL change nothing and call no model.
  - AC-005.11: THE backend SHALL read `SUGGESTION_MIN_SIMILARITY` (default 0.7); IF it is not a number from 0 to 1, THEN THE backend SHALL fail to start.
  - AC-005.12: WHILE the wiki module provides no `WikiSourceRetrieval`, THE stand-in `NoWikiSources` SHALL return no document, so the sources are tickets only.

## Out of Scope
- Posting the MSG80 comment to Jira, the Issue Panel, recomputing suggestions (API-06, tracker KAN-22) and recording the reporter answer (KAN-131).
- Tuning `SUGGESTION_MIN_SIMILARITY` on the KAN-49 data: a later round; until then the default 0.7 stands.
- Wiki retrieval itself (the wiki module) and a retry limit other than JobRunr's default (`worker`, KAN-162).
- Generating a suggestion for an issue recorded before this change (no `issue_summary`), or regenerating one.
- Queueing the job with several Workspaces active: until discovery/platform binds a Jira App request to its Workspace, only `enqueueWorkspaceId()` (one active Workspace) works; otherwise AC-005.2 applies (user, 07/11).
- The users, roles and `project_membership` tables and the method security that back PROJECT_MANAGER (KAN-117).
- The Jira issue summary and a Project display name: no table stores them.
- The old SRS class, reason, Sensitive, route-to-handler and correction controls, removed by the current AI Suggestion decision on 29/10 and the KAN-149 update on 01/11.
- Linking to or showing the text of Published tickets used as AI sources.
- Editing the existing `/triage-history` route title or application shell.

## Interface Contracts

### Exposed by this module
The existing Project Manager route `/triage-history` renders 10.1; 10.2 is a popup without its own route.

The backend (`vn.tsolve.triage`) provides these two endpoints; the MSW handler mirrors them for frontend tests:

- `GET /api/v1/suggestion-results` (`getSuggestionResults`, tag `triage`) accepts optional `projectId`, `answer` (`YES`, `NO`, `NONE`), paired inclusive `from`/`to` dates, zero-based `page`, `size` and `sort=createdAt,desc`. It returns `SuggestionResultPage` with `content` and shared `PageMetadata`. Each summary has `issueKey`, nullable `jiraIssueUrl`, `project` (`projectId`, `key`), `createdAt`, `ticketSourceCount`, `wikiSourceCount`, nullable `suggestedSolutionExcerpt` and nullable `reporterAnswer` (`YES`/`NO`). One Workspace has at most one result per issue key; the issue key is the detail lookup key and is not coerced to a numeric database id.
- `GET /api/v1/suggestion-results/{issueKey}` (`getSuggestionResult`, tag `triage`) returns the same identity fields plus nullable full `suggestedSolution` and `wikiSources[]` (`documentId`, `title`, `isAvailable`). It does not return Ticket source details. A no-suggestion row is still a result with null suggestion and no answer.

Both endpoints use same-origin relative paths with no Workspace query or header. The backend resolves Workspace from the host and Project access from the signed-in user. 401 returns MSG07, non-Project Manager list access returns 403/MSG08, inaccessible detail returns 404/MSG04, malformed filters return 400/MSG39 or MSG06, and unexpected failure is shown with MSG06.

`projectId` and `documentId` use the team's shared id type, the same as the session `ProjectMembership.projectId`; the team decides it for all tables and APIs, and triage follows.

`suggestion_result.sources_used` is a JSON array; each item is `{"type": "TICKET" | "WIKI", "id": <id>, "title": "<text>", "similarity": <0..1>}`. `title` is required for `WIKI` items only. The writer (STORY-005) follows this shape (AC-005.6).

In-process (STORY-005), in `vn.tsolve.triage`:

```java
// worker job: a TenantJobHandler whose handlerType() is "triage-suggestion"; recordId = new UUID(0, suggestionId)
// port in service/, implemented later by the wiki module; stand-in service/impl/NoWikiSources returns List.of()
public interface WikiSourceRetrieval {
    /** Company-wide Wiki documents nearest to queryVector (cosine, 0..1, same scale as tickets), most similar first. */
    List<WikiSource> nearest(float[] queryVector, double minSimilarity, int limit);
}
// triage/dto: documentId in the team's shared id type as a string; content is plain text
public record WikiSource(String documentId, String title, String content, double similarity) {}
```

Configuration: `tsolve.triage.min-similarity` from `SUGGESTION_MIN_SIMILARITY`, default 0.7; at most 5 sources (fixed).

### Required from other modules
- fe-foundation: route/role guard, session managed Projects, URL state, UI kit, message catalog, generated transport and MSW registration.
- tracker: one `suggestion_result` row per Jira issue with `issue_summary` and `issue_description`, and `IssueRecordedEvent` after a new row (tracker AC-003.1, AC-003.9); later the posted comment and the reporter answer (KAN-131).
- search: `SolutionRetrieval.nearestInProjectDepartment` (search STORY-011).
- ai: `EmbeddingClient` (bge-m3), `LlmClient` and `AiUnavailableException`.
- worker: `TenantJobService`, `TenantJobHandler` and the bound Workspace (`WorkerTenantService`).
- wiki: `WikiSourceRetrieval`; until the wiki module implements it, `NoWikiSources` answers none (AC-005.12).
- identity: the signed-in user and the PROJECT_MANAGER role (KAN-117), and the Projects the user manages in `project_membership`, through the triage port `ManagedProjects`. Until identity implements it, the stand-in `NoManagedProjects` answers no Project, so the list is empty and every detail is 404/MSG04.
- wiki: the triage port `WikiDocumentAccess`, answering for the caller whether a Wiki document id is available; until the wiki module implements it, `NoWikiDocumentAccess` answers none (AC-004.3).
- workspace: the Workspace `jira_site_url` through the triage port `JiraSite`; until the workspace table has it, `NoJiraSite` answers none, so `jiraIssueUrl` is null (AC-003.6).

## Definition of Done
- [ ] The current spec is approved through `sdd.ts approve triage` before code is written.
- [ ] Every acceptance criterion has a behavior test whose name starts with its id; `sdd.ts trace triage` passes.
- [ ] All required frontend checks and deterministic API generation pass, and the backend `mvn verify` passes.
- [ ] The frontend calls the real backend endpoints; the MSW handler matches the backend contract.
- [ ] STORY-005 is tested with fake models and retrieval, plus one Testcontainers test of the conditional update.
- [ ] A browser check at 1366 × 768 covers filters, a suggestion detail and a no-suggestion detail.
- [ ] The feature, changed files, checks and proposed Jira commit message are reported before the feature commit.
