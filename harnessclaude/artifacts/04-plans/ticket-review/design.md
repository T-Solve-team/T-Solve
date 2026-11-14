---
module: ticket-review
date: 2026-11-01
---

# Design: ticket-review

Spec: artifacts/03-modules/ticket-review/spec.md (31 criteria, approved 2026-11-01T01:51:05.532Z).
Branch: `feat/KAN-24-review-decisions`, based on main `2a4e5fe`. PR #26 is reference material only.

## Architecture Overview

Keep `features/review` as the owner of queue, decision controls, popup forms, mutation hooks and mutable mock data. The existing `features/tickets/pages/TicketDetailPage.tsx` becomes a thin integration importing review UI only from its barrel; review tests exercise it through the router without claiming unrelated tickets tests. No new route or menu registration is needed.

Queue uses the existing generated review operations with optional Department UUID, paging and supported sort. Ticket Detail uses the new proposed `GET /api/v1/tickets/{ticketId}` operation, reusing compatible shared source/status/Project types. Both call the same decision hooks and existing FormModal/ConfirmDialog. OpenAPI changes precede runtime edits; all generated output comes from orval.

Session roles and Project memberships determine visible controls. Mock requests resolve the synthetic actor from the current dev session, then recheck role, Project management and fixture Workspace membership before reading or mutating. They do not accept caller-selected actors/Workspaces. This is a frontend demonstration contract, not a production backend security implementation.

Queue server data remains in TanStack Query. Selected UUIDs and open dialog are local UI state; they are not a copy of queue records. Mock ticket/history records remain feature-local module state and reset through the existing `resetMockData` entry point. Keep imports from central mocks compatible; no new users-to-review reverse dependency or global session adapter.

## Data Model

### Proposed OpenAPI shapes

| Shape | Fields / reuse |
|-------|----------------|
| ProjectRef | `projectId: UUID`, `name: string`; first verify all consumers of this existing review model |
| ReviewQueueItem | Existing fields; `ticketId` and nested Project id become UUID strings; displayed `sourceTicketId` remains a source key |
| TicketIdList | `ticketIds: UUID[]` |
| Decision bodies | unique `ticketIds: UUID[]`, min 1 / max 500; Reject adds trim-required `reason`, Request Changes adds trim-required `comment`, each max 1000 |
| ReviewDecisionResult | Existing `{ count: integer }` |
| ReviewTicketDetail | `ticketId`, `sourceTicketId`, `title`, `description`, `project`, `source`, `status`, nullable author UUID/name, `reviewDueDate`, `solution`, `evidence`, nullable last review comment; immutable confirmed values |
| ReviewSolution | Reuse compatible existing Solution fields; otherwise a review detail object containing symptoms, rootCause, resolutionSteps and nullable workaround/scope, using text values |
| ReviewEvidence | `id: UUID`, filename, nonnegative sizeBytes, nullable URL; no binary upload/download implementation |

The detail operation is tagged for the review feature initially, while its URL remains the shared Ticket resource namespace. No duplicate endpoint at `/reviews/{id}` is introduced. Existing `/api/v1/tickets` list/search contracts stay intact. The future backend owns canonical records; this proposal does not create another production Ticket model or any table.

Detail action eligibility derives from confirmed source/status plus `session.projects` UUID membership with Project Manager role; authorship uses trusted UUIDs, never display names. The mock record stores its own Workspace marker for foreign-fixture negative cases but does not expose a request parameter for it.

### Mock state and transitions

Extend the existing seeded queue data into feature-owned ticket records with stable UUIDs consistent with central Project fixture ids. Keep recognizable source keys/titles and enough rows for pagination; seed separate permitted Published/imported, Rejected, authored-unpublished and foreign/unmanaged fixtures for detail/security cases. Do not import or mutate another feature's store. Test-only arrangements may seed focused records through a small feature-local reset/fixture utility.

Decision history is an append-only array with ticket UUID, actor UUID, action, timestamp, before/after status and nullable reason/comment. It is visible to mock contract tests; no public history route or Review History UI is added.

Request sequence: parse and validate entire body → authenticate/authorize actor → resolve every affected ticket in the current Workspace → for Request Changes validate permitted source before pending status → validate every pending state → apply all status/history writes synchronously, with no await between validation and writes. Invalid/missing ids produce 400/404; unauthorized direct decisions produce 401 MSG07 or 403 MSG08; stale state produces 409 MSG06. Field validation uses 400 MSG01/MSG02 with `reason`/`comment` field errors. Non-Solution Form changes use MSG18. Preserve Solution, Evidence, source Jira state and review due dates. A duplicate retry after success rejects the stale batch rather than appending history again.

Decided in planning (builder check): trimming detects blank reason/comment only. Apply the 1,000-character limit to raw input and retain valid entered text unchanged in payload/history; do not silently trim it before counting or storage. Queue due dates remain required non-null ISO date-times; detail due dates may be null. Default queue order is due date ascending, then stable UUID lexical order to break ties.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Delivery | User selected frontend + mutable MSW/OpenAPI | Backend plus frontend now | Canonical Ticket, identity/access and audit interfaces are not ready on main; do not copy PR #26's private tables/adapters |
| Starting point | User selected new branch from main | Continue PR #26 | Avoid carrying legacy conflict/history and unrelated old shared changes |
| Filter | User selected Department; Project remains sortable column | Older Jira Project filter | Current frontend guideline and main implementation win by explicit user decision |
| IDs | UUID strings throughout review | Retain numeric ids or coerce UUID paths to numbers | Current shared conventions and outstanding reviewer feedback; source issue keys remain strings |
| Selection limit | Keep existing agreed 500-ticket maximum in one constant and OpenAPI | Silent truncation or unbounded batches | Preserve selection and prevent oversized transactions; typed counter displays selected count and maximum as UI metadata, with `role=status` / polite announcement. No invented catalog error or misleading character-limit MSG02 |
| Selection refresh | Complete eligibility query while selection exists; reconcile only fresh successful results | Prune against current page or stale cache | Off-page rows must survive; new selections must not disappear during cached refresh |
| Polling | Preserve the existing visible-tab 5s queue polling; refresh eligible ids after a fresh queue result or settled mutation | A second unconditional 5s full-id polling loop | Reuse FE-10, avoid redundant continuous requests; KAN-154 real-time work stays outside scope |
| Mutation settlement | Invalidate all affected keys on success/error; queue popup completion does not await refetch | Await all queries before closing popup | A committed decision is not failed because a follow-up query is slow or broken |
| Detail composition | Review exports the scoped detail/actions through its barrel; existing ticket page delegates | Cross-feature deep imports or a new route | Keep ownership and test trace inside review without app registration edits |
| Evidence links | Render only relative same-origin or http(s) API references; format byte sizes locally | Raw executable schemes or unimplemented downloads | No new dependency or file service; filenames and content remain text |

