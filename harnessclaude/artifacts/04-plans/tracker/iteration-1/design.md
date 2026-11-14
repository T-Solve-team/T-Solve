---
module: tracker
date: 2026-10-21
iteration: 1
---

# Design: tracker — iteration 1

Master: artifacts/04-plans/tracker/design.md

Iteration 1 implements the whole module (all 14 tasks, the user's choice), so every section of the master
design applies unchanged: architecture, data model, key decisions, file structure, edge cases, risk review and
builder check. Read the master; this file only marks the scope.

Scope: STORY-001 … STORY-010 (Tasks 1–14 in `tasks.md`), and STORY-011 (Task 15, KAN-22 suggestion job, added
2026-11-07 — master design "STORY-011: Suggestion job").

Scope addition: STORY-013 (Task 19, `jira-project-read`, KAN-127, added 2026-11-12); master section "STORY-013: Read a Jira project for the Project module" defines the read-only service and client contract.
