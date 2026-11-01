---
module: ticket-review
date: 2026-11-01
---

# Design: ticket-review — iteration 1

Master: artifacts/04-plans/ticket-review/design.md
Spec: artifacts/03-modules/ticket-review/spec.md

The user approved the master design and two-task iteration grouping. Reuse the existing queue/popups and generated operations; implement a feature-local UUID ticket/history store with complete batch validation, fresh eligibility reconciliation and mutation settlement independent of refetch completion. Task 2 adds scoped Ticket Detail and decision controls through the review barrel. Department filtering, D1/D2, the 500-id limit and safe evidence references follow the master. Master risk rows R1–R12 and its CAUTION mitigations apply. No backend, migrations, delivery workers, dependency or global CI changes.

Task 1 must pass frontend verification and its user commit checkpoint before Task 2 starts. All runtime/test files and traces remain in the module paths; shared contract changes require FE Lead review. See the master for complete data shapes, error behavior, query invalidation and test targets.