### Query and UI behavior

Use existing generated query-key functions for review pages, matching ids, Ticket Detail and Ticket List. Invalidate queue/id prefixes and affected detail keys on either outcome; also invalidate the existing ticket-list prefix. Preserve success catalog feedback only for the mutation response. Popup forms handle field errors locally; global errors retain existing transport behavior.

Selection is keyed to the Department value in the URL, so navigation Back/Forward resets it as well as direct filter changes. A Select all response for a previous filter must not populate the new filter. During eligibility refetch, retain selections; when the fresh result is accepted, remove only ids absent from that complete set. An eligibility error leaves choices available for retry but does not authorize a mutation against unusable data. Disabled action controls and a synchronous submitting guard prevent duplicate sends; failure keeps form data and eligible selections.

Ticket Detail displays existing shared status tags and read-only fields. Only the ticket's managing PM can use pending-ticket decisions. A non-PM never receives a link to Review Queue. An imported Published detail provides accessible MSG18 explanatory text; it does not manufacture a pending imported fixture. An invalid UUID renders MSG04 before a query. 403 and other load errors use ErrorState with retry; a 404 is MSG04. API-provided strings render as text, including reason/comment and Evidence filenames.

## File Structure

```text
frontend/openapi/tsolve-api.yaml
frontend/src/shared/api/generated/             # generated only
frontend/src/features/review/
  components/                                 # existing popups/queue, scoped actions/detail
  hooks/                                      # eligibility, mutation invalidation, detail
  mocks/                                      # seeded UUID records, atomic decisions/history
  pages/                                      # queue and module-owned router tests
  index.ts                                    # only cross-feature import surface
frontend/src/features/tickets/pages/TicketDetailPage.tsx
frontend/src/features/tickets/pages/TicketDetailPage.module.css  # only if needed
docs/bug-log.md
```

Exact internal filenames are builder choices. Reuse existing shared UI/API modules rather than adding a framework, generic state machine or new package. No change to shared http/session/catalog implementation is planned; if one is proven necessary, obtain its explicit scoped authorization first.

## External Dependencies

- Existing React, Ant Design, Router, TanStack Query, orval, MSW, Vitest and Testing Library only.
- No live backend, Jira, Email Service, worker, Docker or database is needed for this approved frontend slice. Reports must distinguish mock state/history from durable audit or delivery.

## Edge Cases & Risks

All 12 dimensions were reviewed. Production compliance/retention, database migrations and backend transaction/security enforcement are excluded from implementation; mock content/history and the future integration contract still receive explicit tests. Each row supplies at least two concrete boundary cases.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| R1 | 001,005,006 | User types | Signed out, Employee-only, PM with one Department, PM+KM; own-authored versus another user's unpublished ticket | High | Route/mock denial or correct managed/authored visibility, no leaked controls/data | T1,T2 |
| R2 | 003,004,005 | Input extremes | Empty/whitespace reason/comment; 1000/1001 characters; malformed/null ids; duplicate UUIDs | High | Catalog validation, no writes, preserve input and selection | T1 |
| R3 | 001,005 | Timing | Fresh ticket selected during cached eligibility refetch; Select all resolves after Department changed; double-click while mutation pending | High | No stale pruning/filter leakage or duplicate requests | T1 |
| R4 | 001,005 | Scale | 0/1/20/21 queue rows; cross-page selection; 500/501 selected ids; one matching row on a later page | High | Correct pagination/full id scope, off-page retention, over-limit controls and guidance | T1 |
| R5 | 002,003,004 | State transitions | Cancel each dialog; decision succeeds then retry; Rejected ticket resubmitted; 404/409 after another PM decides | High | No cancel writes, one history item per success, reject stale states, refresh then retry eligible remainder | T1,T2 |
| R6 | 001,006 | Environment | Keyboard-only decisions/MSG18 explanation; narrow viewport; long title/filename; offline lookup | Medium | Labeled controls/status announcements, usable wrapping, catalog error/retry | T1,T2 |
| R7 | 005 | Error cascades | One bad ticket in batch; mutation succeeds but detail/queue refetch fails; slow query after popup submit | High | No partial writes for rejected batch; successful popup closes; query errors surfaced independently | T1,T2 |
| R8 | 001,005,006 | Authorization | Foreign Workspace ticket id; unmanaged Project; forged non-PM mutation; self-authored unpublished detail | High | Authoritative fixture scope and trusted actor UUID; deny forged mutations, restrict unpublished reads | T1,T2 |
| R9 | 005 | Data integrity | Mixed valid/stale batch; duplicate retry; immutable Solution/Evidence/Jira state/due date; same display name with different actor ids | High | Atomic snapshots, preserved fields, UUID identity, append history only on success | T1 |
| R10 | 005,006 | Integration | UUID payload/detail path; deterministic generation; existing ticket list/search/client consumers; old imported fixture with numeric Project ids | High | Update all review model consumers, no numeric coercion or unrelated generated drift | T1,T2 |
| R11 | 005,006 | Compliance | Comment/filename containing markup; timer/query cleanup after errors; mock reset between users/tests | High | Render text, no log/persistent content, no pending work after teardown or cross-test state | T1,T2 |
| R12 | 002,003,004,006 | Business logic | Single versus bulk Approve; one comment for many tickets; imported Published Request Changes; managing PM self-approval | High | MSG19 only for bulk; shared comment; reachable MSG18 restriction; self-approval permitted; no MSG20 | T1,T2 |

Open questions for the spec owner: none currently identified. Scope, filter, D1/D2, UUIDs and limits come from the approved spec and recorded decisions.

## Risk Review — 2026-11-01 — Verdict: CAUTION

Independent persona findings:

- Architect: reuse the existing review feature and shared Ticket namespace; keep the backend and canonical model work out of this slice. A thin Ticket Detail adapter is the only additional cross-feature entry point.
- Security: visible controls cannot prove authorization. The mock must reject forged role/Project/Workspace choices, use actor UUIDs and avoid unsafe Evidence URLs. Document future production enforcement explicitly.
- Performance: page polling already exists; avoid a duplicate id poll and per-ticket decision requests. One full-id request supports cross-page selection; max 500 limits writes in the proposal.
- User: stale cached selection must survive until fresh eligibility arrives; failure must retain entered forms; popup success must not depend on unrelated refetch latency. Explain MSG18 and selection limits to keyboard users.
- Devil's advocate: much of the queue UI exists. Reuse it; port only applicable fixes from PR #26 after checking them against current main. A wholesale cherry-pick would reintroduce obsolete Project filtering, numeric ids and private backend tables.

