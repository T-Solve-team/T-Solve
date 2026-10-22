---
project: T-Solve
mode: blueprint
date: 2026-10-20
---

# Context Brief: T-Solve ticket review

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
