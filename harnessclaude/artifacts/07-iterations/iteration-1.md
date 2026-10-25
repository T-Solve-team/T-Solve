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
| 7 · Catalog synced to the System_Messages of 25/10; 9.3 Audit Entry Detail removed (KAN-204) | Trần Trung Hiếu | pending |
| 8 · Workspace from the Workspace site, central sign-in, MSG123, background header (KAN-206) | Trần Trung Hiếu | pending |
| 9 · Route tree chosen by host (Platform Console host) (KAN-207) | Trần Trung Hiếu | pending |
| 10 · Ids are UUID strings in the /me contract (KAN-205) | Trần Trung Hiếu | pending |

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
| 5 · Ids are UUID strings (KAN-205) | Trần Trung Hiếu | pending |

### platform

Spec re-approved after X2 and U3. Tasks 2–5 also wait on Trần Hải Anh's answer about the
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

## Order — feedback first

1. fe-foundation Task 7 (KAN-204): catalog synced to System_Messages of 25/10; 9.3 Audit Entry Detail removed.
   You see: the screens on mocks show the 25/10 message texts; the Audit Entry Detail route is gone.
2. fe-foundation Task 10 + chatbot Task 5 (KAN-205), one PR: ids are UUID strings. You see: the same screens on
   mocks, with UUID ids in `/me` and the chatbot data.
3. fe-foundation Task 8 (KAN-206): workspace from the Workspace site, central sign-in, MSG123, background header.
   You see: sign-in from a Workspace site in the browser, on mocks.
4. fe-foundation Task 9 (KAN-207): route tree chosen by host. You see: the Platform Console host opens its own
   routes.
5. identity plan (design + tasks for STORY-001..003; each slice contract + mocks + screen first). You see: the
   plan, for review.
6. identity slices: My Profile (KAN-160) → Log out (KAN-106) → Login / landing (KAN-103, KAN-104); the backend
   after each screen. You see: each screen on mocks first, then on the real API.
7. platform Task 1 TenantContext and `runIn` (KAN-99); unblocks Đặng Nhật Minh's Task 8. You see: its tests.
8. platform Task 10 backend CI and images on GHCR (KAN-6, KAN-8, KAN-9). You see: CI checks on PRs and images on
   GHCR.
9. After Phase 1 (about 2026-10-28): platform Tasks 2 → 4 → 15 → 9 → 5 → 11 → 12 → 13. You see: after Task 9 the
   local stack with the two demo Workspaces in a browser; after Task 13 the staging stack.
10. Iteration 2: platform Tasks 6, 7, 14, 16.

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

## Actual Outcomes
<!-- filled at end of iteration, split by module -->

## Retro Notes
<!-- what went well, what to improve -->
