---
iteration: 1
start: 2026-10-17
end: 2026-10-30
demo: 2026-10-30
status: planned
---

# Iteration 1

## Goal

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

## Demo Script

### fe-foundation
1. Mock mode: pick "Project Manager + Knowledge Manager" in the dev user switcher → lands on Review Queue; the menu
   shows the PM items, then Wiki and Knowledge Chatbot.
2. Pick "Employee" → lands on the Knowledge Chatbot placeholder; opening `/review-queue` shows the 403 page (MSG08).
3. Pick "Admin" with the Workspace not set up → sent to 2.1 Workspace Setup.
4. Log out → Login; signing in as another user shows nothing of the first one.

## Actual Outcomes
<!-- filled at end of iteration, split by module -->

## Retro Notes
<!-- what went well, what to improve -->
