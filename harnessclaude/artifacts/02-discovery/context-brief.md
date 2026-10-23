---
project: T-Solve
mode: blueprint
date: 2026-10-22
---

# Context Brief: T-Solve

## Project Overview

T-Solve (Ticket Solution Repository Application) is a knowledge layer next to a ticket tracker (Jira in the MVP);
it does not replace it. A Ticket Resolver writes a Solution when they resolve an issue; the Project Manager of
that Project reviews it; Published tickets are searched and reused, suggested back to new issues by AI Ticket
Triage, and used by the Knowledge Chatbot together with the Wiki documents of each Department's Space.
Capstone project SEP490_G89, FPT University, Fall 2026.

Report3 is the current requirements baseline. It changed the product the code was started on (SRS v2): five
roles, several per user; every message code renumbered; three new features (AI Ticket Triage, Wiki, Knowledge
Chatbot). The code on `main` still follows SRS v2 in the web app's roles, catalog and routes, and in the backend
skeleton.

## Goals

- Deliver the 13 Report3 features as working slices (API + screens) on the VPS for the demo, then the testing
  phase.
- Every member plans and builds their own module through HarnessClaude, on one shared module map, without
  colliding with the others.
- Shared bases first (roles, session, errors, message catalog), so feature modules build on them.

## Key Requirements

### Functional
Report3 groups 53 use cases into 13 features:
1. **Role-Based Access Control** (UC-01–09): sign in with the company account (OIDC, Google in the MVP), log out
   (clears the session's chat history), my profile, user list, add user, account status, roles and Department,
   hand over Projects and Spaces.
2. **Workspace & Department Management** (UC-10–15): company information of the single Workspace; create,
   update, delete and restore Departments.
3. **Ticket tracker integration** (UC-16–18): add and configure a Project linked to a tracker project; add and
   remove Ticket Resolvers.
4. **Solution Authoring & Standardisation** (UC-19): submit a Solution from the tracker's Solution Form (five
   sections, AI drafting and rewriting, drafts).
5. **Ticket Import & Repository** (UC-20–25): import from the tracker, CSV or manual entry (Published at once,
   GBR-23), import history, ticket detail, unpublish and re-publish.
6. **Intelligent Retrieval** (UC-26–27): semantic search of Published tickets; ticket list.
7. **Reuse & Feedback** (UC-28): Helpful / Not helpful feedback on Published tickets.
8. **Review & Approval Workflow** (UC-29–33): review queue, approve / reject / request changes, expired tickets,
   review history, JOB-02 expiry.
9. **Dashboard & Audit** (UC-34–35): knowledge dashboard (Department or Workspace), audit log.
10. **AI Ticket Triage** (UC-36–37): Simple / Complex classification of a new issue; a suggested solution posted
    on Simple issues from Published tickets and Wiki documents; triage history.
11. **Wiki Space Management** (UC-38–47): Space access, folders (≤ 3 levels), trash.
12. **Wiki Document Management** (UC-48–50): upload, view, delete Wiki documents.
13. **Knowledge Chatbot** (UC-51–53): answers with citations from Published Solutions and the Wiki documents the
    user may see; session-only history; ratings.

Roles (UC list 4.1, GBR-28): Employee (every account), Ticket Resolver, Project Manager, Knowledge Manager, Admin;
a user can hold several and the permissions add up. Access per action: Permission Matrix 4.4 with footnotes ¹–⁹.

### Non-Functional
1. Personal data is PII-masked before any text goes to an AI model; ticket details never appear in Chatbot
   answers or triage comments (GBR-35, GBR-36).
2. AI is optional: an AI failure never breaks a flow (GBR-12).
3. The backend re-checks every permission; the UI only hides what a user cannot do.
4. Every acceptance criterion has a test that names its id; every bug is logged in `docs/bug-log.md`.
5. One deployable backend (modular monolith) and one static web app, Docker Compose + Caddy on one VPS.

## Constraints & Assumptions
- **Source of truth:** Report3 (`UC_list_and_Actors` 4.1, 4.2, 4.4; `Business_Rules` GBR-01…36; `System_Messages`).
  The FE Lead's decisions D1–D6 (gap report, 21/10) win where they differ — Request changes keeps several tickets
  with one comment (D1); a Solution is never empty, no MSG20 (D2); Hand Over messages (D3); catalog, upload types,
  `{tracker_name}` "Jira", menu order and Workspace Setup (D4); every UC is settled (D5); Architecture.jpg stays
  as it is (D6). Report3 will be corrected. SRS v3460 is a layout template only.
- **Architecture:** `artifacts/03-modules/architecture.md` (modular monolith, REST under `/api/v1/**`, the
  contract in `frontend/openapi/tsolve-api.yaml`, PostgreSQL + pgvector, Liquibase). Team rules: `backend/AGENTS.md`,
  `frontend/AGENTS.md`.
- **Team:** one owner per module (or per part of a module) from the team table; Trần Trung Hiếu acts as Team Lead
  while Đặng Nhật Minh is away. Owners plan their own module; the Team Lead approves the specs.
- **Timeline:** product by 15/11 (demo on the VPS), Report 4 on 07/11, testing after 15/11.
- **Work in flight:** PR #26 (KAN-24) builds the review flow; its module joins this map.

