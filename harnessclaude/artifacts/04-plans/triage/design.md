---
module: triage
date: 2026-11-02
---

# Design: triage frontend history

Spec: artifacts/03-modules/triage/spec.md

## Architecture Overview

This plan covers only the KAN-149 web slice. The registered `/triage-history` route already checks
Project Manager. Replace its placeholder with a filter toolbar, DataTable and 10.2 detail popup.
Read the Project, answer, date and page state from the URL. Feature-local hooks call two generated
TanStack Query endpoints. The detail popup is keyed by the selected Jira issue key; closing or
switching selection must not show the previous issue's data. The existing mock registration includes
`triage/mocks/handlers.ts`, so no central file changes are required.

Mock mode contains results for the IT Project Manager's two managed Projects, the HR Project
Manager's Project and another Workspace. The handler checks the session role and Project access
before returning any content. It combines valid filters, sorts by `createdAt` descending with
`issueKey` as a stable tie-breaker, then slices for the requested page. The detail handler finds the
issue only inside the caller's accessible Projects and Workspace. Neither mock behavior nor the
route guard is a production security guarantee; the backend KAN-149 endpoint must enforce both.

## Data Model

The OpenAPI proposal exposes `SuggestionResultSummary`, `SuggestionResultDetail`,
`SuggestionResultPage` and `SuggestionWikiSource`. It reuses `PageMetadata`, `Problem` and
UUID project/document ids. `issueKey` is a stable opaque Jira key, matching the tracker's
one-result-per-issue constraint, so the FE does not depend on its internal BIGSERIAL
`suggestion_id`. `reporterAnswer` is nullable `YES`/`NO`, shown as No answer when null;
the answer filter's `NONE` means null. The summary returns a bounded plain-text excerpt.
The detail returns the full nullable suggestion and only Wiki source references that the viewer
may be told about; Ticket sources are represented by a count, not titles or links.

The proposed contract must be agreed with the tracker/triage backend owner before replacing MSW.
KAN-131 currently records a result but has no history read API; this feature does not claim one
exists. The tracker's current table has `sources_used`, `suggested_solution` and
`reporter_answer`; the backend will need to derive the proposed source counts and visibility.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|-------------------------|-----------|
| Scope | One list plus read-only detail popup | List only | User chose both on 2026-11-02; the existing 10.2 popup completes the view flow |
| Filters | Managed Project, answer, paired inclusive Vietnam dates | Obsolete class/routing filters | SRS III.10.1 has Project/answer/date; KAN-149 removed classes and routing |
| Source display | Ticket source count, available Wiki document links | Ticket source links or raw source JSON | KAN-47 forbids exposing Ticket source links/details in the suggestion; no data leak in history |
| Contract | Two OpenAPI proposals, generated client, MSW | Hand-written fetch or wait for backend | FE task must remain runnable while KAN-131 is pending |
| Detail key | Jira issue key | Internal BIGSERIAL id | One result per issue; no numeric-id coupling in FE |
| Popup | Use a feature-local read-only presentation inside the existing FormModal shell, with both footer actions closing | Raw antd Modal or shared UI change | Raw Modal is banned and shared UI changes need FE Lead review; the two footer buttons are a kit limitation to verify visually |
| Jira link | HTTPS only from the API, no URL constructed from untrusted issue key | Blindly render API URL | Prevent executable schemes while preserving Jira navigation |
| Dates | Inclusive Vietnam calendar dates; invalid values stop the query and show MSG39 | Silently broaden or coerce | Matches the audit and search patterns; malformed shared links cannot broaden data |

## File Structure

```text
frontend/openapi/tsolve-api.yaml                 proposal for list/detail schemas
frontend/src/shared/api/generated/**            generated only
frontend/src/features/triage/
  pages/TriageHistoryPage.tsx                    list and popup selection
  pages/TriageHistoryPage.test.tsx
  components/HistoryFilters.tsx
  components/TriageResultDetailModal.tsx
  components/TriageHistory.module.css
  hooks/useSuggestionHistory.ts
  hooks/history-filters.ts
  hooks/history-filters.test.ts
  mocks/data.ts
  mocks/handlers.ts
  mocks/handlers.test.ts
```

