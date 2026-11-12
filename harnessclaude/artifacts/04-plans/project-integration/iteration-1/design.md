---
module: project-integration
date: 2026-11-12
iteration: 1
---

# Design: project-integration, iteration 1

Spec: `artifacts/03-modules/project-integration/spec.md`
Master: `artifacts/04-plans/project-integration/design.md`
Tasks: `artifacts/04-plans/project-integration/iteration-1/tasks.md`

Iteration 1 contains all of KAN-127's current master design, UC-16 STORY-001–007. Seven Project tasks follow tracker Task 19 (`jira-project-read`, STORY-013), whose body lives in the tracker plan. D29's separate MSG48 catalog quick change precedes the screen. All deliverables are pending; this file does not approve specs or change module state.

## Architecture Overview

`ProjectController` exposes connection-test, create and list, all PM-only. Extend the existing `ProjectService` interface; preserve its Active-key lookup. `ProjectServiceImpl` checks bound Workspace and eligible own Department, normalizes and checks the link before calling tracker. Connection-test returns canonical key, statuses and prefills without writes. Create performs a fresh Jira lookup outside transactions, validates selected ids and range, then uses one `TransactionTemplate` for eligibility/link rechecks, conditional persistence, membership and audit. List returns own Department and all Active/Removed Projects sorted by key, without Jira.

The master contains all three request diagrams, the create body, exact DTO/error semantics, repository SQL, canonical-key handling and rollback policy. Reconnect keeps id, Department, tickets, resolver members and field mapping; same PM leaves membership rows unchanged, replacement deletes the previous PM row then upserts the caller. Existing JPA entity/repository remain unchanged.

The web app changes only `features/jira-integration`. Admin-only retains the placeholder; PM mounts the list/table and Add project popup. Existing route guards cover other roles. The popup uses `FormModal`'s existing primary action for Test then Save, with a shared synchronous submission guard and busy dismissal protection. Hooks use generated API operations, owned field errors and list-key invalidation. MSW has stateful create/reconnect, OPS/OLD fixtures and SUP/MISSING error paths; resets restore every fixture.

## Data Model

The master's full changeset 029 converts scalar `done_status` to JSONB singleton arrays and keeps SQL NULL; its rollback takes element zero into VARCHAR(100). Include 029 last, after current 026. No review-range CHECK, default or backfill. Project, membership and audit writes share one transaction; scope is the bound database. Internal BIGINT ids stay inside JDBC; API and audit target ids use public UUIDs.

## Key Decisions

The master's Decision/Choice/Alternatives/Rationale table incorporates D1–D43, including historical supersessions. Principal constraints for this iteration:

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| D4, D28, D38 | Tracker read service, Optional canonical key client result, typed statuses | Direct Project client; extra TrackerProject DTO | One credential/error boundary, explicit RUN 2 contract |
| D19, D20, D34 | 029 last, preserving scalar/NULL; no range CHECK | Reuse 027; constrain existing ranges | Other branches reserve 027/028; review fallback fixtures require 0/-1 |
| D21, D22 | Own Department SQL; NamedParameterJdbcTemplate | Identity changes; extend JPA entity | Exact own-scope check, conflict-safe insert, unchanged JPA lookup |
| D23, D24, D33 | Fresh ids; BigDecimal range; deduplicate exact ids | Trust tested values; Integer truncation | Reject stale selections and fractions, preserve user intent |
| D25, D36, D42 | Exact coded exceptions; @Valid first; group-sequenced key validation | Module handlers; unordered blank/length errors | Existing global contract, deterministic MSG01/MSG02 |
| D27, D41 | Protected previous PM; locked reconnect; canonical-key recheck | Demote old PM; merge aliases | At most one manager, preserved Project identity and data |
| D29, D30, D37, D40 | Separate catalog quick change; existing FormModal; busy lock | Shared kit edits; editable in-flight form | Scope ownership, one request and no stale settings |
| D39, D43 | Extend ProjectService; one shared IT configuration/container | New service; one test stack per class | Keep lookup compatibility, prove transaction/isolation efficiently |

Decided in planning: use the master's SQL and exact file plan, not the older scout proposals to expand the JPA entity or add a review-range constraint. Tracker Task 19 and Project Task 2 require their respective current specs approved before code; this planning run invokes no approval/status commands.

## File Structure

Exact paths and Create/Modify classifications live in the master and tasks. Scope excludes Project Detail, membership management screens, shared kit/app/route registrations, credential setup and external DDL edits. Generated client changes occur only through the contract generator.

## External Dependencies

None new. Tracker Task 19 supplies `TrackerProjectService`; existing audit/security/tenant ports and FE kit supply the rest. D29 and production binding/CSRF/Bearer follow-ups retain their separate owners.

## Edge Cases & Risks

All 12 dimensions apply; none skipped. The master's numbered rows 1–38 apply in full and are the authoritative test mapping. Tasks 1–7 name those row ids; tracker-specific rows 58–65 map to tracker Task 19. Critical/High coverage includes migration preservation, caller/Workspace restrictions, local conflict ordering, fresh Jira reads outside transactions, concurrent insert/reconnect, membership and audit rollback, canonical aliases, UI double submits/error retention and stateful refresh.

Risk Review: **CAUTION**, as detailed in the master: real scoped tokens versus Basic authentication; GBR-04's missing consumer; reconnect membership replacement; intentional absence of a DB range check. Backend ITs and MSW demonstrate this slice; production browser dependencies remain visible.