Agreements (all five): no duplicate workflow/backend; protect the entire batch; preserve failure recovery; prove cross-feature contracts through generated types and tests. Conflict: immediate popup completion versus stale-query consistency. Resolution: complete on the mutation response and invalidate in the background, with detail/queue query states surfacing follow-up failures; never treat a successful mutation as rolled back by a query error.

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Review-specific UUID conversion breaks another consumer | High | Search all ProjectRef/TicketIdList and operation consumers before changing YAML; typecheck/full suite/generation | T1 |
| Stale cache drops legitimate selected ids | High | Deferred refresh and filter-change regression tests; reconcile only accepted fresh eligibility | T1 |
| Mock history is mistaken for backend audit or Jira delivery | High | Scope labels in contract/reports; no production adapters, database changes or delivery-success claims | T1,T2 |
| Detail exposes unpublished content or unsafe link | High | Author/manager UUID policy and foreign/reader negative cases; safe-scheme tests | T2 |

## Proposed implementation tasks and iteration placement

These are reviewable task definitions, not yet written into the iteration-scoped tasks file. Both tasks are proposed for iteration 1 in dependency order. User approval of the design and grouping is required before creating the scoped plan and advancing phase.

### Task 1: Complete UUID review queue and atomic mock decisions

**Stories:** STORY-001, STORY-002, STORY-003, STORY-004, STORY-005
**Feature:** review-queue-decisions
**Acceptance:** AC-001.1–001.6, AC-002.1–002.3 and AC-002.5, AC-003.1–003.4, AC-004.1–004.4, AC-005.1–005.7 (Ticket Detail callers completed in Task 2).

**Work:** Update the existing OpenAPI ids/limits, add the proposed detail shapes/query needed by Task 2, regenerate, convert affected queue callers/fixtures/tests, implement the feature-local ticket/history store and atomic guarded handlers, and complete queue selection/mutation/popup recovery. Mock detail contract and negative reader/Workspace cases can be established here so the second task adds UI against a settled proposal. Preserve Department filtering and queue polling. Use existing UI and catalog; no backend or shared runtime change.

**Files:** `frontend/openapi/tsolve-api.yaml`, generated API output, existing review `components/`, `hooks/`, `mocks/`, `pages/ReviewQueuePage*` and tests under review. One local maximum-selection constant if multiple components need it. `docs/bug-log.md` for every reproduced defect; no wholesale import of PR #26's bug ledger or code.

**Dependencies:** Current approved spec, accepted design/task grouping and `ticket-review` planned phase. **Size:** medium–large vertical slice; do not begin Task 2 before verification and the user's commit checkpoint.

**Tests:** R1–R5 and R7–R12 for queue/contracts, plus R6 keyboard/limit feedback. Run every affected existing queue test, meaningful new mock and deferred-response regressions, all required frontend checks and generation determinism. Browser-check queue/filter/Select all/single+bulk Approve/Reject/Request Changes/cancel/failure at 1366 × 768. Report the actual criterion coverage; do not mark the whole module complete while detail criteria remain pending.

### Task 2: Add Ticket Detail review controls and read-only content

**Stories:** STORY-002, STORY-003, STORY-004, STORY-005, STORY-006
**Feature:** review-ticket-detail
**Acceptance:** AC-006.1–006.6 and the detail callers of AC-002.3, AC-003.*, AC-004.1/004.4 and AC-005.4–005.6.

**Work:** Implement a feature-owned detail view/actions through the review barrel and integrate it into the existing Ticket Detail placeholder. Render confirmed fields, populated Solution, safe Evidence references and applicable source/status/role actions using shared UI. Wire all three decisions to Task 1 hooks. Exercise author/manager/ordinary-reader and 404/409 recovery rules, including imported Published MSG18. No new registration or generic Ticket management feature.

**Files:** New review detail component/hook and module-owned tests; `features/review/index.ts`; `features/tickets/pages/TicketDetailPage.tsx` and a local CSS module only if needed. Reuse Task 1 OpenAPI/generated shapes; any discovered schema adjustment is generated before code. `docs/bug-log.md` only for established defects.

**Dependencies:** Task 1 verified and committed with user agreement. **Size:** medium.

**Tests:** R1, R5–R12, router integration with real hooks/MSW, readonly fields, unsafe URLs, role/access controls, single decision payload, failed-mutation input retention, and stale detail refresh. Full frontend checks, 31/31 criterion trace, deterministic generation and browser verification at 1366 × 768 plus narrow-width/keyboard inspection. Keep the backend and real audit/Jira delivery exclusions visible in final reports/PR.

Each task gets a session log and test/review reports using its feature slug. Full frontend verification is format:check, lint, typecheck, test, build and generate:api with no additional generated diff. On Windows use an ignored workspace TEMP directory when necessary; never change test expectations/timeouts or ignore runtime errors to obtain a pass. After verification, present files/results and a Jira-conventional proposed message to the user; commit code and harness separately only with agreement. Push/create the new PR only if requested. Do not close or modify PR #26 under this plan.

## Builder and artifact validation — 2026-11-01

A fresh-context builder reviewed the spec, this design including task definitions, module and architecture artifacts. No blocking findings: the two tasks supply behavior and test targets for all 31 criteria. One minor precision point about whitespace normalization was resolved above using the existing form convention; no spec or acceptance criterion changed. No additional scope was introduced. Design structure validation: architecture, data model, chosen/alternative/rationale decisions, file structure, dependencies, edge/risk rows and ordered task definitions are present. `sdd.ts check ticket-review` passes 6 stories / 31 criteria and `sdd.ts diff ticket-review` confirms the spec matches its approval.

Design and proposed iteration grouping await user review. No iteration-scoped task file or code has been written.

## KAN-158 extension — Expired Tickets (STORY-007)

Architecture: extend the existing review feature; reuse generated TanStack Query clients, DataTable, cells, shared states, session/Department selector and the KAN-24 Ticket Detail. No new route, package, shared runtime or backend code. Base: KAN-24 a4500fb, explicitly authorized by the user; branch feat/KAN-158-review-expired-tickets. Existing unrelated dirty files stay outside this feature's commit.

