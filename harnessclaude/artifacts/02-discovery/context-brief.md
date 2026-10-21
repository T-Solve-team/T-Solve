---
project: T-Solve
mode: blueprint
date: 2026-10-21
scope: Jira integration — ticket created / ticket Done flows (Jira App + server-side Jira App API)
---

# Context Brief: T-Solve — Jira integration

## Project Overview
T-Solve turns resolved Jira tickets into reusable Solutions and uses them to help with new tickets. This brief
covers the two Jira flows: what happens when a ticket is **created** and when it **moves to Done**. It follows
the team's architecture diagram (Report 4, I.1 Software Architecture): the **T-Solve Jira App** (Atlassian
Forge, UI Kit — Issue Panel, Solution Form, T-Solve Sign-in) talks to the **T-Solve Server**, whose
**Ticket Tracker Connector** (`tracker` package, `TicketTrackerClientFactory` → `JiraClient`) reads Jira and
hands work to the business modules (`triage`, `search`, `ticket`, `review`, `ai`, `identity`).

## Goals
- Every new ticket in a T-Solve project is triaged by AI and Staff see Solution suggestions in Jira.
- Every ticket that reaches Done gets an AI-drafted Solution that the Staff member who solved it checks and
  submits in one step, so knowledge is captured while the context is fresh.
- Project Managers review only Solutions a person has confirmed; only approved Solutions are suggested.

## Key Requirements

### Functional
1. **Ticket created — event.** When a ticket is created, the Jira App sends the event (cloudId, issue id/key,
   event type) to the server's Jira App API. The server (`tracker`, `JiraClient`) reads the ticket from the
   Jira REST API itself; the Jira App does not send ticket content (Q4 revised, diagram point 4).
2. **Project filtering on the server.** The app reports events for every project; the server ignores tickets
   of projects not linked to a T-Solve Project (`jiraProjectKey`) (Q2).
3. **Triage.** The server passes the new ticket to AI Ticket Triage (owned by another member) and returns the
   triage decision; the Jira App posts it as an **internal** comment on the ticket. Its content is defined by
   the triage owner (diagram point 2).
4. **Suggestions.** Suggestions are computed when the ticket is created and stored; the Issue Panel shows them
   to Staff and has a "search again" action that recomputes from the ticket's current content (Q8, Q9). Nothing
   is posted to the reporter/customer.
5. **Ticket Done — event.** When a ticket's status changes into the **Done status category**
   (`statusCategory.key = done`, any name) (Q5), the Jira App reports the event; the server reads basic fields
   (key, summary, description, issue type, priority, project, status, resolution, created/resolved dates), all
   comments, and the assignee (accountId + display name) (Q10, Q11).
6. **AI draft → Solution Form.** The server (`ticket` package, another member) creates an AI draft Solution from
   that data. The Solution Form in the Issue Panel shows the draft; the Staff member edits and submits it, and
   the submitted Solution enters the Review Queue for a Project Manager (diagram point 3).
7. **"No Solution needed".** The Solution Form lets Staff close a Done ticket without a Solution, with a reason
   (e.g. duplicate, not reusable); the ticket is then no longer flagged.
8. **Reminders.** When a ticket enters Done without a submitted Solution, the Jira App posts an internal
   comment mentioning the assignee; the Issue Panel shows a warning while the Solution is still missing.
9. **Done again.** Every transition into Done is reported; the server updates the unsubmitted draft for that
   issue, or starts a new draft if a Solution was already submitted/approved. The app keeps no "already sent"
   state (Q12).
10. **No workflow enforcement.** The app does not block, revert, or validate transitions (validators and
    auto-revert were rejected: Jira Automation conflicts, per-workflow setup, admin-level scopes).
11. **Identity.** The Solution Form needs the T-Solve user behind the Jira accountId; the account link and the
    T-Solve Sign-in screen belong to the `identity` owner — this work only calls their interface.

### Non-Functional
1. **Authentication — Forge Invocation Token.** The Jira App calls the server through Forge Remote. The
   security layer verifies every call: JWT signature against Atlassian's JWKS (cached), issuer = Forge,
   audience = this app's id, not expired, and the token's cloudId belongs to a registered Workspace; otherwise
   401/403. Tokens are never logged. No shared API key (Q14 revised, diagram).
