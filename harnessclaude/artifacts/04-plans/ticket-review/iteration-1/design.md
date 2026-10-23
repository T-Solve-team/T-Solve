---
module: ticket-review
date: 2026-10-20
---

# Design: ticket-review iteration 1

Spec: `artifacts/03-modules/ticket-review/spec.md`

Master: `artifacts/04-plans/ticket-review/design.md`

## Architecture Overview
This iteration contains the backend review API feature followed by the frontend integration feature. Backend uses trusted auth/Project integration ports and persisted notification handoffs; actual identity and delivery implementations remain excluded. Each feature stops for user agreement on its own code commit before the next starts.

## Key Decisions
Use the existing stack; add forward-only review tables; enforce Project scope and all-or-nothing writes; apply one shared change comment to the complete ticket selection under D1; remove the empty-Solution review case under D2; regenerate the frontend client from the updated contract. See the master design for the data model, file plan, all 12 edge-case dimensions and inline risk review.

## Edge Cases & Risks
Master risk rows 1–12 all apply. Verdict CAUTION: authorization, transaction and integration limits require the verification in tasks 1 and 2. No external integration is simulated in production.

## FE/BE review amendments — 23 October 2026

- Keep backend review code and tests in `vn.tsolve.review`, retaining table names and applied Liquibase changesets. Leave legacy ticket snapshot classes in `vn.tsolve.ticket`.
- Ticket detail checks Project scope first, then allows unpublished content only to the Project PM or author. Other readers use the Published-only service path (404 MSG04 for unpublished), retaining that consumer interface.
- All three request schemas and the internal decide port enforce max 500 before locks (400 MSG06); UI disables every action over the limit and explains the limit without truncating Select all or splitting transactions.
- A null Evidence list means no files; defensive immutable copy remains for non-null lists. Maximum five Evidence items stays constructor-enforced, with no migration edit.
- Test TicketDecisionActions within the review feature. Other modules retain ownership of their tests; retain page rendering coverage separately without review AC ids. Review Queue navigation is role-gated.
- Keep `/api/v1/tickets/{id}` as the review read contract for this PR. Legacy snapshot IDs at `/api/tickets/{id}` are distinct; ticket-repository owns eventual mapping/consolidation.
- Production auth/Project adapter and notification consumer remain excluded by the original scope. They must be connected by be-foundation roles-security/me-session, identity and workspace/Project integration, then email delivery/retry. Upstream task Jira keys are not assigned in the current plan; do not invent KAN numbers.
- Preserve main context/map and base tracking; replay this module's status/approval commands through sdd.ts.