Data model: ExpiredTicketItem adds expiredAt and previousStatus to the existing ticket summary fields. The mock record carries the Project's current reviewRangeDays (positive integer) and the expiry metadata. Reuse reviewStore for all transitions; append SEND_BACK history records rather than creating a second Ticket store. The real backend must eventually provide expiry metadata and current range authoritatively.

Decisions: chosen Department filter (existing approved review convention and frontend vocabulary), alternatives Project filter/both; rationale keep FE Lead convention despite inconsistent SRS 7.5 field table. Chosen generated GET expired-tickets and ticket-ids, POST reviews/send-back; alternatives raw fetch/reusing pending-only queue; rationale typed isolated contract and correct expiry fields. Chosen no confirmation for send-back, matching SRS MSG25 success-only behavior. Decided in planning: expiredAt descending with UUID tie-break, ISO timestamps displayed through cells, 20-row paging and existing 500-selection ceiling. Calendar due-date recalculation uses Vietnam calendar date plus current range, with tests around the UTC day boundary.

Files: frontend/openapi/tsolve-api.yaml, generated output, review/pages/ExpiredTicketsPage.tsx and its tests, feature-local expired hooks/table/mock handlers and tests, review/mocks/data.ts and state.ts for expiry metadata and SEND_BACK records. Reuse feature-local access/scope logic where practical; do not export arbitrary internals solely for tests. Register mock handlers through the existing review handlers array. No app registration edits.

Risks/tests: (1) stale off-page selection or forged UUIDs: fresh eligibility and atomic pre-validation, 404/409 recovery tests; (2) cross-Workspace/unmanaged access: negative list/ids/mutation tests, reuse live /me; (3) due dates and retained content: deterministic fixed-time boundary tests, distinct current Project ranges and snapshots of Solution/Evidence; (4) double submissions and failed refetch: disabled actions, pending guard and independent mutation completion tests; (5) selection/filter URL navigation: cross-page, all-filtered and Back/Forward tests; (6) empty/loading/error: catalog states with retry tests. Risk verdict CAUTION: mock scope checks demonstrate FE behavior, not production security or durable transactions; preserve this limitation in evidence.

Builder check: inherited detail component and API contract are available on the approved branch base. No unresolved behavior beyond the choices stated here. KAN-159 remains excluded and gets its own branch/commit. No production expiry job, email, audit persistence or SSE is claimed.

Verification: focused tests first, then full format:check, lint, typecheck, test, build; generate API twice with no additional diff. Trace STORY-007, inspect browser at desktop and narrow widths, exercise selecting/sending, detail navigation and stale/error recovery. Record task-3-expired-tickets reports. Stop at the user's feature commit checkpoint before starting KAN-159.

## KAN-159 extension: Review History (STORY-008)

Scope: complete existing ReviewHistoryPage only, using the existing PM route, shared DataTable/cells/states, generated API and feature-owned MSW store. No new dependencies, shared runtime changes, browser test requirement or backend implementation.

Chosen: reuse reviewStore.history and current scoped ticket metadata; alternatives: separate fixture authority or durable backend work; rationale: preserve existing session decisions and the approved frontend-first scope. Add stable UUID and actor display snapshot to successful decision producers, using native crypto.randomUUID and the existing getMe result. Keep the private queue decision helper restricted to its three supported mutation actions when widening the history action type. Reset retains the existing empty-history contract. UNPUBLISH/REPUBLISH are read-only fixture/query values, with no new mutation route.

Chosen: GET /api/v1/review-history contract in the spec; alternatives: client-only filtering or hand-written client; rationale: existing server pagination and generated-hook conventions. Generate DTOs/hooks from YAML. Feature-local handler validates query inputs, authenticates PM and scopes through current Workspace/managed Projects before filtering/counting. Unknown/foreign Department must never broaden access. Sort by timestamp with UUID tie-breaker, then paginate. GET must not mutate history or tickets.

Chosen: Department filter, decision select and paired date inputs with URL state; alternatives: Project filter or local-only state; rationale: existing frontend vocabulary and list conventions. Both dates absent means unrestricted; partial/invalid/reversed range shows MSG39 and disables query. Inclusive Vietnam dates become start midnight and exclusive next-day midnight. No extra future-date restriction. Back/forward must restore filters, paging and validity. Stable decision UUID is the row key; ticket UUID is only the detail link. Render user content as text; nullable author/reason/comment use shared empty-value cells.

Invalidate generated history query keys after successful existing decisions/send-back using the existing invalidation architecture, without turning a completed mutation into a failure if refetch fails. No polling/SSE extension belongs to this task.

### Edge cases and verification

Test scoped access before pagination/counting; signed-out/non-PM/foreign Department; all six labels; duplicate ticket decisions and equal timestamps; empty history/filter result; null author/content and HTML-like text; UTC+7 midnight/leap-date/invalid/partial/reversed ranges; URL navigation/page reset; server errors/retry/loading; each successful existing producer appends identity/content and invalidates history; failed decisions leave history unchanged. Reuse existing fixtures and meaningful interaction assertions, without weakening timeouts or regression checks.

Delivery: separate branch feat/KAN-159-review-history currently stacked on committed KAN-158 to reuse SEND_BACK history and KAN-24 detail. Keep the KAN-159 commit distinct; disclose this dependency before any MR. Required checks: named AC-008 tests, trace, full frontend format/lint/typecheck/test/build, deterministic regeneration, independent review and Graphify refresh. User commit checkpoint applies.

## KAN-24 production backend

Evolve the shared ticket table and its existing Solution fields. Apply UUID primary keys through a forward Liquibase migration. Backfill the import decision links before replacing the numeric ticket key. Preserve source keys, imported data and after-commit event behavior. The event carries the new UUIDs. A separate Review ticket table would split Review and Import data.

Keep the existing OpenAPI contract and Department filter. Require a trusted Workspace binding and current Project Manager membership. The import access stub cannot establish either. Validate the complete batch under locks and save state and decisions in one transaction. Jira feedback reads the committed decision under a verified issue and writer identity.

| Risk | Expected behavior | Verification |
|---|---|---|
| R13. Upgrade and import integrity | Preserve existing ticket data and decision links. New imports still commit atomically. | PostgreSQL upgrade and import tests |
| R14. Forged IDs or concurrent decisions | Restrict reads to the Workspace and managed Projects. Refuse invalid or stale batches without partial writes. | API scope, concurrency and rollback tests |
| R15. Jira feedback crosses scope | Show MSG64 only to the verified writer of the associated issue. | Jira issue, Account and Workspace scope tests |

