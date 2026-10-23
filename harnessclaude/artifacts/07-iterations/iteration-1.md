---
iteration: 1
start: 2026-10-17
end: 2026-10-30
demo: 2026-10-30
status: planned
---

# Iteration 1

## Goal

The Report3 base: the module map, the web app foundation (`fe-foundation`), the backend foundation
(`be-foundation`) and the review flow (`ticket-review`), so feature modules start on the right model; the
Knowledge Chatbot screen on mocks (`chatbot`, KAN-48).

## Planned Deliverables

### fe-foundation

| Task | Assignee | Status |
|------|----------|--------|
| 1 · Five roles, multi-role session, logout, mock users, test helpers (KAN-59) | Trần Trung Hiếu | pending |
| 2 · Guards, landing page, Workspace Setup redirect, menus (KAN-60) | Trần Trung Hiếu | pending |
| 3 · Route map with the new screens as placeholders (KAN-61) | Trần Trung Hiếu | pending |
| 4 · Report3 message catalog and triage status tags (KAN-62) | Trần Trung Hiếu | pending |
| 5 · Frontend guidelines for Report3 (KAN-63) | Trần Trung Hiếu | pending |

### be-foundation

| Task | Assignee | Status |
|------|----------|--------|
| 1 · Errors as ProblemDetail with a message code (fixes BUG-001) | Anh Tran Hai | pending |
| 2 · Five roles, deny-by-default security and CSRF | Anh Tran Hai | pending |
| 3 · The session endpoint `/me` | Anh Tran Hai | pending |
| 4 · Audit events (append-only, Testcontainers) | Anh Tran Hai | pending |

### chatbot

| Task | Assignee | Status |
|------|----------|--------|
| 1 · Ask a question and show the answer, with the API contract and mocks (KAN-71) | Trần Trung Hiếu | pending |
| 2 · Citations of an answer (KAN-73) | Trần Trung Hiếu | pending |
| 3 · Session conversations (KAN-72) | Trần Trung Hiếu | pending |

## Demo Script

### fe-foundation
1. Mock mode: pick "Project Manager + Knowledge Manager" in the dev user switcher → lands on Review Queue; the menu
   shows the PM items, then Wiki and Knowledge Chatbot.
2. Pick "Employee" → lands on the Knowledge Chatbot placeholder; opening `/review-queue` shows the 403 page (MSG08).
3. Pick "Admin" with the Workspace not set up → sent to 2.1 Workspace Setup.
4. Log out → Login; signing in as another user shows nothing of the first one.

### be-foundation
1. `mvn verify` in `backend/`: every AC test passes, including the database tests on a Testcontainers PostgreSQL.
2. Run the backend; `curl /api/v1/me` with no session → 401 `application/problem+json` with `code` MSG07;
   `curl /actuator/health` → UP.
3. In `psql`, `UPDATE audit_event …` and `DELETE FROM audit_event` → refused (append-only).

### chatbot
1. Mock mode, "Employee": lands on Knowledge Chatbot with an empty "New conversation".
2. Ask "How do I connect to the VPN from home?" → answer with [1] a Wiki document link and [2] "Solution from a
   ticket"; Show expands the Solution.
3. Ask "Who won the match?" → MSG109. Ask an expense question → its Wiki citation shows MSG110.
4. New conversation, ask about leave; the list shows both, newest first; reopen the VPN one and ask a follow-up.
5. Reload → the conversations are still there. Log out and sign in again → none.

## Actual Outcomes
<!-- filled at end of iteration, split by module -->

## Retro Notes
<!-- what went well, what to improve -->

## Ongoing ticket-review work

The approved ticket-review implementation remains in-progress on its existing PR branch. Its spec, module paths, review API/UI, D1/D2 amendments and test evidence are retained; the workspace foundation planning does not reset that work. See artifacts/03-modules/ticket-review/spec.md and artifacts/05-implementation/ticket-review/task-5-fe-lead-amendments.md.
