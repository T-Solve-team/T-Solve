---
module: jira-app
date: 2026-11-11
iteration: 2
---

# Design: jira-app — iteration 2 (KAN-126: Solution Form, AI Rewrite, Issue Panel, Ticket Preview)

Spec: `artifacts/03-modules/jira-app/spec.md` (approved, version 8281a56c), STORY-001 to STORY-009 · Master design:
`artifacts/04-plans/jira-app/design.md` (STORY-010) · Research: RESEARCH-003 (Forge limits, verified 01/11).
Conventions: `jira-app/AGENTS.md` (UI Kit only, one server client, no secrets, fewest scopes), `backend/AGENTS.md`.
Branch `feat/KAN-126-jira-app-solution-form-v2` (from `main` 2f1eafff). Replaces the 01/11 plan of
`feat/KAN-126-jira-app-solution-form`, whose ticket services predate `ticket-review`'s ticket storage.

## Architecture Overview

```
Jira issue view
 ├─ jira:issueViewBackgroundScript ── events.on(JIRA_ISSUE_CHANGED, own atlassianId) ─► requestJira status
 │        └─ into Done category ─► POST …/issues/{key}/solution-form ─► mayOpen ─► UI Kit Modal "form"
 ├─ jira:issuePanel (3.3) ── GET …/issues/{key}/panel ─► MSG62 | MSG65 | MSG64 | MSG77 + button | open button
 │        ├─ GET …/issues/{key}/review-feedback   (ticket-review, MSG64 — existing endpoint)
 │        └─ GET …/tickets/search?q= ─► results ─► Modal "preview" (3.4) ─► GET …/tickets/{ticketId}
 └─ Modal "form" (4.1)
          ├─ requestJira GET /rest/api/3/issue/{key}?fields=summary,description   (as the viewer)
          ├─ POST …/issues/{key}/solution-form {summary, description} → Draft | AI draft | prefill + MSG67
          ├─ POST …/solution-rewrites ─► step "rewrite" (4.2) Accept / Discard (MSG68)
          ├─ PUT  …/issues/{key}/solution-draft                    (MSG41 / MSG06)
          └─ POST …/issues/{key}/solution  (five sections; Close = all empty) — retry ×3 → MSG69

backend /api/v1/jira-app/** (FIT chain, Roles.JIRA_APP) ─► vn.tsolve.jiraapp.controller
   JiraAppSolutionController, JiraAppPanelController, JiraAppTicketController
     └─ JiraIssueAccess (jiraapp.service) ── principal → TSolveUser (JiraAskerLookup, existing stand-in)
     │                                   └─ issue key → ACTIVE Project (jira_project_key) + membership
     │      └─ JiraIssueAccessReviewAdapter implements review.service.ReviewJiraAccess  ◄── ticket-review MSG64
     ├─ SolutionAiService (jiraapp.service) ── ai.client.LlmClient (JSON schema) | stand-in TSOLVE_STUB_AI
     ├─ ticket.service: SolutionFormTicketService (create Pending review), SolutionDraftService
     └─ search.query.service.TicketSearchService.search(user, query PUBLISHED)  ─► panel search
```

- **One access check, shared with `ticket-review`.** `JiraIssueAccess` turns the FIT principal and an issue key into
  `(TSolveUser, Project, member?, manager?)`. Its adapter is the first production `ReviewJiraAccess`, so
  `ticket-review`'s existing `GET …/review-feedback` (MSG64) starts working with the same rules (AC-008.7).
- **Tickets are written only by the `ticket` module**, in the shape `ticket-review` reads: source `SOLUTION_FORM`,
  status `PENDING_REVIEW`, `source_ticket_id` = issue key, `writer_id` = Account id, `writer` = full name. The
  existing triggers set `review_queue_entered_at` (009) and NOTIFY the Review Queue stream (019); the service sets
  `review_due_date` = today (Asia/Ho_Chi_Minh) + the Project's `review_range` (3 when not positive), the same rule
  as send-back (`TicketRepository` line 39, GBR-10).
