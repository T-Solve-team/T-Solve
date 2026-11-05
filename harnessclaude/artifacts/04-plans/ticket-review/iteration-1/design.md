---
module: ticket-review
date: 2026-11-01
---

# Design: ticket-review — iteration 1

Master: artifacts/04-plans/ticket-review/design.md
Spec: artifacts/03-modules/ticket-review/spec.md

The user approved the master design and two-task iteration grouping. Reuse the existing queue/popups and generated operations; implement a feature-local UUID ticket/history store with complete batch validation, fresh eligibility reconciliation and mutation settlement independent of refetch completion. Task 2 adds scoped Ticket Detail and decision controls through the review barrel. Department filtering, D1/D2, the 500-id limit and safe evidence references follow the master. Master risk rows R1–R12 and its CAUTION mitigations apply. No backend, migrations, delivery workers, dependency or global CI changes.

Task 1 must pass frontend verification and its user commit checkpoint before Task 2 starts. All runtime/test files and traces remain in the module paths; shared contract changes require FE Lead review. See the master for complete data shapes, error behavior, query invalidation and test targets.

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