Risk review verdict: CAUTION. Storage can be verified independently. Production activation requires trusted Workspace routing, Project membership and Jira issue identity.

### Project membership adapter (decision 2026-11-07, user)

Without a production `ReviewWorkspaceAccess` every Review endpoint answers 403. The team's tenant DDL (`database/02_tenant.sql`) decides who manages a Project: `project_membership` with `role_in_project = PROJECT_MANAGER`. The table is not in the changelog yet, so ticket-review adds it as drawn there, with UUID references because `app_user.id` and `project.public_id` are UUIDs: `user_id → app_user(id)`, `project_id → project(public_id)`, composite key, `date_added`. Identity keeps ownership of membership administration (UC-05/UC-16); this changeset only creates the table.

`ProjectMembershipReviewAccess` (in `review/service/impl/`) implements `ReviewWorkspaceAccess` against the bound tenant database: an Account that is missing or not ACTIVE gets empty (fail closed). Readable Projects are the ACTIVE Projects of the Workspace; managed ones carry a PROJECT_MANAGER membership. Each Project is keyed by both ticket-side ids in use, `public_id` and the legacy hex form of `project_id`, the same match the search module uses. The Project name is its Jira key, because `project` has no name column.

## KAN-155 extension: writer email (STORY-010)

- **Delivery** — chosen: a tenant JobRunr job per decision (`TenantJobService.enqueue("review-notification", decisionId)`), enqueued from a `@TransactionalEventListener(phase = AFTER_COMMIT)`; alternatives: direct SMTP after commit, an outbox table. Rationale: the user chose job delivery; JobRunr retries failed deliveries and SMTP never runs inside the decision transaction or the request. An outbox would also survive a crash between commit and enqueue, but it is more than the demo needs.
- **Event** — `ReviewFeedbackDecidedEvent(List<UUID> decisionIds)` follows `TicketsPublishedEvent`: published inside the transaction and consumed only after commit, so a rollback sends nothing.
- **Job payload** — only the decision id. The handler re-reads the decision, ticket and writer account inside `runInTenant`, so no email address or feedback is stored in the job table, and a decision that disappears completes without email.
- **Process split** — the handler bean exists whenever `tsolve.worker.enabled` is true, because `TenantJobServiceImpl` checks the handler type at enqueue time in the API process too. The SMTP sender is only in the `worker` profile; the handler resolves it lazily and fails the job if it is missing.
- **Disabled queue** — when the API runs without `tsolve.worker.enabled`, there is no `TenantJobService`: the listener skips enqueueing and the decision response is unchanged (AC-010.3).

## STORY-010 change: branded HTML email (2026-11-14)

- **Rendering**: chosen: one classpath HTML template, `backend/src/main/resources/mail/review-notification.html`, with `{{name}}` placeholders filled by a small `ReviewEmailRenderer` in `worker/client/`, which escapes every value with Spring's `HtmlUtils.htmlEscape` and turns the reason or comment's line breaks into `<br>`. Alternatives: Thymeleaf (a new dependency, which `backend/AGENTS.md` says needs a team decision), or HTML built in Java strings (hard to read and review). Rationale: no new dependency, and the markup stays one reviewable file. The plain-text part comes from the same `ReviewEmail` record, so the two parts cannot drift apart (AC-010.2).
- **Sending**: `ReviewEmailRenderer` renders the subject (`[<key>] …`, with `` and `
` replaced by a space, AC-010.9), the plain-text part and the HTML part. `ReviewNotificationSender.send(recipient, subject, text, html)` is generic (worker AC-001.1, AC-001.2): `MimeMessageHelper(multipart = true)`, `setText(plain, html)`, `setFrom(new InternetAddress(from, "T-Solve"))`, the `singleMailbox` checks and the `DeliveryException` wrapping (AC-010.4, AC-010.6). `Decision` lives with `ReviewEmail`. The job handler calls the renderer and then the sender. Decided in planning (KAN-162 request): with this split, the KAN-162 outbox can render at enqueue time and reuse the sender unchanged.
- **Data**: `ReviewNotification` gains `ticketTitle`, `projectName`, `reviewerName`, `writerName`, `decidedAt` (`Instant`) and `ticketId`. `findWriterNotification` joins `project` (its name is the Jira key, as for Review History) and the reviewer's `app_user` (`coalesce(full_name, email)`); the writer's name is null when none is stored, and the template then says "Hi there" (AC-010.7). The time is formatted in Asia/Ho_Chi_Minh as `d MMM yyyy, HH:mm '(GMT+7)'` with `Locale.ENGLISH`.
- **Link**: the handler reads the bound Workspace id from `WorkerTenantService.currentWorkspaceId()` and its site host (`<slug>.<TSOLVE_DOMAIN>`, the catalog's site host) through identity's Workspace lookup, then builds `https://<site host>/tickets/<ticketId>`. When there is no host (no lookup bean, an unknown Workspace, or a blank host), the email goes out without the button and the link (AC-010.8). Decided in planning: when `WorkspaceLookup` is only a port in `identity/signin/port`, it is reached through an identity `service/` interface (`WorkspaceSiteService.siteHost(UUID)`), so the worker does not depend on identity internals (backend/AGENTS.md §1).
- **Workspace name in the footer**: decided in planning: the catalog stores no display name, so the footer names the Workspace by its site host (for example `acme.tsolve.io.vn`). When there is no host, the footer leaves the name out.
- **Visual source**: the approved preview (header `#5577FF`, red badge `#FFF2F0`/`#CF1322`, amber badge `#FFFBE6`/`#AD6800`, 600 px card, table layout, inline styles, no images).

## KAN-159 extension: production Review History (STORY-011)

- **Query** — chosen: two native queries on `review_decision` joined to `ticket` and the reviewer's `app_user`, paged in the database with a count query, one per sort direction; alternatives: in-memory sort like the queue, a JPQL join to the identity `Account` entity. Rationale: history grows without bound, and a JPQL join would reach into another module's entity. The `app_user` join reads only the reviewer name, the same way KAN-24 already stores `project_manager_id`.
- **Scope** — managed Projects come from `ReviewWorkspaceAccess.readableProjects`, as for the queue; the Department filter narrows that set before the query, so a foreign or unmanaged Department yields an empty page without a query.
- **Decisions** — storage holds APPROVED, REJECTED and CHANGES_REQUESTED; the API maps them to the contract's APPROVE, REJECT and REQUEST_CHANGES. UNPUBLISH, REPUBLISH and SEND_BACK are valid filters with no stored rows yet, so they return an empty page; unknown values return MSG06.
- **Dates** — paired yyyy-MM-dd dates become `[from 00:00, to + 1 day 00:00)` in Asia/Ho_Chi_Minh, like Suggestion History; missing bounds use fixed sentinels so no nullable timestamp parameter reaches PostgreSQL.
- **Names** — actorName is the reviewer's full name, else their email; authorName is `ticket.writer`, as in the Review Queue.

## KAN-154 extension: real-time Review Queue (STORY-012)

- **Transport** — chosen: Server-Sent Events from Spring MVC `SseEmitter` on GET `/api/v1/review-queue/events`; alternatives: WebSocket/STOMP, keep polling only. Rationale: the queue only needs server → browser signals; SSE is same-origin, carries the session cookie through the Vite proxy and Caddy, and needs no new package on either side.
- **Event source** — chosen (user, 2026-11-07): a tenant trigger `ticket_notify_review_queue` (AFTER INSERT OR UPDATE OF status, project_id) that calls `pg_notify('review_queue', project_id::text)` when a Solution Form ticket enters Pending review or any ticket leaves it; alternatives: in-process Spring events after commit, Redis pub/sub. Rationale: there is no submit endpoint in the backend yet (the Solution form belongs to jira-app), so only the database sees every entry; NOTIFY is delivered at commit, never after a rollback, and PostgreSQL drops duplicate payloads in one transaction, which gives AC-012.3's one event per Project per transaction.
- **Signal, not data** — chosen: the event carries only `{"projectId": "<uuid>"}` and the browser refetches the queue it already shows; alternatives: push the changed rows. Rationale: the queue's scope, Department filter, sort and paging stay in one place (STORY-009) and no ticket content travels on the stream.
- **Listener per Workspace** — `ReviewQueueEventServiceImpl` (review module) keeps the open emitters per Workspace with the caller's managed Project ids taken at subscribe time. While a Workspace has at least one emitter, one daemon thread holds one connection from the routing `DataSource` opened inside `TenantScope.runIn(workspace)`, runs `LISTEN review_queue`, and polls `PGConnection.getNotifications(500 ms)`; the last emitter to leave closes it. Each active Workspace therefore holds one pool connection; a listener whose connection fails is reopened on the next poll cycle after 2 s.
- **Heartbeat and lifetime** — a scheduled task sends the comment `:hb` every 25 s (AC-012.1). An emitter times out after 30 minutes; the browser's EventSource reconnects by itself, which also picks up Project membership changes made meanwhile (a manager removed from a Project keeps that Project's signals at most until then — signals carry no content).
- **Frontend** — `useReviewQueueEvents()` in `features/review/hooks` opens `EventSource('/api/v1/review-queue/events', { withCredentials: true })` while the Review Queue page is mounted, invalidates the queue and ticket-ids queries on `queue-changed` and on every (re)open, and reports `connected`; `useReviewQueue` sets `refetchInterval` to `false` while connected and keeps `REVIEW_QUEUE_POLL_MS` otherwise (AC-012.5–012.7). The MSW mock serves no stream, so mock mode keeps polling.