- **Search reuses `search`** (`TicketSearchService`), which already limits results to the user's visible scope
  (Departments) and ranks with embeddings; the Jira App filters to PUBLISHED and maps to title, Ticket ID,
  Department (AC-008.4). Its failure is MSG06 in the panel only (AC-008.8).
- **AI** goes through `SolutionAiService`: a draft (issue text → five sections) and a rewrite (five → five), both
  `LlmClient.chat` with a JSON schema of the five sections, within the 20 s UI budget (NF 2); a config stand-in
  `TSOLVE_STUB_AI=available|unavailable` serves demos without Ollama (user decision 11/11).
- **UI → server only through `invokeRemote`** in `jira-app/TSolve_JiraApp/src/frontend/api/tsolveClient.js`, the
  same client as Ask T-Solve; issue text comes from `requestJira` as the viewer, so the backend needs no Jira token.

## Data Model

New changeset `027-jira-app-solution-draft.sql` (author `ticket`), tenant-table convention (AC-001.20):

```sql
CREATE TABLE solution_draft (                         -- Solution Form Draft (GBR-26), one per creator and issue
    solution_draft_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id        UUID         NOT NULL REFERENCES app_user (id),
    project_id        UUID         NOT NULL REFERENCES project (public_id),
    jira_issue_key    VARCHAR(50)  NOT NULL,
    content           JSONB        NOT NULL,          -- the five sections, unvalidated (AC-005.1)
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_solution_draft UNIQUE (creator_id, project_id, jira_issue_key)
);
```

- `ticket` gains a factory `Ticket.submitted(projectId, issueKey, title, writerId, writerName, sections,
  reviewDueDate)`; an empty Solution (Close) stores empty strings, as the columns are NOT NULL (AC-006.2). Title =
  issue summary cut to the column length.
- No new index: `uq_ticket_ticket_id` (changeset 003) already makes `source_ticket_id` unique in the Workspace, so
  an issue has at most one ticket of any source (AC-004.3); the insert uses `ON CONFLICT (source_ticket_id) DO
  NOTHING` and answers 409 MSG31 (Task 2, 12/11).
- `writer_id` and `review_due_date` are read-only in the `Ticket` entity, so the ticket is created by a native
  `INSERT … SELECT FROM project … RETURNING ticket_id` in `TicketRepository` instead of `Ticket.submitted(...)`.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Fresh branch | v2 from `main`, port spec/design/research | merge `main` into the 01/11 branch | User 11/11: 61 commits behind, old services predate `ticket-review` storage |
| Who resolves Jira users | `JiraIssueAccess` + production `ReviewJiraAccess` adapter | separate checks per module | One rule for the form, the panel and MSG64 (user 11/11) |
| Jira account link | Existing `JiraAskerLookup` stand-in (`TSOLVE_STUB_JIRA_ASKERS`) | new stub | Already used by Ask T-Solve; identity replaces it once |
| Membership | `project` + `project_membership` rows (`MEMBER`/`PROJECT_MANAGER`) | config stub of 01/11 | Real tables exist on `main` |
| Workspace of a FIT call | Stand-in `TSOLVE_STUB_JIRA_SITES` (`<cloudId>=<Workspace uuid>`), bound with `TenantScope.runIn`; an unlisted site is refused (403 MSG08) | default database; one dev Workspace for every site | User 12/11 (Task 3): identity's scope check and `ReviewJiraAccess` need a Workspace from the verified cloudId; platform replaces the stand-in |
| Ticket storage | `ticket` row `SOLUTION_FORM`/`PENDING_REVIEW`, triggers for entry time and NOTIFY, due date in the service | a new table | It is what `ticket-review` queues and reviews |
| Duplicate guard | Existing `uq_ticket_ticket_id` + `ON CONFLICT DO NOTHING` → 409 MSG31 | a new partial index; check-then-insert | Races (double click, retry after slow 201); the column is already unique |
| Search | `search`'s `TicketSearchService`, PUBLISHED only | keyword `ILIKE` (01/11) | User 11/11: same results as the web |
| AI | `jiraapp` `SolutionFormAi` wrapping the `ai` module's `SolutionAiService` (KAN-142) with an 18 s deadline on its own pool, the AC-002.2 prefill and `TSOLVE_STUB_AI` | own prompts on `LlmClient`; stub only | User 11/11; Task 4 (12/11) found `ai` already drafts and rewrites, so its prompts are reused |
| Open the form | Background script on own `JIRA_ISSUE_CHANGED` + panel button | panel only | RESEARCH-003: board drags send nothing, so both |
| Retry | Client: 3 retries on network error / 5xx (1 s, 2 s, 4 s), then MSG69; a 409 after a retry counts as success | none | AC-004.4, GBR-04 |
| Evidence | Not in the Jira App | 300 KB parts | RESEARCH-003; user 01/11, kept 11/11 |