No app, shared, test harness, package or route registration edits. `index.ts` exports the popup
only if another feature needs it; KAN-149 uses it privately.

## External Dependencies

- Existing React, antd, TanStack Query, React Router, dayjs, orval, MSW and Vitest/RTL only.
- KAN-131's real suggestion result persistence and Wiki source visibility are required for later
  backend integration, not for the mock FE slice.

## Edge Cases & Risks

Dimensions analyzed: user types, input extremes, timing, scale, state transitions, environment,
error cascades, authorization, data integrity, integration, compliance and business logic.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | User types | Employee or Resolver opens route or direct API | High | Guard/handler deny with MSG08, no rows | Task 1 |
| 2 | STORY-001 | Authorization | PM filters a Project they do not manage | High | 403/MSG08, no fallback to all Projects | Task 1 |
| 3 | STORY-001 | Authorization | Other Workspace has matching issue key | High | Excluded before counts, pagination and detail lookup | Task 1/2 |
| 4 | STORY-001 | Input extremes | Missing, impossible, partial or reversed date | High | MSG39; no unfiltered request | Task 1 |
| 5 | STORY-001 | Environment | Result at 16:59:59.999Z/17:00:00Z | High | Correct inclusive Vietnam day | Task 1 |
| 6 | STORY-001 | Scale | 0, 1 and 21+ matching results | High | MSG04 or correct pages/total | Task 1 |
| 7 | STORY-001 | State transitions | Filter on page 2; Back then reload | High | Reset page on change; restore URL filters | Task 1 |
| 8 | STORY-001 | Error cascades | 500 then retry | High | ErrorState; same filters on retry | Task 1 |
| 9 | STORY-001 | Data integrity | Equal timestamps, null suggestion/answer | Medium | Stable issue-key order; dash/No answer | Task 1 |
| 10 | STORY-001 | Input extremes | HTML-like issue summary or solution | High | React text rendering only | Task 1/2 |
| 11 | STORY-001 | Integration | HTTP or javascript Jira URL | High | No executable link | Task 1 |
| 12 | STORY-002 | Timing | Open A, close, open B while A request finishes | High | Only B shown, no stale detail | Task 2 |
| 13 | STORY-002 | Error cascades | 404/500 detail, retry | High | MSG04 or ErrorState; list remains | Task 2 |
| 14 | STORY-002 | Compliance | Unavailable Wiki document | High | MSG110, no link | Task 2 |
| 15 | STORY-002 | Business logic | Result with no suggestion and no answer | Medium | Read-only dash and No answer | Task 2 |
| 16 | STORY-002 | Authorization | Forged issue key from unmanaged Project | High | 404/MSG04, no existence leak | Task 2 |
| 17 | STORY-002 | Integration | Source data contains Ticket ids | High | Show count only, no Ticket source details | Task 2 |
| 18 | STORY-001 | Integration | Regenerate client twice | High | Second run produces no diff | Task 2 |

Open questions for the spec owner: none. The source display and filter choices are explicit in the
spec review; changes to them require a spec change before code.

## Risk Review — 2026-11-02 — Verdict: CAUTION

Architect: the two query hooks fit the existing feature boundary, but the backend read contract
must be agreed before real integration. Security: result access and Wiki availability are data-level
checks that the production backend must enforce. Performance: page 20 summaries and one detail
request avoid returning every full suggestion in the list. User: the read-only detail remains useful
for a no-suggestion result; keyboard and error states need tests. Devil's advocate: the old SRS
class/routing controls would be dangerous to revive, so they are explicitly excluded.