### Edge cases

| Case | Handling |
|------|----------|
| Decision batch of 500 tickets in one Project | One NOTIFY payload after commit (PostgreSQL de-duplicates), one event per stream |
| Decision rolled back (stale batch, AC-009.7) | NOTIFY is transactional — nothing is sent |
| Ticket moved to another Project while pending | Trigger notifies the new Project (and the old one via `OLD.project_id`) |
| Caller manages no Project | `managedProjectIds` empty → 403 like the queue, no stream |
| Workspace unbound or unverified Account | 403, no listener opened |
| Listener connection dropped | Thread logs once without payload, retries after 2 s; after reopening it sends one `queue-changed` for every Project of its emitters, so changes in the gap are not lost |
| Browser tab hidden | EventSource stays open; refetch on event is cheap. Polling, when active, still pauses while hidden |

### PR #155 review fixes (2026-11-07)

- **Connect off the lock** — chosen: the subscription is registered under the lock with a `Listener` that has no connection yet; the listener thread opens the connection and runs `LISTEN` itself, and the first subscriber waits (bounded, 5 s) for "listening" before its heartbeats start; alternatives: build the Listener outside the lock and race to insert it. Rationale: a pool or database outage can no longer stall every subscribe, unsubscribe and completion callback behind the Hikari timeout.
- **Sends off the shared threads** — chosen: each subscription coalesces what it still has to send (a set of pending Project ids and a heartbeat flag) and a small shared send pool (4 threads) drains it; the listener and heartbeat threads only enqueue. A subscription whose send fails is completed and removed; one that still has a send in flight when the next heartbeat is due keeps a single pending heartbeat, so a slow client cannot grow a queue or hold another stream's thread. Alternatives: a send timeout per call (SseEmitter has none), one thread per stream.
- **Managed Projects re-checked** — chosen: with each heartbeat (25 s) the subscription re-reads `managedProjectIds` for its Account inside the Workspace; Projects no longer managed stop getting events, and a subscription left with none (or an unverified Account) is completed. Alternatives: re-check on every event (one query per stream per event), shorten the emitter timeout. Rationale: AC-012.2 ("whose caller manages that Project") holds within one heartbeat interval at one query per stream per 25 s. Removing the Project Manager role itself takes effect at the next sign-in, as for every other Review endpoint.
- **Pool use** — each Workspace with an open stream holds one tenant pool connection for its listener; the join barrier briefly uses one more. The per-Workspace pool must keep at least 2 connections above request load; the platform pool sizing (KAN platform module) owns that limit.
- **Generated client** — orval also emits `useStreamReviewQueueEvents` for the SSE operation; it must not be called (it would wait for a body that never ends). The web app uses `useReviewQueueEvents` only.

### PR #155 second review (review 5813751864, 2026-11-07)

- **Added Project (BUG-174)** — when a heartbeat re-check adds a managed Project, the stream gets one `queue-changed` for it through the coalesced send path, because its queue may have changed before it was added.
- **Mock mode (BUG-175)** — the web app opens no EventSource while it runs on MSW mocks; the generated SSE mock returns a finite body, and reconnects every ~3 s made polling flap. Mock mode stays on 5 s polling, as the design already intended.
- **Dead connection (BUG-176)** — EventSource never surfaces `:hb` comments, so a connection cut without a close is invisible. While connected, the queue keeps a 60 s safety refetch; the 5 s polling stays off (AC-012.6).
- **Subscribe failure (BUG-177)** — anything that throws after the subscription is registered (join barrier, heartbeat scheduling) ends and removes it; the request thread waits at most ~5 s in total.
- **Changeset number** — main gained `019-tracker-*` and `019-identity-*`; ours stays `019-review-queue-notify.sql` (ids differ by name) and is included last.