2. **Server access to Jira** uses the Forge Remote app system token, not stored Jira API tokens.
3. **Delivery reliability:** event delivery to the server is retried a bounded number of times with back-off
   (e.g. Forge Async Events, 3 attempts); after the last failure only issue id + error type are logged (Q13).
4. **Privacy:** never log ticket content or personal data — ids, keys, status codes, error types only
   (`jira-app/AGENTS.md` rule 4). Error messages never embed Jira response bodies. Text sent to the AI service
   is PII-masked by the `ai` module (architecture.md).
5. **Least privilege:** only the scopes the flows need — reading issues/comments and adding comments
   (`read:jira-work`, `write:jira-work`) plus panel/remote needs; never `manage:jira-configuration`.
6. **Internal comments:** on Jira Service Management projects, comments must be internal, never public.
7. **One backend client** in the Jira App (base remote, errors); **UI Kit only**; glossary words exact.

## Constraints & Assumptions
- **Ownership:** the user builds `jira-app/` and the server-side `tracker` work (Jira App API endpoints, FIT
  verification, `JiraClient`). `triage`, `search`, `ticket` (AI draft, Solution Form submission), `review`,
  `ai` and `identity` are owned by other members; this work calls their service interfaces and uses stubs
  until they exist.
- The Forge app is not created yet; the one-time setup in `jira-app/AGENTS.md` is a prerequisite.
- Users run both company-managed and team-managed projects; nothing here depends on project type.
- The Jira App API endpoints are not in `frontend/openapi/tsolve-api.yaml` yet; they are added to the contract
  before code. Server endpoints follow `backend/AGENTS.md`.
- The user writes the code; Claude advises and reviews.
- [NEEDS CLARIFICATION: SRS (SEP490_G89) not available — Jira App screens and MSG texts not checked.]
- [NEEDS CLARIFICATION: interfaces of `triage`, `search`, `ticket`, `identity` — agree with their owners in plan.]
- [NEEDS CLARIFICATION: how many suggestions the panel shows and its empty state — settle in spec.]

## Risks
| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Other members' packages not ready | High | High | Agree interfaces early; stub them; vertical slices still demoable |
| FIT verification done wrong (no aud/cloudId check) | High | Medium | Explicit checks + tests per check; security review |
| Server down → lost events | High | Medium | Bounded retry; "search again" in panel |
| Staff forget to submit Solutions | Medium | Medium | Internal comment + panel warning; "No Solution needed" to clear noise; measure before adding auto-submit |
| Ticket content leaks via logs | High | Medium | Log ids/codes only; review rule |
| Public comment on JSM reaches customers | High | Low | Always internal comments; test on a JSM project |
| Large tickets / many comments | Medium | Medium | Paginate comments; payload limits decided in design |

## Suggested Module Boundaries
- **jira-app** (`jira-app/`, owner: user): triggers for issue created / moved to Done, event delivery with retry,
  Forge Remote client, Issue Panel (suggestions, search again, Done warning), Solution Form UI, internal
  comments (triage decision, Done reminder).
- **tracker** (`backend/` package `tracker`, owner: user): Jira App API endpoints, FIT verification in the
  security layer, `TicketTrackerClientFactory` + `JiraClient` reading issues/comments, project filter, hand-off
  to `triage` / `search` / `ticket`.
- Depends on: `triage`, `search`, `ticket`, `review`, `ai`, `identity` (other owners), `project` (jiraProjectKey).

## Sources
- Discovery conversation with the user, 2026-10-21 (Q1–Q14 and diagram points 1–4)
- Architecture diagram "T-Solve – Architecture Diagram (Report 4, I.1 Software Architecture)", shared in chat
- User's prototype Forge code (panel resolver + `onIssueUpdated` trigger), pasted in chat
- `jira-app/AGENTS.md`, `frontend/openapi/tsolve-api.yaml`, `harnessclaude/artifacts/03-modules/architecture.md`
- Forge docs: Jira workflow validator module — https://developer.atlassian.com/platform/forge/manifest-reference/modules/jira-workflow-validator/
