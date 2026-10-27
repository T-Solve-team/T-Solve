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