## File Structure

```
jira-app/TSolve_JiraApp/
├── manifest.yml                    + jira:issueViewBackgroundScript, endpoint routes for the seven paths
└── src/frontend/
    ├── api/tsolveClient.js         + solution-form, solution, draft, rewrite, panel, search, preview, feedback; retry
    ├── background/index.jsx        status watch → form Modal
    ├── panel/IssuePanel.jsx        3.3 (replaces the hello-world src/frontend/index.jsx)
    ├── form/SolutionForm.jsx       4.1, form/validation.js, form/RewritePreview.jsx (4.2)
    ├── preview/TicketPreview.jsx   3.4
    └── messages.js                 + MSG04, 41, 64, 65, 66, 67, 68, 69, 70, 76, 77
backend/src/main/java/vn/tsolve/
├── jiraapp/controller/  JiraAppSolutionController, JiraAppPanelController, JiraAppTicketController
├── jiraapp/service/     JiraIssueAccess (+ impl), SolutionAiService (+ impl/LlmSolutionAiService, impl/StubSolutionAiService)
├── jiraapp/service/impl/JiraIssueAccessReviewAdapter (implements review.service.ReviewJiraAccess)
├── jiraapp/dto/         SolutionSections, SolutionFormRequest/Response, PanelResponse, JiraAppTicketResult, JiraAppTicketPreview
└── ticket/
    ├── entity/          Ticket.submitted(...), SolutionDraft
    ├── repository/      SolutionDraftRepository (+ TicketRepository finders by project and issue key)
    └── service/(impl/)  SolutionFormTicketService, SolutionDraftService
backend/src/main/resources/db/changelog/changes/027-jira-app-solution-draft.sql
frontend/openapi/tsolve-api.yaml    seven jira-app paths
.env.example                        TSOLVE_STUB_AI
```

## External Dependencies
- Forge: `@forge/react` (UI Kit), `@forge/bridge` (`invokeRemote`, `requestJira`, `events`). Scope `read:jira-work`
  (already); no new scope.
- Owners to tell: Phạm Tùng Dương (`ticket-review`: the first production `ReviewJiraAccess`, the
  `SOLUTION_FORM` rows and `uq_ticket_ticket_id`), Trần Hải Anh (`LlmClient` use and prompts), Đặng Nhật Minh
  (`ticket` additions: `Ticket.submitted`, `solution_draft`).

## Edge Cases & Risks

