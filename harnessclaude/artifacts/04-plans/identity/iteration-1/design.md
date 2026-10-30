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
