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
