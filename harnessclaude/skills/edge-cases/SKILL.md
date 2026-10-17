---
name: edge-cases
description: Expand a module's stories into edge cases and test targets across 12 dimensions
type: on-demand
phases: [plan, evaluate]
agent: planner
---

# Edge Cases

Turn acceptance criteria into a concrete list of edge cases, failure modes, and test targets **before** tasks are written. Used by the plan skill; the reviewer can reuse the table in evaluate.

Skip for trivial modules (static content, config-only changes).

## Process

1. Read `artifacts/03-modules/{module}/spec.md` (stories, acceptance criteria, interface contracts) and `module.md`.
2. Mark which of the 12 dimensions apply; list the skipped ones with a reason.
3. For each applicable dimension, write 2–5 concrete scenarios: a specific trigger and the expected behavior.
4. Rate severity.
5. Write the table into the **Edge Cases & Risks** section of `design.md` (active plan path).
6. For every Critical/High row, make sure `tasks.md` has a test task that covers it (reference the row number).
7. If a scenario reveals a gap or ambiguity **in the spec** (expected behavior unknown), do not invent the answer — list it and ask the user. If they decide, the spec owner updates `spec.md` and its Changelog.

## The 12 Dimensions

| # | Dimension | Look for |
|---|-----------|----------|
| 1 | User types | admin, guest, banned, new, power user, bot |
| 2 | Input extremes | empty, null, max length, unicode, special characters, injection strings |
| 3 | Timing | concurrent requests, double submit, timeouts, retries, slow network |
| 4 | Scale | 0, 1, many, pagination boundaries, very large payloads |
| 5 | State transitions | first use, abort mid-flow, resume after crash, partial completion |
| 6 | Environment | mobile/low-end, no JS, screen reader, timezone/locale, offline |
| 7 | Error cascades | DB down, dependency timeout, disk full, partial write |
| 8 | Authorization | expired token, wrong role, shared link, CSRF, privilege escalation |
| 9 | Data integrity | duplicates, orphans, encoding mismatch, concurrent migrations |
| 10 | Integration | webhook replay, API version mismatch, third-party outage, contract drift |
| 11 | Compliance | deletion requests, audit-log gaps, retention, PII leaking into logs |
| 12 | Business logic | zero/negative amounts, stacking discounts, refunds, quota limits |

## Severity

| Level | Meaning |
|-------|---------|
| Critical | Data loss, security breach, auth bypass, silent corruption |
| High | Broken for a subset of users, inconsistent data |
| Medium | Degraded experience, error not surfaced to the user |
| Low | Cosmetic, non-blocking warning |

## Output (in design.md)

```markdown
## Edge Cases & Risks

Dimensions analyzed: {list} · Skipped: {list — reason}

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-003 | Input extremes | Empty display name | High | 400 with field error | T-07 |
| 2 | STORY-005 | Authorization | Expired JWT on /orders | Critical | 401, session cleared | T-09 |

Open questions for the spec owner: {list or "none"}
```