Dimensions analyzed: 1 User types, 2 Input extremes, 3 Timing, 7 Error cascades, 8 Authorization, 9 Data
integrity, 10 Integration · Skipped: 4 Scale (one form per user), 6 Environment (Jira renders UI Kit), 11 Compliance
(ids only in logs), 12 Business logic (no amounts).

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | Authorization | Non-member calls `POST …/solution` directly | Critical | 403 MSG08, nothing stored | Task 5 |
| 2 | STORY-004 | Timing | Two Finish calls race (double click, retry after a slow 201) | High | One ticket (`uq_ticket_ticket_id`, `ON CONFLICT DO NOTHING`), the other 409 MSG31; the client counts only a 409 MSG31 after a retry as success (BUG-204) | Task 2, Task 7 |
| 3 | STORY-004 | Integration | Ticket created but not visible in the Review Queue | High | `SOLUTION_FORM`, `PENDING_REVIEW`, entry time and due date set; queue IT reads it | Task 2 |
| 4 | STORY-002 | Input extremes | Exactly 10,000 characters; emoji / surrogate pairs | Medium | Code points counted; 10,000 accepted, 10,001 → MSG02 | Task 2, Task 7 |
| 5 | STORY-001 | Timing | Issue moved to Done from the board | Medium | No popup; the panel button opens the form | Task 6, Task 8 |
| 6 | STORY-005 | Authorization | User B reads or overwrites user A's Draft | High | Draft keyed by creator from the FIT; another's never returned | Task 2 |
| 7 | ~~STORY-009~~ | — | Removed 14/11 with the Jira search (Task 9) | — | — | — |
| 8 | STORY-007 | Error cascades | AI hangs or Ollama is down | Medium | Deadline inside 20 s → MSG68 / MSG67, form still usable | Task 4 |
| 9 | STORY-008 | — | Removed 14/11 with the Jira search (Task 9) | — | — | — |
| 10 | STORY-006 | Data integrity | Close after a Draft exists | Medium | Empty ticket created; the Draft deleted | Task 2 |
| 11 | STORY-008 | Integration | MSG64 for a ticket written by someone else | High | No feedback (ticket-review filters by writer); panel shows status only | Task 3, Task 8 |
| 12 | All | Integration | FIT without `principal` (app-level call) | High | 401/403, no data | Task 5 |
| 13 | All | Integration | FIT from a Jira site with no registered Workspace | High | 403 MSG08 before any read (`TSOLVE_STUB_JIRA_SITES` until platform's registry) | Task 3 |

## Builder Check — 2026-11-11 (inline)
- Decided: backend package for the new endpoints is `vn.tsolve.jiraapp` (as Ask T-Solve), not `tracker`.
- Decided: `writer` = Account full name and `writer_id` = Account id, as `ticket-review` reads both.
- Decided (Task 5): the Department of a Project is read by `jiraapp.repository.JiraProjectDepartmentRepository`
  (read-only SQL, like review's and identity's) because the harness gate keeps `project-integration` closed until it
  is planned; search results are filtered again with `ScopeAccessService.canAccessDepartment` and their Ticket IDs read
  through the new `ticket` `PublishedTicketService`. With `SEARCH_DEV_SCOPE` unset, search's `NoVisibleScope` returns
  nothing, so the panel shows MSG04 until identity supplies a real `VisibleScope`.
- Decided (Task 4): `ai`'s own fallback (description + last comment) is replaced by AC-002.2's (summary +
  description in Symptoms); a rewrite failure or timeout is `ai`'s `AiRewriteUnavailableException` (503 MSG68).
- Decided (Task 3): membership uses identity's `ScopeAccessService.canAccessProject` (MEMBER needs Ticket
  Resolver, PROJECT_MANAGER needs Project Manager); `AccountLookupService.findName` reads a former manager's name for
  MSG64; `ReviewDecisionApiIT`'s test `ReviewJiraAccess` is marked `@Primary` next to the production adapter.
- Open for the team, not blocking: tracker spec STORY-007/008 (Issue Panel status, Solution Form close) overlap
  this spec's STORY-008/STORY-006 — a change request removes them from the tracker spec before Task 8 lands.

## Change — 2026-11-14: no Published-ticket search in Jira (Task 9)
- Spec: AC-008.4, AC-008.5, AC-008.6, AC-008.8 removed; STORY-009 (3.4 Published Ticket Preview) retired. User
  decision in the dev-site test; search stays on the T-Solve web app (deviation from the SRS, to agree with the team).
- Chosen: remove the UI and the backend (`/jira-app/tickets/**`, `PublishedTicketService`,
  `JiraProjectDepartmentRepository`, the panel's `canSearch`). Alternative: hide the UI and keep the endpoints.
  Rationale: user 14/11 — no code nothing calls, and `TSOLVE_WEB_ORIGIN` (only for "Open in T-Solve") goes too.
- The panel now shows only the Solution status: MSG62, MSG65, MSG64, MSG77 + reopen, and the open-form button.
  The diagram's search and preview arrows and the Builder Check note on `JiraProjectDepartmentRepository` and
  `PublishedTicketService` (Task 5) describe the 11/11 design and no longer apply.
