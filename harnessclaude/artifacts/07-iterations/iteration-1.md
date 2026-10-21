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

## Planned Deliverables
- import-ticket-list Tasks 1–12 (`artifacts/04-plans/import-ticket-list/iteration-1/tasks.md`): contract, schema, parser, validator, ProjectAccess stub + ProblemDetail errors, upload, preview rows, controller + template, confirm, cleanup job, performance check, retire the old snapshot import

## Demo Script
1. Swagger UI as a stub Project Manager: download the CSV template.
2. Upload a file mixing valid rows, a duplicate ID, a missing Symptoms and a too-long title → preview counts + MSG30, error rows paged.
3. Remove one valid row, then Confirm → MSG32 (created / skipped); the tickets are in `ticket` as PUBLISHED with APPROVE decisions; the Import Batch is COMPLETED.
4. Upload the same file again → those IDs now fail with MSG31.
5. Upload a Windows-1258 file → MSG27; an 11 MB file → MSG28.

## Actual Outcomes
<!-- filled at end of iteration, split by module -->

## Retro Notes
<!-- what went well, what to improve -->
