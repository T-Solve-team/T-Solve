---
name: ticket-review
repos: [default]
dependencies: [fe-foundation]
complexity: large
paths:
  - frontend/src/features/review/**
  - frontend/src/features/tickets/pages/TicketDetailPage.tsx
  - frontend/src/features/tickets/pages/TicketDetailPage.module.css
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: ticket-review

## Description

KAN-24 frontend/MSW delivery of Review Queue, Approve, Reject and Request Changes, with decision controls on Ticket Detail. Owner: Phạm Tùng Dương. This restores the module already identified in the project map; it does not import the unmerged PR #26 backend implementation.

## Responsibilities

- Screens 7.1, 7.2, 7.3 and review-related Ticket Detail 5.5 content/actions.
- Department-filtered queue, selection, UUID API proposal, mutable mock decisions/history and catalog feedback.
- Permission-aware controls and recovery from stale or rejected decisions.

## Discovery and decisions — 2026-11-01

Jira KAN-24 requests API plus screens, but the user selected a new branch from main and frontend + MSW/OpenAPI first. The user selected Department filtering under the current frontend guideline instead of the older Jira Project-filter wording. Main `2a4e5fe` has a reference queue with numeric review ids and placeholder Ticket Detail; its mock accepts partial batches and refreshes only on success. PR #26 is open, conflicts with main and has review feedback; use it as a source of candidate fixes, never as an approved implementation or dependency to merge wholesale. Graphify and source inspection confirm these gaps. SQL nodes cannot be indexed without tree_sitter_sql; no SQL change is planned.

## Interfaces

### Exposed

| Interface | Type | Description |
|-----------|------|-------------|
| Review decisions | Proposed OpenAPI + generated hooks + MSW | Queue, matching ids, approve/reject/request changes and Ticket Detail |
| Ticket decision controls | Feature barrel | Reusable review actions for Ticket Detail; tests stay in review |

### Consumed

| Dependency | Interface | Description |
|------------|-----------|-------------|
| fe-foundation | Session, routes, shared forms, UI, MSG and API errors | Existing guards, Project membership, generated client and query conventions |

Future live integration depends on canonical Ticket/KAN-137, identity/Workspace access and be-foundation audit/security. Those backend interfaces and database migrations are deferred, not replaced by fake production adapters.

## Target Repos

- default: frontend and its proposed API contract. No backend, database, CI configuration or dependency change.
- Ticket Detail is a scoped integration into the existing placeholder; keep review logic/tests within `features/review`. Shared generated contract changes require FE Lead review.

## Related Artifacts

- Spec: artifacts/03-modules/ticket-review/spec.md
- Map: artifacts/03-modules/module-map.md
