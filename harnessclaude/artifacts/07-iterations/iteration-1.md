---
iteration: 1
start: 2026-10-17
end: 2026-10-30
demo: 2026-10-30
status: planned
---

# Iteration 1

## Goal

KAN-31: a Project Manager can import resolved tickets from a CSV file. The backend API and the OpenAPI contract are complete (module import-ticket-list, STORY-001..006).

The Report3 base: the module map, the web app foundation (`fe-foundation`) and the review flow (`ticket-review`),
so feature modules start on the right model.

## Planned Deliverables

### fe-foundation

| Task | Assignee | Status |
|------|----------|--------|
| 1 · Five roles, multi-role session, logout, mock users, test helpers (KAN-59) | Trần Trung Hiếu | pending |
| 2 · Guards, landing page, Workspace Setup redirect, menus (KAN-60) | Trần Trung Hiếu | pending |
| 3 · Route map with the new screens as placeholders (KAN-61) | Trần Trung Hiếu | pending |
| 4 · Report3 message catalog and triage status tags (KAN-62) | Trần Trung Hiếu | pending |
| 5 · Frontend guidelines for Report3 (KAN-63) | Trần Trung Hiếu | pending |

### import-ticket-list
- import-ticket-list Tasks 1–12 (`artifacts/04-plans/import-ticket-list/iteration-1/tasks.md`): contract, schema, parser, validator, ProjectAccess stub + ProblemDetail errors, upload, preview rows, controller + template, confirm, cleanup job, performance check, retire the old snapshot import

## Demo Script

### fe-foundation
1. Mock mode: pick "Project Manager + Knowledge Manager" in the dev user switcher → lands on Review Queue; the menu
   shows the PM items, then Wiki and Knowledge Chatbot.
2. Pick "Employee" → lands on the Knowledge Chatbot placeholder; opening `/review-queue` shows the 403 page (MSG08).
3. Pick "Admin" with the Workspace not set up → sent to 2.1 Workspace Setup.
4. Log out → Login; signing in as another user shows nothing of the first one.

### import-ticket-list
1. Swagger UI as a stub Project Manager: download the CSV template.
2. Upload a file mixing valid rows, a duplicate ID, a missing Symptoms and a too-long title → preview counts + MSG30, error rows paged.
3. Remove one valid row, then Confirm → MSG32 (created / skipped); the tickets are in `ticket` as PUBLISHED with APPROVE decisions; the Import Batch is COMPLETED.
4. Upload the same file again → those IDs now fail with MSG31.
5. Upload a Windows-1258 file → MSG27; an 11 MB file → MSG28.

## Actual Outcomes
<!-- filled at end of iteration, split by module -->

## Retro Notes
<!-- what went well, what to improve -->