Agreement: ship a bounded mock-backed FE slice with explicit proposed endpoints and negative
authorization tests. Conflict: the UI kit has no read-only detail modal. The existing FormModal shell
is the minimal feature-local route; verify its two close actions in the browser and request FE Lead
review if the final Figma frame requires a dedicated kit component.

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Mock access test mistaken for production authorization | High | Label proposal, negative fixture and direct-handler tests | Task 1/2 |
| Source details leak through history | High | DTO includes only Ticket count and available Wiki references | Task 2 |
| Backend contract drift | High | Generated client; agreement with KAN-131 owner before backend integration | Task 1/2 |
| Obsolete class/routing behavior reappears | High | Explicit out-of-scope and absence tests | Task 2 |

## Backend history API — 2026-11-04

The spec change of 04/11 (STORY-003, STORY-004) makes KAN-149 a FE + BE task. It replaces the
issue-summary and Project-name decisions above: the issue summary is dropped and the Project is
shown by `jira_project_key`. API ids keep the team's shared id type (today UUID in the session
`ProjectMembership`) while the tables on `main` are BIGINT; the team decides one type for all, and
the backend maps between them until then.

```text
backend/src/main/java/vn/tsolve/triage/
  controller/SuggestionResultController.java   GET list + GET {issueKey}; @PreAuthorize PROJECT_MANAGER
  dto/                                          SuggestionResultSummary, SuggestionResultDetail, ...
  entity/SuggestionResultView.java             read-only @Immutable mapping of suggestion_result
  repository/SuggestionResultViewRepository.java   Specification query: project_id IN managed, filters
  service/SuggestionResultService(.impl)       scope, filters, excerpt, sources_used parsing, URL
  port/WikiDocumentAccess.java                 isAvailable(callerId, documentIds); default bean: all false
backend/src/test/java/vn/tsolve/triage/        controller slice + service tests named with AC ids
```

- **Read-only mapping.** Triage maps `suggestion_result` as its own `@Immutable` entity and never
  writes it, so the tracker's `SuggestionResult` entity and migration `002` stay untouched. No new
  changeset: every column read exists on `main`.
- **Scope first.** The service resolves the caller's managed Project ids from `project_membership`
  (KAN-117) before any filter; a `projectId` outside that set is 403/MSG08; the detail looks up
  `jira_issue_key` with `project_id IN managed` and answers 404/MSG04 for every miss.
- **Dates.** `from`/`to` are `LocalDate`s converted to `[from 00:00, to+1 00:00)` in
  Asia/Ho_Chi_Minh, matching the FE helper `historyDateRange`.
- **sources_used.** Parsed with Jackson into `{type, id, title, similarity}`; unreadable items are
  skipped (AC-004.4). Counts and the Wiki list come from the parsed items.
- **Wiki availability.** `WikiDocumentAccess` is an interface in triage; the default bean answers
  false until the wiki module provides a bean (AC-004.3), so no document is linked by mistake.
- **Excerpt.** First 200 characters of `suggested_solution`, whitespace collapsed; null when none.
- **Jira URL.** `jira_site_url` + `/browse/` + key, only when the site URL is HTTPS; else null
  (AC-003.6); the FE keeps its HTTPS check.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 19 | STORY-003 | Authorization | PM of Project A passes `projectId` of B | High | 403/MSG08, no rows | Task 3 |
| 20 | STORY-003 | User types | Resolver or Employee calls the list | High | 403/MSG08 | Task 3 |
| 21 | STORY-004 | Authorization | Detail of an issue in an unmanaged Project | High | 404/MSG04, same as missing | Task 3 |
| 22 | STORY-003 | Environment | Row at 16:59:59Z / 17:00:00Z UTC | High | Correct Vietnam day | Task 3 |
| 23 | STORY-004 | Data integrity | `sources_used` holds junk or unknown type | Medium | Item skipped, result returned | Task 3 |
| 24 | STORY-003 | Integration | Workspace without `jira_site_url` | Medium | `jiraIssueUrl` null | Task 3 |

