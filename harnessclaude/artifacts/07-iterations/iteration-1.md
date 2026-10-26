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
| 6 · Placeholders only while a screen is not built (change request, KAN-71) | Trần Trung Hiếu | pending |

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

### platform

Waiting for the user to re-approve the changed spec (X2, U3). Tasks 2–5 also wait on Trần Hải Anh's answer about the
`workspaces` reshape changeset (design decision P1).

| Task | Assignee | Status |
|------|----------|--------|
| 1 · TenantContext and `runIn` (KAN-99) | Trần Trung Hiếu | pending |
| 2 · Tenant schema, base entity, convention guard (KAN-98) | Trần Trung Hiếu | pending |
| 3 · Row-level security (KAN-100) | Trần Hải Anh | pending |
| 4 · Workspace registry (KAN-98) | Trần Trung Hiếu | pending |
| 5 · Tenant Resolver of the web app and the isolation test (KAN-99) | Trần Trung Hiếu | pending |
| 8 · File storage per Workspace (KAN-96) | Đặng Nhật Minh | pending |
| 9 · Local Docker Compose (KAN-94) | Trần Trung Hiếu | pending |
| 10 · Backend CI and images on GHCR (KAN-6, KAN-8, KAN-9) | Trần Trung Hiếu | pending |
| 11 · Application VPS and the staging stack (KAN-10, KAN-11, KAN-12) | Trần Trung Hiếu | pending |
| 12 · Main Caddy, Cloudflare DNS, wildcard certificates (KAN-13, KAN-95) | Trần Trung Hiếu | pending |
| 13 · Deploy staging on merge with the smoke test (KAN-14) | Trần Trung Hiếu | pending |
| 15 · Demo seed: the two Workspaces (KAN-97, moved forward) | Trần Trung Hiếu | pending |

### search

Frontend only, on MSW mocks. Spec: `artifacts/03-modules/search/spec.md` · Design:
`artifacts/04-plans/search/iteration-1/design.md` (master: `artifacts/04-plans/search/design.md`).

| Task | Assignee | Status |
|------|----------|--------|
| 1 · Ticket List, 6.2 (KAN-147) | Trần Hải Anh | pending |
| 2 · Search Published tickets, 6.1 (KAN-145) | Trần Hải Anh | pending |
| 3 · Give Feedback popup, 8.1 (KAN-148) | Trần Hải Anh | pending |

## Demo Script

### platform
1. `mvn verify`: the tenant tests pass on Testcontainers as the application user — a native `SELECT` in Workspace A
   returns no row of B, and `ALTER TABLE … DISABLE ROW LEVEL SECURITY` is refused.
2. `docker compose up`: `curl -H 'Host: nosuch.localhost' localhost/api/v1/me` → 404 MSG119; on a seeded site
   `<site>.localhost` → 401 MSG07.
3. Merge to `main`: the `cd-staging` run shows pull, migrate, up, seed and the smoke test green;
   `https://<site>.staging.tsolve.io.vn` opens with a valid certificate (after basic auth); `https://www.staging…` → 404.
4. `infra-checks` workflow from a hosted runner: only 22/80/443 are open on the application VPS.

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

### search
1. Mock mode, "Ticket Resolver": open Ticket List → 20 tickets, Updated newest first; type "vpn" and press Enter,
   pick Department IT and status Published → the list narrows; reload → same list; sort by Title.
2. Open Search → empty results area; type "vpn" and press Enter → ranked cards with "N% helpful" (none on a ticket
   without Feedback); pick Source "Jira import" and a Resolved date range → results update; "zzz" → MSG04.
3. The popup has no screen until 5.5 Ticket Detail mounts it: `npm run test -- GiveFeedbackModal` runs its
   tests — Send with nothing → MSG01; Helpful, a comment, Send → MSG26.

## Actual Outcomes
<!-- filled at end of iteration, split by module -->

## Retro Notes
<!-- what went well, what to improve -->
