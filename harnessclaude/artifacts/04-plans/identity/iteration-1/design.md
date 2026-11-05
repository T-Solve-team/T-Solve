---
module: identity
date: 2026-10-25
---

# Design: identity — current frontend delivery

Master: artifacts/04-plans/identity/design.md
Spec: artifacts/03-modules/identity/spec.md

## Architecture Overview

Deliver KAN-107–110 sequentially using the existing Admin routes, shared UI kit, generated API hooks and MSW data. This file groups the requested frontend work under the workspace's current iteration; Jira's S2 labels and dates are unchanged.

## Data Model

Use the master plan's user list, detail, mutation, lookup and handover contract areas. Keep actual endpoint/schema additions with the story that consumes them. No production backend changes.

## Key Decisions

Use the approved MSW approach and existing project stack. One issue branch and user-approved feature commit per task, followed by its harness commit. The hook approval command must be run by the user before code can start.

## File Structure

See the master's feature-local outline and the exact paths in tasks.md. Do not pre-create unused components.

## External Dependencies

No new dependencies.

## Edge Cases & Risks

The master plan's numbered risk table applies in full. Tasks 1–4 explicitly map its High-severity cases to verification. Risk verdict: CAUTION, with atomic mock mutations, rejected-operation tests, generated contracts and clearly scoped backend limitations as mitigations.

## Task 3 integration note
User approved the minimal central mock-session adapter for KAN-109/110, requiring FE Lead review. Follow the master's approved bridge note; retain unchanged session fixtures and keep the adapter implementation feature-local. No other shared/app edit is authorized by this note.

## KAN-111 preparation — draft

Master: artifacts/04-plans/identity/design.md, "KAN-111 preparation — 2026-10-29".
Spec: artifacts/03-modules/identity/spec.md, STORY-008 / AC-008.1–008.9.

The user selected preparing the scope/spec now and waiting for KAN-109/110 to merge into main before code. This is preparation in the current plan folder, not a change to Jira sprint dates. Reuse the included Project handover and its existing popup/mutation contracts; no standalone screen or endpoint. Reconcile the merged code against the master's risk table, implement only gaps, and retain existing Space behavior. Approval of the new spec and merged KAN-109 permit the first caller slice (user continuation request, 2026-10-30); the KAN-110 caller and final completion wait for KAN-110 on main. Full frontend verification, browser inspection and a user commit checkpoint apply; KAN-112 remains separate.

## KAN-112 — Task 22: Space handover

Master: artifacts/04-plans/identity/design.md, "KAN-112 — Space handover — 2026-10-31".
Spec: artifacts/03-modules/identity/spec.md, STORY-009 / AC-009.1–AC-009.9.

User approved the master design and placement in iteration 1 on 2026-10-31 after approving the spec. Reuse UC-06/07 caller flows, the shared handover popup, existing generated models and atomic mutable MSW helpers. Expand the existing OpenAPI proposal's descriptions; no new endpoint, model, dependency or backend implementation. Exercise master risk rows S1–S8: eligibility and stale choices, exactly the affected Space set, multi-Space/mixed-Project atomicity, cancellation/failure/retry, loading/refetch/pending controls and retained history. Task 22 owns implementation, browser evidence, trace and the required frontend checks. Runtime changes require a demonstrated gap; commit and push remain separate user checkpoints.

## STORY-003 My Profile — KAN-160 (2026-10-31)

Master: artifacts/04-plans/identity/design.md, "STORY-003: My Profile — KAN-160 plan, 2026-10-31".
Spec: artifacts/03-modules/identity/spec.md, STORY-003 / AC-003.1–003.14 and AC-002.7.

Tasks 15–18: the web app on MSW mocks (D1), stacked on `chore/KAN-231-identity-plan` (PR #94). Two new endpoints
under tag `profile` (`getMyViewableSpaces`, `getMySolutions`), each in its screen's PR; feature-local mocks read the
dev user with `getDevUser()`. No shared edit: in My Solutions only the Ticket ID of a `canOpen` row is a link
(`cells.link`, R1 A). Edge-case rows 53–81 of the master apply. Task 19 (backend
AC-003.4–.10) is a stub with no feature until it has a Jira issue.

## HLA change — Tasks 9b, 10, 11, 12, 14 (2026-11-03)

Master: artifacts/04-plans/identity/design.md, "HLA change, 2026-11-03" and "Data model (Q8, HLA Q2–Q4)".
Spec: artifacts/03-modules/identity/spec.md, Changelog row 2026-11-03.

Aligns identity's backend plan with the multi-tenant HLA (KAN-231 step 2, Hiếu: Q1–Q4 = A). The Platform Console on
`admin.` stays (Task 11). Sign-in states and hand-off codes move to the Platform / Tenant Registry database in Task 14;
Tasks 8–9 keep their tables until then. Sessions live in Redis (Task 10; local stack and tests use a Redis container).
One database per company: the account tables have no `workspace_id` and email uniqueness across the platform goes
through the registry (Tasks 9b and 14). Task 9 is unchanged. New edge-case rows 90–93 apply to Tasks 9b, 10 and 14.

## KAN-107 UUID read integration — 2026-11-05
Chosen: consume current platform UUID contracts; remove the branch-only SQL catalog/router/bootstrap instead of maintaining a second tenant system. User chose main UUID architecture for PR119. `TenantContext` is a UUID port owned by platform; implementation is not supplied by this feature. The identity read repository uses the primary JdbcTemplate, a read-only repeatable-read TransactionTemplate and a guard check (`tenant_guard_ok()` plus tenant_identity equality) before account queries. Platform owns connection routing and setting app.tenant_id; this slice never guesses a tenant or sets it from request data. Missing infrastructure fails closed.
Shared TSolveUser identifies the Account. Trusted issuer supplies serializable WorkspaceSessionBinding(UUID) as Authentication.details; the live roles filter preserves it while restoring the original SecurityContext afterward. CORS and security errors reuse main SecurityProblemWriter. DTO/query/filter ids become UUID; the temporary numeric mock boundary and SqlId API type disappear. SQL schema creation/provisioning and real session issuance are deferred dependencies; no applied migration changes.
Risk review — CAUTION: Architect: avoid duplicate platform ownership; Security: guard and trusted binding fail closed before business SQL; Performance: bounded count/page/roles queries in one response snapshot; User: retain UUID navigation and existing loading/error states; Devil's advocate: keep feature disabled until prerequisites actually exist. Mitigations: wrong/missing setting, foreign principal, stale role and cross-database regression tests; no infrastructure fallback, no activation/production-ready claim.