## KAN-156 extension: production Expired tickets and send-back (STORY-013)

Decided with the user on 2026-11-08, before the spec: SEND_BACK is stored in `review_decision`; Review History returns it; `previousStatus` and `expiredAt` are derived, not stored; the review range is `project.review_range` with a 3-day fallback.

- **Storage** — chosen: changeset `024-review-send-back-decision.sql` (author `review`) drops and re-adds `ck_review_decision` with `SEND_BACK` added; the stored-decision enum gains SEND_BACK. Alternatives: `audit_log_entry`, a new `send_back` table. Rationale: the schema reference already lists SEND_BACK in `review_decision`, and Review History reads one table.
- **Reads** — `GET /api/v1/expired-tickets` and `/ticket-ids` in `ReviewReadController` (Project Manager) resolve managed Projects through `ReviewWorkspaceAccess` as the queue does, narrow them by Department, then new `TicketReviewReadService` methods load EXPIRED Solution Form tickets of those Projects and page them in memory in the queue's comparator style (same data scale as the queue). Sort keys `expiredAt`, `sourceTicketId`, `title`, `project`; default `expiredAt,desc`, then ticket UUID. An unknown key or direction, or page/size outside the limits, is the MSG06 400 that history uses (AC-013.4).
- **Derived fields** — `previousStatus` is always PENDING_REVIEW (user decision, AC-013.3). Known limitation: KAN-157 (JOB-02, STORY-014) chose on 2026-11-08 to also expire Changes requested tickets and to store `ticket.expired_at` and `ticket.status_before_expiry` (changeset 025); once both branches are on main, a change request on STORY-013 switches these reads to the stored columns so CHANGES_REQUESTED shows correctly. `expiredAt` is 00:00 Asia/Ho_Chi_Minh on `review_due_date + 1 day`, so sorting by `expiredAt` is sorting by the due date. JOB-02 expires a ticket only because of its due date, so an EXPIRED ticket always has one; a legacy EXPIRED row with a null due date falls back to `updated_at` for `expiredAt` (sorted the same way) rather than failing the page.
- **Send-back** — `POST /api/v1/reviews/send-back` in `ReviewDecisionController` calls a new `TicketDecisionService.sendBack(...)`, one `@Transactional` method in `TicketDecisionServiceImpl`. Order mirrors the three decisions (AC-009.7): request shape 400 (empty, more than 500, duplicate, non-UUID — the existing ticket-ids request validation); lock the rows (`FOR UPDATE`); any id missing in this Workspace → 404 MSG04 with no data; any Project not managed → 403; any status other than EXPIRED or source other than SOLUTION_FORM → 409. Only then one native update sets status PENDING_REVIEW, `review_due_date = :today + range` where range is `project.review_range` when positive, else 3, and `updated_at = :now`, joined to `project` on `project_id`; then one `review_decision` row per ticket (SEND_BACK, caller, `:now`, null text). `:today` is the Vietnam calendar date of `:now` from the injected `Clock`. Solution, Evidence and feedback columns are untouched. `review_due_date` is read-only on the entity, hence the native update.
- **Queue entry and events** — the existing trigger of AC-009.11 sets `review_queue_entered_at` on the transition to PENDING_REVIEW (the transaction time), and the STORY-012 trigger NOTIFYs the Project; no code needed, the IT asserts the entry time.
- **No email** — the STORY-010 enqueue runs only for Reject and Request Changes; `sendBack` never calls it (asserted with the notifier).
- **History** — SEND_BACK moves out of the history-only filter list; stored SEND_BACK maps to SEND_BACK with null reason and comment (AC-013.8).
- **Contract and web** — the three operations keep their shapes; `tsolve-api.yaml` drops the "frontend proposal" wording and documents sorts, 404/409 and the derived fields; then `npm run generate:api`. The web hooks already call the generated `getExpiredTickets`, `getExpiredTicketIds` and `sendBackTickets` through `@/shared/api`; a page test named AC-013.9 proves the requests go through them. MSW handlers stay for mock mode.

## KAN-157 extension: JOB-02 expires overdue tickets (STORY-014)

Decisions (user, 2026-11-08): JobRunr fan-out; ticket columns plus a per-Project log line, no review decision or audit row; fixed 15-minute retry, 3 attempts per night; the Active-Workspace list is a worker change request (worker STORY-003).

- **Registration (AC-014.1)** — chosen: `ReviewExpiryRegistration` listens for `ApplicationReadyEvent` and calls `JobRunrRuntime.scheduler().scheduleRecurrently("review-job-02-expire-overdue", "0 0 * * *", ZoneId.of("Asia/Ho_Chi_Minh"), new ReviewExpiryDispatchRequest())`, only when `tsolve.worker.enabled=true`. JobRunr keys recurring jobs by id, so every process re-registering keeps one, and one BackgroundJobServer runs each occurrence. Alternatives: Spring `@Scheduled` (runs in every process); per-Workspace recurring jobs on provisioning (needs platform hooks, KAN-161).
- **Fan-out (AC-014.2)** — `ReviewExpiryDispatchHandler` (`JobRequestHandler<ReviewExpiryDispatchRequest>`, a Spring bean so the JobActivator finds it) calls `WorkerTenantService.activeWorkspaceIds()` and enqueues `ReviewExpiryJobRequest(workspaceId, 1)` for each. If the list cannot be read, the occurrence fails (JobRunr records it) and the next night catches up.
- **Per-Workspace job (AC-014.3–014.7)** — `ReviewExpiryJobHandler.run(request)`, annotated `@Job(retries = 0)` so JobRunr's exponential retry never doubles ours:
  1. If `tenants.isWorkspaceActive(ws)` is false → log that the Workspace was skipped and return: no change, no retry (AC-014.7).
  2. `today = LocalDate.now(clock.withZone(Asia/Ho_Chi_Minh))`, once per attempt.
  3. `execution.runInTenant(ws, () -> expiry.expireOverdue(today))`.
  4. On a `RuntimeException`: log the attempt number, Workspace UUID and exception class only (not its message, which may quote SQL values). If `n < 3`, `scheduler.schedule(now + 15 min, new ReviewExpiryJobRequest(ws, n + 1))`; then return normally. If scheduling the retry itself throws, rethrow so JobRunr marks the job failed.
