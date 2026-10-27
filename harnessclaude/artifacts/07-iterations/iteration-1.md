---
iteration: 1
start: 2026-10-17
end: 2026-10-30
demo: 2026-10-30
status: planned
---

# Iteration 1

## Goal

Jira integration, server side: T-Solve receives Jira issue events from the Forge app securely, processes them in
the background, answers Simple tickets with an MSG80 suggestion, drafts Solutions for Done tickets, and serves
the Issue Panel and Solution Form API (module `tracker`, all stories).

## Planned Deliverables

| Module | Task | Feature | Story |
|--------|------|---------|-------|
| tracker | 1 | forge-remote-spike | STORY-003 |
| tracker | 2 | tracker-foundation | STORY-003 |
| tracker | 3 | fit-verification | STORY-001 |
| tracker | 4 | jira-site-registration | STORY-002 |
| tracker | 5 | token-cipher | STORY-004 |
| tracker | 6 | receive-issue-events | STORY-003 |
| tracker | 7 | jira-client | STORY-004 |
| tracker | 8 | event-worker | STORY-004 |
| tracker | 9 | triage-on-create | STORY-005 |
| tracker | 10 | draft-on-done | STORY-006 |
| tracker | 11 | issue-panel | STORY-007 |
| tracker | 12 | solution-form | STORY-008 |
| tracker | 13 | suggestion-answer | STORY-010 |
| tracker | 14 | event-cleanup | STORY-009 |

Risk: 14 tasks in the 9 days left, plus the Forge setup for `jira-app` — the user chose to keep all in
iteration 1. If it slips, Tasks 10–14 move to iteration 2 first (Tasks 1–9 give a complete demo).

## Demo Script

1. Admin registers the Jira site (Task 4, via the workspace call or a test endpoint) — show the cloudId stored.
2. Create a Simple ticket in Jira → `issue_event` row RECEIVED → PROCESSED → MSG80 comment appears (stub triage).
3. Edit a ticket's label → no row; send a forged request without a valid FIT → 401 MSG08.
4. Move a ticket to Done → AI draft requested from the ticket port (stub); panel data shows the draft.
5. Open the panel unlinked → MSG62; as reporter answer Yes → MSG82.

## Actual Outcomes
<!-- filled at end of iteration, split by module -->

## Retro Notes
<!-- what went well, what to improve -->
