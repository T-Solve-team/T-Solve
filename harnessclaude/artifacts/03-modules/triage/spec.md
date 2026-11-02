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

## Requirements

### Functional
1. A Project Manager views the AI Solution Suggestion results of Projects they manage in screen 10.1.
2. A Project Manager opens a result in the read-only 10.2 detail popup, including its suggested solution, source summary and reporter answer.
3. History and detail use the existing Workspace site and role model. The server decides Project access; the web app never supplies a Workspace selector.

### Non-Functional
1. Each acceptance criterion has a test named with its id, and `sdd.ts trace triage` passes.
2. The frontend checks `format:check`, `lint`, `typecheck`, `test`, `build` and deterministic `generate:api` pass.
3. Use the existing UI kit, generated API client, TanStack Query and MSW; add no npm dependency or shared/app source edit.

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
  - AC-001.7: THE Issue cell SHALL show the Jira issue key and its Project; a safe HTTPS issue link SHALL open Jira, and a separate View details action SHALL open the 10.2 popup.
  - AC-001.8: THE Sources cell SHALL show the count of Ticket and Wiki sources used; THE Suggested solution cell SHALL show a plain-text excerpt or a dash when no suggestion was posted; THE Reporter's answer SHALL show Solved, Not solved or No answer.
  - AC-001.9: WHILE the list loads, THE frontend SHALL show LoadingState; IF no results match, THEN THE frontend SHALL show MSG04; IF the list request fails, THEN THE frontend SHALL show ErrorState with retry that retains the filters.
  - AC-001.10: IF the user lacks Project Manager, THEN THE existing route guard SHALL deny the page and the mock history API SHALL refuse a direct request without returning results.
  - AC-001.11: WHEN the mock history API receives a Project filter outside the user's managed Projects, THE mock API SHALL return 403/MSG08 rather than widening the list; THE mock API SHALL exclude results of another Workspace before filtering, ordering and pagination.
  - AC-001.12: THE frontend SHALL render issue summaries, solution excerpts and all other API text as plain text, never as HTML, and SHALL reject non-HTTPS Jira URLs.

### STORY-002: View one suggestion result
- As a Project Manager, I want to open a suggestion result so that I can read its full suggestion and the reporter's answer.
- Acceptance:
  - AC-002.1: WHEN View details is chosen on a result, THE frontend SHALL open the read-only 10.2 Triage Result Detail popup for that Jira issue, showing issue key, summary, Project, date, full suggested solution, source summary and reporter's answer.
  - AC-002.2: WHEN the result has no suggested solution, THE popup SHALL show a dash for that field and No answer when the reporter has not answered.
  - AC-002.3: THE popup SHALL show the number of Ticket sources without linking or exposing their details; THE popup SHALL link only available Wiki sources through the existing Wiki Document route.
  - AC-002.4: IF a Wiki source is no longer available to the viewer, THEN THE popup SHALL show MSG110 for that source without a usable link.
  - AC-002.5: WHILE the detail request loads, THE popup SHALL show LoadingState; IF it fails, THEN THE popup SHALL show ErrorState with retry; IF the result no longer exists, THEN THE popup SHALL show MSG04.
  - AC-002.6: WHEN the popup closes and another result is opened, THE frontend SHALL show only the newly selected issue's detail, with no stale suggestion or source data.
  - AC-002.7: IF the requested issue belongs to a Project the user does not manage or another Workspace, THEN THE mock detail API SHALL return 404/MSG04 without revealing whether a suggestion exists.
  - AC-002.8: THE popup SHALL provide no class correction, routing or other mutation controls.

## Out of Scope
- Backend implementation of the history/detail APIs, suggestion generation and Jira comment/answer processing (KAN-47 and KAN-131).
- The old SRS class, reason, Sensitive, route-to-handler and correction controls, removed by the current AI Suggestion decision on 29/10 and the KAN-149 update on 01/11.
- Linking to or showing the text of Published tickets used as AI sources.
- Editing the existing `/triage-history` route title or application shell.

## Interface Contracts

### Exposed by this module
The existing Project Manager route `/triage-history` renders 10.1; 10.2 is a popup without its own route.

The following two endpoints are **frontend OpenAPI proposals** for KAN-149, not claims that the backend already provides them:

- `GET /api/v1/suggestion-results` (`getSuggestionResults`, tag `triage`) accepts optional `projectId` UUID, `answer` (`YES`, `NO`, `NONE`), paired inclusive `from`/`to` dates, zero-based `page`, `size` and `sort=createdAt,desc`. It returns `SuggestionResultPage` with `content` and shared `PageMetadata`. Each summary has `issueKey`, `jiraIssueUrl`, `issueSummary`, `project` (`projectId`, `name`), `createdAt`, `ticketSourceCount`, `wikiSourceCount`, nullable `suggestedSolutionExcerpt` and nullable `reporterAnswer` (`YES`/`NO`). One Workspace has at most one result per issue key; the issue key is the detail lookup key and is not coerced to a numeric database id.
- `GET /api/v1/suggestion-results/{issueKey}` (`getSuggestionResult`, tag `triage`) returns the same identity fields plus nullable full `suggestedSolution` and `wikiSources[]` (`documentId` UUID, `title`, `isAvailable`). It does not return Ticket source details. A no-suggestion row is still a result with null suggestion and no answer.

Both endpoints use same-origin relative paths with no Workspace query or header. The backend resolves Workspace from the host and Project access from the signed-in user. 401 returns MSG07, non-Project Manager list access returns 403/MSG08, inaccessible detail returns 404/MSG04, malformed filters return 400/MSG39 or MSG06, and unexpected failure is shown with MSG06. The mock handler reproduces these outcomes but does not establish production authorization.

### Required from other modules
- fe-foundation: route/role guard, session managed Projects, URL state, UI kit, message catalog, generated transport and MSW registration.
- tracker/KAN-131: one result per Jira issue, including the source counts, posted suggestion and reporter answer.
- wiki: document ids and availability for the viewer.

## Definition of Done
- [ ] The current spec is approved through `sdd.ts approve triage` before code is written.
- [ ] Every acceptance criterion has a behavior test whose name starts with its id; `sdd.ts trace triage` passes.
- [ ] All required frontend checks and deterministic API generation pass.
- [ ] A browser check at 1366 × 768 covers filters, a suggestion detail and a no-suggestion detail.
- [ ] The feature, changed files, checks and proposed Jira commit message are reported before the feature commit.