## Risks
| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| The code follows SRS v2 (roles, MSG codes, routes) | Features built on the wrong base | Confirmed | `be-foundation` and `fe-foundation` first; feature modules after them |
| Report3 still differs from D1–D6 | Members follow Report3 and contradict the decisions | High | Specs cite the decisions; document owners correct Report3 |
| Modules overlap in shared files | Gate and trace conflicts, merge conflicts | Medium | Test paths never overlap; shared contract files listed where needed; one owner per module |
| AI quality and speed (local models, Vietnamese text) | Triage and Chatbot below expectations | Medium | AI optional (GBR-12); fallbacks per flow; benchmark before the demo |
| Short time, part-time team, a member away | Modules late | High | Build order by dependency; parallel work against mocked interfaces |

## Suggested Module Boundaries
Full-stack modules cut along the Report3 features, two foundations and the Jira App — see
`artifacts/03-modules/module-map.md` for the list, owners, dependencies and order.

## Sources
- Report3 (team docs folder `Report3/`): `UC_list_and_Actors` (4.1 Actors, 4.2 UC-01…53, 4.4 Permission Matrix
  and footnotes ¹–⁹), `Business_Rules` (GBR-01…36), `System_Messages` (103 codes), `Architecture.jpg`.
- `Claude decisions/`: `T-Solve_FE_spec_gap_Report3.md` (decisions D1–D6), `T-Solve_UI_design_v3_decisions.md`,
  `T-Solve_Jira_ticket_convention.md`.
- The code on `main`, `backend/AGENTS.md`, `frontend/AGENTS.md`, `architecture.md`; PR #26 (KAN-24).

## Ticket review implementation context retained from its approved work

## Project Overview
Implement the requested Approve, Reject and Request changes flow against the team documentation folder Report3/ (UC list, Business Rules, System Messages), with FE Lead decisions D1/D2 taking precedence while document corrections are pending, using the existing Spring Boot backend and React frontend. Discovery is scoped to this request, not the entire SRS.

## Goals
Provide review APIs and UI using the real API client, with executable verification and a permanent bug log. Work sequentially without subagents.

## Key Requirements

### Functional
1. UC-29: show Pending review Solution Form tickets belonging to Projects managed by the reviewer; filter by Project and default to earliest review due date.
2. UC-30 / GBR-08 / GBR-09: approve or reject one, selected or all queued tickets. Approve publishes; rejection requires one reason for the selection and is final.
3. UC-30: request changes for one, several selected or all eligible Solution Form tickets from the queue, and one from Ticket Detail, with a shared mandatory comment; preserve every review due date (D1 / FE-34).
4. Persist one decision and one audit event per ticket; make rejection reasons and change comments available to the email integration.
5. Use Report3 review message codes MSG13–MSG19. Remove MSG20 under D2: empty Solution is not a valid Ticket state. Keep other catalog entries unchanged for the frontend foundation regeneration.

### Non-Functional
1. Every acceptance criterion has a named automated check. Backend verification includes Maven verify and Spotless; frontend verification includes formatting, lint, typecheck and tests.
2. Every discovered bug remains in docs/bug-log.md after its fix; regression tests cite bug IDs.
3. Failed or concurrent decisions must not leave partial status, history or audit writes.

## Constraints & Assumptions
- Confirmed by user: implement backend and frontend, using real API calls.
- Confirmed by user: implement only review, with auth/email integration interfaces; explicitly document that complete live operation is not yet available.
- Existing architecture fixes Java 21, Spring Boot, PostgreSQL, Liquibase, MapStruct, React and generated OpenAPI clients. No new framework is required.
- Identity, Project membership and mail delivery/retry implementations are outside this request. Their integration contracts must fail closed and must not simulate success.
- No commits until the user approves each completed feature. No push, merge or PR requested.

## Risks
| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Layout template treated as requirements over FE decisions | Incorrect single-ticket and empty-Solution behavior | Confirmed | Follow Report3 plus D1/D2; restore canonical main bug IDs and retain the rejected bulk restriction as Won't fix |
| Identity and Project data are absent | Cannot enforce real PM permissions without integration | Confirmed | Explicit server-side identity/project ports, contract tests and no client-provided reviewer identity |
| Email infrastructure is absent | Writers cannot receive real notifications yet | Confirmed | Durable pending notification handoff; document missing sender and GBR-27 retry responsibility |
| Core schema predates ticket-level review | Unsafe reuse of Solution lifecycle | Confirmed | Add forward-only review schema; retain existing schema and legacy endpoints |
| Shared frontend MSG definitions conflict | Existing consumers can display incorrect messages after remapping | High | Inventory affected consumers and run frontend suite |

## Suggested Module Boundaries
One ticket-review module owns the review data, queue, decisions, history/audit persistence, notification handoff, API contract and review UI. Authentication, Project administration, mail transport, Jira submission and knowledge retrieval remain external integrations.

## Sources
- Authoritative sources: `Report3/UC_list_and_Actors (1).docx`, `Report3/Business_Rules.docx`, `Report3/System_Messages.docx` in the team documentation folder; verified directly from their OOXML text. Portable index: `harnessclaude/artifacts/00-baseline/Report3/README.md`. SRS v3460 is a layout template only.
- Overrides: FE Lead D1 (21/10, reaffirmed in PR review) retains multi-ticket Request changes and one shared comment; D2 removes the empty-Solution case and MSG20 handling. GBR-04/08/09, UC-30 and MSG20/MSG70 await correction in Report3.
- Report 3: UC-24, UC-29, UC-30, UC-32; UI 7.1–7.4; GBR-05–GBR-11, GBR-23, GBR-27; System Messages.
- Graphify: 1,128 nodes and 2,476 edges, queried before source inspection. SQL extraction is unavailable because tree_sitter_sql is missing; SQL was inspected directly.
- Existing frontend review feature, OpenAPI stub, MSG catalog and FE-34 decision; backend Solution, resolved ticket snapshot, initial schema and error handler.