- **Expiry (AC-014.3–014.5)** — `ReviewExpiryService.expireOverdue(LocalDate today)` in `ReviewExpiryServiceImpl`, `@Transactional`, runs one statement through `ReviewExpiryRepository` (JdbcClient): `UPDATE ticket t SET status_before_expiry = t.status, status = 'EXPIRED', expired_at = statement_timestamp() FROM project p JOIN department d ON d.department_id = p.department_id WHERE <p's UUID key> = t.project_id AND d.status <> 'DELETED' AND t.status IN ('PENDING_REVIEW', 'CHANGES_REQUESTED') AND t.review_due_date < :today RETURNING t.project_id`. The service groups the returned Project UUIDs into counts and logs one line per Project with Workspace UUID, Project UUID and count (AC-014.5). One statement is all or nothing, so no batching is needed. A concurrent decision on the same row waits for the row lock: if the decision commits first, the ticket no longer matches and is skipped; if JOB-02 commits first, the decision's status guard refuses it (409, the existing stale path).
- **Queue and stream** — the status leaves PENDING_REVIEW, so the queue and eligible ids drop the ticket, and the STORY-012 trigger sends one NOTIFY per Project (de-duplicated in the transaction). CHANGES_REQUESTED → EXPIRED is not a queue change, so no NOTIFY is sent for it.
- **Changelog (AC-014.8)** — `025-review-ticket-expiry.sql`, changeset `review:025-review-ticket-expiry`: nullable `ticket.expired_at TIMESTAMPTZ`, nullable `ticket.status_before_expiry VARCHAR(20)` with a CHECK for NULL, PENDING_REVIEW or CHANGES_REQUESTED, and a partial index on `review_due_date` for the two expirable statuses. It is included last in the master changelog. KAN-156 (STORY-013) derives the Expired list without these columns, as approved there (previous status always PENDING_REVIEW, expiry at 00:00 VN after the due date). The user chose (2026-11-08) to keep them anyway, as the only record of a Changes requested ticket's previous status and of the real expiry time; switching the Expired list to read them is a follow-up change request on STORY-013 after both branches merge. KAN-156 owns changeset 024.
- **Payloads (AC-014.8)** — `ReviewExpiryDispatchRequest()` has no fields; `ReviewExpiryJobRequest(UUID workspaceId, int attempt)` has only those. Both are records in `review/dto` implementing `JobRequest`.
- **Vietnam date** — `today` comes from the shared `Clock` bean in Asia/Ho_Chi_Minh, never `CURRENT_DATE` in the session time zone. A ticket due yesterday (VN) expires at 00:00 VN; one due today stays (GBR-10: the due date has passed).

### Edge cases

| Case | Handling |
|------|----------|
| Same batch sent twice (double click, two managers) | Row locks serialise; the second sees PENDING_REVIEW → 409, nothing written |
| One batch over two Projects with different ranges | Each ticket uses its own Project's range |
| `review_range` null, 0 or negative | 3 days |
| Send-back at 23:30 UTC (06:30 next day in Vietnam) | Due date counts from the Vietnam date; fixed-Clock test at the boundary |
| Ticket id of another Workspace | Not in this tenant database → 404, nothing exposed |
| Department with no managed Project | Empty page and empty ids, no query |
| Imported (non Solution Form) EXPIRED ticket | Not listed; send-back 409 |
| Worker down at 00:00 | The next occurrence or retry uses `< today`, so missed tickets expire then |
| Run twice the same day | Second run matches no row (AC-014.4) |
| Workspace suspended between fan-out and run | Step 1 skips it; if suspended between step 1 and `runInTenant`, runInTenant throws → failure → the 15-min retry skips it |
| `review_due_date` null (older rows) | `NULL < :today` is not true → never expired |
| Deleted Department restored later | Its overdue tickets expire on the next run after restoration |
| Other statuses (PUBLISHED, REJECTED, UNPUBLISHED, EXPIRED) | Not matched |
| DB error mid-statement | Transaction rolls back; no per-Project log; retry scheduled (AC-014.6) |
| JobRunr default retries | Off on the expiry handler (`@Job(retries = 0)`); the dispatch handler keeps the default (it only enqueues) |

### Files

`backend/src/main/resources/db/changelog/changes/025-review-ticket-expiry.sql` + master changelog; `review/dto/ReviewExpiry{Dispatch,Job}Request.java`; `review/service/ReviewExpiryService.java`; `review/service/impl/ReviewExpiry{ServiceImpl,DispatchHandler,JobHandler,Registration}.java`; `review/repository/ReviewExpiryRepository.java`. Tests: `review/ReviewExpiryIT.java` (PostgreSQL), `review/service/impl/ReviewExpiry{JobHandler,DispatchHandler,Registration}Test.java`.

## KAN-307 change: Expired list reads the stored expiry columns (STORY-013)

Decided with the user on 2026-11-10: read `ticket.expired_at` and `ticket.status_before_expiry` (written by JOB-02, changeset 025) and keep the KAN-156 derived values only where a column is null; no backfill changeset (AC-013.3, AC-013.4).

- **Entity** — `Ticket` maps both columns read-only (`insertable = false, updatable = false`): `expiredAt` as `Instant`, `statusBeforeExpiry` as `TicketStatus` (`EnumType.STRING`). Only JOB-02's native UPDATE writes them; send-back leaves them as they are (they are read only for EXPIRED tickets, and the next expiry overwrites both).
- **Read** — `findExpiredTickets` adds `t.expiredAt` and `t.statusBeforeExpiry` to `ExpiredRow`. `TicketReviewReadServiceImpl.expired` reports `expiredAt` = stored instant in Asia/Ho_Chi_Minh offset, else the KAN-156 fallback (00:00 VN on `review_due_date + 1`, else `updated_at`); `previousStatus` = stored status, else PENDING_REVIEW. Each column falls back on its own.
- **Sort** — unchanged code: the in-memory comparator already orders `ExpiredItem::expiredAt`, which is now the reported value, so stored and fallback rows sort together (AC-013.4).
- **Contract** — the `getExpiredTickets` description drops "previousStatus is always PENDING_REVIEW" and describes the stored values with the fallback; shapes unchanged; regenerate the client. The web page already shows CHANGES_REQUESTED (enum has it).
- **Tests** — `ExpiredTicketsApiIT`: a ticket expired from CHANGES_REQUESTED with a stored `expired_at` that differs from its due date reports both stored values; a null-columns ticket keeps the derived values; `sort=expiredAt` orders by the stored time across both kinds.