Dependency, revised 04/11 (user: build the backend now, own tasks only): neither `main` nor KAN-117
(PR #107) has `app_user`/`project_membership`, and `workspace` has no `jira_site_url`. Task 3 therefore
reads them through three triage ports in `service/`, each with a fail-closed stand-in in
`service/impl/`, the team's pattern of `ticket`'s `ProjectAccess`/`StubProjectAccess`:

| Port | Stand-in | Answers until its owner implements it |
|------|----------|---------------------------------------|
| `ManagedProjects.of(user)` → `ManagedProject(id, publicId, key)` | `NoManagedProjects` | no Project: the history is empty, the detail 404 |
| `JiraSite.url()` | `NoJiraSite` | empty: `jiraIssueUrl` null (AC-003.6) |
| `WikiDocumentAccess.available(user, ids)` | `NoWikiDocumentAccess` | none: every Wiki source unavailable (AC-004.3) |

`ManagedProject` carries both the database id and the id the web app knows, so the API keeps the
session's id type while `project` stays BIGINT. The caller is `Authentication.getName()` and the role
check is `hasRole('PROJECT_MANAGER')`, as `ImportController` does; both keep working with KAN-117's
`TSolveUser`. The detail endpoint is `isAuthenticated()` so a non-Project Manager gets the same 404.
Query correctness is proven by `SuggestionResultRecordRepositoryIT` (Testcontainers; CI needs Docker).

## Proposed Iteration

Current iteration 1 gets one Jira feature `suggestion-history` (KAN-149) with two implementation
stages: Task 1 list/filter/API mocks and Task 2 detail popup/verification. Both stages belong to
one code commit after the full frontend verify and user agreement. Branch:
`feature/KAN-149-triage-suggestion-history` from current `main`. No push, merge or PR is authorized.

## AI Solution Suggestion job — 2026-11-07

Spec change of 07/11: STORY-005 (triage), search STORY-011, tracker AC-003.1/003.6/003.9. Backend only; the
MSG80 comment, the Issue Panel and API-06 stay in tracker / jira-app.

### Flow

```text
Forge trigger (Jira App reads description via REST v2)
  -> POST /api/v1/jira-app/events/issue-created          tracker
       insert suggestion_result (+ issue_summary, issue_description)    commits on its own (not @Transactional)
       publish IssueRecordedEvent(suggestionId)          try/catch: 202 whatever happens (AC-003.9)
  -> triage SuggestionJobEnqueuer (@EventListener)        AC-005.1, AC-005.2
       TenantJobService.enqueue("triage-suggestion", new UUID(0, suggestionId))
       unbound thread -> WorkerTenantService.enqueueWorkspaceId() + runIn (ReviewNotificationEnqueuer pattern)
  -> JobRunr -> TenantJobRequestHandler.runInTenant -> SuggestionJobHandler.execute(recordId)
       SuggestionGenerator.generate(suggestionId)         no transaction around model calls (NF 5)
         1 read row (SuggestionResultRecord, read-only)   guards AC-005.10
         2 EmbeddingClient.embed([issueText])             AC-005.3
         3 SolutionRetrieval.nearestInProjectDepartment(projectId, v, min, 5)
           WikiSourceRetrieval.nearest(v, min, 5)          merge, sort by similarity desc, keep 5 (AC-005.4)
         4 none kept -> return (AC-005.7)
         5 LlmClient.chat(prompt, null); blank -> throw    AC-005.5, AC-005.8
         6 SuggestionWriter.write(id, sourcesJson, text)  one conditional UPDATE (AC-005.5, AC-005.10)
```

### Key Decisions

| Decision | Choice | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| Issue text for the job | Two nullable columns `issue_summary VARCHAR(255)`, `issue_description TEXT` on `suggestion_result`, written by tracker | Separate temp table; `@Async` in memory | User, 07/11. Worker NF5 forbids content in the job payload; Jira cannot be read later (no stored system token); least code, survives retries and restarts |
| Description format | Plain string ≤ 32767 or null; the Jira App reads it with REST v2 and forwards it | Accept ADF and extract text; summary only | User, 07/11. Forge's raw `avi:jira:created:issue` payload has no description (Forge events reference); no ADF parser in the backend |
| Trigger | Tracker publishes `IssueRecordedEvent`; triage listens and enqueues | Tracker calls a triage port; triage polls the table | Tracker stays unaware of triage; same shape as `ReviewFeedbackDecidedEvent` → `ReviewNotificationEnqueuer` |
| Runner | `worker` tenant job (`TenantJobService`, JobRunr) | Spring `@Async`, scheduler | User, 07/11; retry and Workspace binding come with it |
| Job record id | `new UUID(0, suggestionId)`; the handler takes `getLeastSignificantBits()` and ignores an id whose high bits are not 0 | Add a UUID column | `TenantJobService` takes a UUID and the table id is BIGINT; same mapping as changeset 010 (`lpad(to_hex(id), 32, '0')::uuid`); no schema change |
| Sources | Published tickets of the Project's Department (search STORY-011, new method on `SolutionRetrieval`) + Company-wide Wiki through the triage port `WikiSourceRetrieval` (stand-in `NoWikiSources`) | Workspace-wide `nearest`; wait for wiki | User, 07/11; triage may use search only through its `service/` interface (backend AGENTS) |
| Threshold | `tsolve.triage.min-similarity: ${SUGGESTION_MIN_SIMILARITY:0.7}`, `@Validated` record `TriageProperties` (0..1) ; at most 5 sources, a constant | Hard-coded; per-Project | User, 07/11; tuning on KAN-49 data is a later round |
| AI failure | Exception leaves the job → JobRunr retries (default policy); after the last retry the row stays NULL/`[]`; WARN log with id, key, error type | Status column; error column | User, 07/11: no schema change for failures |
| Write | Native `UPDATE suggestion_result SET sources_used = CAST(:json AS jsonb), suggested_solution = :text WHERE suggestion_id = :id AND suggested_solution IS NULL` in a triage repository, its own short transaction | JPA entity update | The triage entity stays `@Immutable`; the `IS NULL` guard makes a repeated or racing job a no-op (AC-005.10) |
| Ticket privacy | Prompt gets each ticket only as "Solution N" with its five sections; Wiki documents by title; the system prompt asks to name only Wiki titles | Pass ticket keys | GBR-36 / spec: tickets are never linked; ids stay in `sources_used` only (AC-005.6) |

Decided in planning (builder check):
- Issue text = summary, blank line, description; capped at 8000 characters for both embedding and prompt
  (`ponytail:` comment; raise when the chat model's context is measured).
- Prompt language: the system prompt asks the model to answer in the language of the issue.
- Merge order on equal similarity: tickets before Wiki, each in its retrieval order.
- Logs: INFO "suggestion stored / no source" with suggestion id + issue key; WARN on failure with
  `exception.getClass().getSimpleName()`; never text.
- Plain text by prompt instruction only; no Markdown stripping (builder check).
- Wiki `content` capped at 2000 characters per document in the prompt; similarity is cosine 0..1 like tickets.
- The handler logs and rethrows `IllegalStateException("triage-suggestion failed: " + type)` without the cause, so
  JobRunr's stored failure holds no model or issue text (AC-005.8).
- A run that found no source succeeds, so JobRunr does not deliver it again; no extra guard.
- Edge case 38 (high bits set) is a guard with a unit test but no AC of its own.
- The handler bean is `@ConditionalOnProperty(tsolve.worker.enabled=true)` like `ReviewNotificationJobHandler`, so the
  API process can check the type when it enqueues.

### File Structure

```text
backend/src/main/java/vn/tsolve/
  tracker/dto/IssueCreatedPayload.java                 + summary, description            (tracker Task 14)
  tracker/entity/SuggestionResult.java                 + issueSummary, issueDescription  (tracker Task 14)
  tracker/event/IssueRecordedEvent.java                record(long suggestionId)          (tracker Task 14)
  tracker/service/impl/IssueCreatedServiceImpl.java    store + publish                    (tracker Task 14)
  search/retrieval/...                                 nearestInProjectDepartment         (search Task 20)
  triage/config/TriageProperties.java                  min-similarity
  triage/entity/SuggestionResultRecord.java            + issue_summary, issue_description (read-only)
  triage/repository/SuggestionWriteRepository.java     conditional UPDATE
  triage/service/WikiSourceRetrieval.java, dto/WikiSource.java
  triage/service/impl/NoWikiSources.java
  triage/service/impl/SuggestionGenerator.java         steps 1-6
  triage/service/impl/SuggestionJobEnqueuer.java       @EventListener -> TenantJobService
  triage/service/impl/SuggestionJobHandler.java        TenantJobHandler "triage-suggestion"
backend/src/main/resources/application.yml             tsolve.triage.min-similarity
backend/src/main/resources/db/changelog/changes/019-tracker-suggestion-issue-content.sql
frontend/openapi/tsolve-api.yaml + generated model     IssueCreatedPayload fields (tracker Task 14)
```

### Edge Cases & Risks

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 25 | STORY-005 | Error cascades | Ollama down for embed or chat | High | Job throws, JobRunr retries; row NULL/`[]` | Task 4 |
| 26 | STORY-005 | Timing | Job delivered twice / two workers race | High | Second run sees a solution or the `IS NULL` update hits 0 rows; no second model call after a stored one | Task 4 |
| 27 | STORY-005 | Data integrity | Row from before the change (no `issue_summary`) | Medium | No model call, no change | Task 4 |
| 28 | STORY-005 | Authorization | Ticket of another Department scores 0.95 | High | Not a source (search AC-011.3) | search Task 20, Task 4 |
| 29 | STORY-005 | Compliance | Ticket key / title reaches the prompt or the solution | High | Prompt carries sections only; TICKET items have no title | Task 4 |
| 30 | STORY-005 | Business logic | Best source 0.69 with threshold 0.7; exactly 0.7 | High | 0.69 → no suggestion, no chat call; 0.7 kept | Task 4 |
| 31 | STORY-005 | Scale | 5 tickets + 5 Wiki above threshold | Medium | Exactly 5 kept, most similar first | Task 4 |
| 32 | STORY-005 | Error cascades | Worker disabled / enqueue throws in the event request | High | 202, row kept, WARN with id + type | Task 4, tracker Task 14 |
| 33 | STORY-005 | Multi-tenancy | Event request thread has no bound Workspace | High | `enqueueWorkspaceId` + `runIn`; none (several Workspaces) → WARN, no job — accepted until discovery (user, 07/11) | Task 4 |
| 34 | STORY-005 | Input extremes | Description null / blank / 32767 chars | Medium | Summary only / first 8000 chars (AC-005.3) | Task 4 |
| 35 | STORY-005 | Environment | `SUGGESTION_MIN_SIMILARITY=1.5` or `abc` | Medium | Startup fails | Task 4 |
| 36 | STORY-005 | Integration | LLM answers whitespace | Medium | Job fails and retries | Task 4 |
| 37 | tracker 003 | Input extremes | `description` sent as an ADF object | Medium | 400 MSG06, nothing stored | tracker Task 14 |
| 38 | STORY-005 | Integration | Job record id with high bits set | Low | Ignored, logged | Task 4 |

### Risk Review — 2026-11-07 — Verdict: CAUTION

Touches a shared contract (Jira App payload), a migration and a multi-tenant background job.
- Contract: the payload change ships only with Bình's Jira App change; until then events without `summary` get 400 and
  Forge retries them. Mitigation: tell Bình before merge; merge both together.
- Migration: additive nullable columns, rollback drops them; old rows simply get no suggestion (row 27).
- Tenancy: the job runs only through `TenantJobRequestHandler.runInTenant`; the enqueue copies the reviewed
  `ReviewNotificationEnqueuer` binding (row 33).
- Content at rest: the issue text now lives in the tenant database, like ticket content already does; never logged.

## AI Solution Suggestion — 2026-11-08 (supersedes the job design above)

Spec change of 08/11: `main` already runs the suggestion job (tracker STORY-011, KAN-22 #160) and declares the port
`SuggestionSearch` for KAN-47 with a stand-in. Triage now only implements that port. From the 07/11 section, the
Flow, the rows "Issue text for the job", "Description format", "Trigger", "Runner", "Job record id", "AI failure" and
"Write", the job-handler and enqueue items of "Decided in planning" and the File Structure no longer apply.

### Flow

```text
tracker SuggestionJobServiceImpl.run(suggestionId)        worker job, or in-process (tracker AC-011.9)
  -> SuggestionSearch.suggest(projectId, summary, description)   projectId = project.public_id
       triage SuggestionGenerator (@Service, the only bean; StubSuggestionSearch deleted)   AC-005.14
         blank summary -> Optional.empty(), no model call                       AC-005.13
         1 EmbeddingClient.embed([issueText])                                   AC-005.3
         2 SolutionRetrieval.nearestInProjectDepartment(projectId, v, min, 5)
           WikiSourceRetrieval.nearest(v, min, 5)  merge, similarity desc, keep 5   AC-005.4, AC-005.12
         3 none kept -> Optional.empty()                                        AC-005.7
         4 LlmClient.chat([system, user], null); blank -> IllegalStateException AC-005.5, AC-005.8
         5 return IssueSuggestion(sources, text)                                AC-005.6
  <- tracker stores it (fillSuggestion, IS NULL guard), posts MSG80; an exception fails the job -> retry
```

### Key Decisions

| Decision | Choice | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| Owner of the port | Triage implements tracker's `SuggestionSearch` | Search implements it; triage keeps its own job | User, 08/11: STORY-005 and its approved criteria already live in triage; the tracker job already queues, retries, stores and posts |
| Stand-in | Delete `StubSuggestionSearch`, its test, `tsolve.stub.suggestion` and `TSOLVE_STUB_SUGGESTION` | Keep it behind a flag; `@Primary` | User, 08/11; its own Javadoc says to delete it with KAN-47; two beans would stop the app starting |
| Failure | Let `AiUnavailableException` pass; a blank answer throws `IllegalStateException("Blank suggested solution")` | Catch and return empty | The tracker job turns any exception into a retry and logs only key + type (tracker AC-011.6); neither message holds text |

Kept from 07/11: Sources, Threshold (`TriageProperties`), Ticket privacy, issue text 8000 cap, prompt language, merge
order on ties (tickets first), plain text by prompt only, Wiki `content` capped at 2000 characters.

Decided in planning: triage logs nothing in `SuggestionGenerator` (the tracker logs key + error type); each retrieval
asks for 5 so the merged top 5 is exact.

### File Structure

```text
backend/src/main/java/vn/tsolve/
  common/config/TriageProperties.java                  min-similarity (0..1, default 0.7; AGENTS §1)
  triage/service/WikiSourceRetrieval.java, triage/dto/WikiSource.java
  triage/service/impl/NoWikiSources.java
  triage/service/impl/SuggestionGenerator.java         implements tracker.service.SuggestionSearch
  tracker/service/impl/StubSuggestionSearch.java       deleted (+ its test)
backend/src/main/resources/application.yml             + tsolve.triage.min-similarity; - tsolve.stub.suggestion
.env.example                                           + SUGGESTION_MIN_SIMILARITY; - TSOLVE_STUB_SUGGESTION
```

### Edge Cases & Risks (changes)

Rows 25, 28–31, 34–36 stand, with "job throws" read as "`suggest` throws, the tracker job retries". Row 26 (double
delivery) and row 32/33 (enqueue, Workspace binding) are the tracker's (AC-011.6, 011.7, 011.9). Row 27 is AC-005.13.
Row 37 and 38 no longer apply (main reads ADF; no BIGINT record id).
