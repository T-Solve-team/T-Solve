# System test round 2 – role plan with the existing staging accounts

Staging site `https://bluefin.tsolve.io.vn`. Only these four Google accounts exist (all `@bluefin-demo.io.vn`), and no
new account may be created (team decision, 21/11/2026). The test cases need seven roles (ENV-04 of
`demo-mvp-testcases-web.md`), so three roles are played by an existing account. This file says which account plays
which role, and which Admin or Project change comes before it and is reverted after.

## Accounts on 21/11/2026

| Account | Department | Roles | Project membership |
|---|---|---|---|
| `admin@` | HR | Employee, Admin, Project Manager (PM role added on 16/11 to take over SAM1) | SAM1 (HR) as Project Manager |
| `project-manager@` | IT Support | Employee, Project Manager | DP and KAN (both IT Support) as Project Manager |
| `knowledge-manager-1@` | IT Support | Employee, Knowledge Manager (Spaces HR, IT Support) | none |
| `ticket-resolver@` | IT Support | Employee, Ticket Resolver | DP and KAN as Member; Jira user "Duong pham tung" linked (21/11) |

The 16/11 run report wrote `knowledge-manager@`; the real account is `knowledge-manager-1@`.

## Why these choices (from the code on `main` 36d69bf)

- Ticket scope (`identity/repository/ScopeAccessRepository`): a user sees the tickets of their own Department only if
  they hold the Project Manager role, plus the Departments of the Projects where they are a counted member (Project
  Manager with the PM role, or Member with the Ticket Resolver role). The Admin role adds no scope.
- Menus (`frontend/src/app/layout/menu-config.ts`): Ticket Resolver has Search and Ticket List only. It has no Knowledge
  Dashboard, User List or Audit Log, so it is a valid "not allowed" account for W-1.11 and W-3.17.
- A Resolver B must be a different person from Resolver A, who writes T1 and T2 (W-2.07, W-2.13 and W-2.14 check a
  reader who is not the writer).

## Role map

| Test role | Played by | Change before | Revert after | Why it is a fair stand-in |
|---|---|---|---|---|
| Admin | `admin@` | none | – | Real role |
| Project Manager | `project-manager@` | none | – | Real role, PM of DP |
| Knowledge Manager | `knowledge-manager-1@` | none | – | Real role, manages Spaces HR and IT Support |
| Resolver A (writer of T1, T2) | `ticket-resolver@` | none | – | Real Ticket Resolver, Member of DP |
| **Employee IT** | `ticket-resolver@` | none | – | Department IT Support. The Ticket Resolver role adds only Search and Ticket List. Chatbot, Wiki, Create in Jira and the blocked pages behave as for an Employee of IT Support |
| **Employee HR** | `admin@` | none | – | Department HR. Its only Project (SAM1) is in HR, so no IT Support ticket or IT-only document is in its scope. Limitation: Admin and PM menus are visible. Note this in the test result |
| **Resolver B** | `knowledge-manager-1@` | (1) Admin, User Detail of `knowledge-manager-1@`: add the role Ticket Resolver (keep Knowledge Manager and Department IT Support). (2) `project-manager@`, Jira Integration → Project DP → Add member: `knowledge-manager-1@` | (1) `project-manager@`: remove `knowledge-manager-1@` from DP. (2) Admin: remove the Ticket Resolver role. Check the Audit Log shows both changes | Second Ticket Resolver of DP in IT Support. Not a PM, so it is also the account for W-2.03 |

Do the Resolver B change once, before Scenario 2 step 7 (W-2.07), and revert it after W-2.14. Between those cases,
`knowledge-manager-1@` acts as Resolver B only, not as Knowledge Manager. The Knowledge Manager cases (W-1.23,
W-1.24) run before the change.

## Blocked / Pending cases that need one of these roles

From `demo-mvp-testcases-web.md` and the 5.3 sheet (Round 1 = Pending or Blocked).

| Case | Role in the case | Account | Change before | Other precondition |
|---|---|---|---|---|
| W-1.11 | Employee IT | `ticket-resolver@` | none | – |
| W-1.25 | Employee HR | `admin@` | none | W-1.24 (documents 1 and 2 uploaded) |
| W-1.26 | Employee IT | `ticket-resolver@` | none | W-1.24 |
| W-1.27 | Resolver B | `ticket-resolver@` is enough (search only; Resolver B is not needed before T2 exists) | none | BUG-323 fixed; W-1.24 |
| W-2.03 | Resolver B (not PM) | `knowledge-manager-1@` | Resolver B change | – (or `ticket-resolver@`, which is also not a PM) |
| W-2.07 | Resolver B | `knowledge-manager-1@` | Resolver B change | T2 seeded, not yet approved |
| W-2.10 | Resolver B | `knowledge-manager-1@` | Resolver B change | W-2.08 (T2 approved) and indexing |
| W-2.11 | Resolver B | `knowledge-manager-1@` | Resolver B change | W-2.08 |
| W-2.12 | Employee HR | `admin@` | none | W-2.08 |
| W-2.13 | Resolver B | `knowledge-manager-1@` | Resolver B change | W-2.08 |
| W-2.14 | Resolver B | `knowledge-manager-1@` | Resolver B change; revert after this case | W-2.08 |
| W-3.01 to W-3.08 | Employee IT | `ticket-resolver@` | none | Chatbot data (W-1.24, CSV tickets) |
| W-3.09 | Employee HR | `admin@` | none | Document 2 (IT only) uploaded |
| W-3.10 | Employee IT | `ticket-resolver@` | none | Document 2 uploaded |
| W-3.11 to W-3.14 | Employee IT | `ticket-resolver@` | none | Default Project of IT Support (W-1.15 is blocked: no screen sets it) |
| W-3.17 | Employee IT | `ticket-resolver@` | none | – |
| E2E-03 | all | all four | as above | – |

Cases that need no stand-in but are still blocked for another reason: W-1.02 (needs an email that was not added: use
any Google account outside `bluefin-demo.io.vn` the user already has, signing in only to see the refusal), W-1.03
(needs a second workspace), W-1.04 and W-1.05 (need the Jira token to be typed again), W-1.15 (no default-Project screen),
W-2.01 to W-2.09 (need the T1 and T2 seed, see below), W-2.02 (needs a third Pending-review ticket while the queue is open),
W-2.06 (needs the inbox of `ticket-resolver@`).

## Order of a round-2 run

1. Admin (`admin@`): W-1.01 … W-1.10 already passed. Check Audit Log before (baseline).
2. KM (`knowledge-manager-1@`): W-1.23, W-1.24.
3. Employee checks: W-1.11, W-1.26, W-3.17 (`ticket-resolver@`); W-1.25 (`admin@`).
4. Seed T1 and T2 (DATA-03) as Resolver A (`ticket-resolver@`). Done on 21/11/2026, see "Seed data created" below.
5. PM (`project-manager@`): W-2.01, W-2.04, W-2.05.
6. Resolver B change (Admin, then PM). Resolver B (`knowledge-manager-1@`): W-2.03, W-2.07.
7. PM: W-2.08, W-2.09.
8. Resolver B: W-2.10, W-2.11, W-2.13, W-2.14. Employee HR (`admin@`): W-2.12.
9. Revert the Resolver B change (PM removes the member, Admin removes the role).
10. Chatbot as Employee IT / HR: W-3.01 … W-3.14.
11. PM and Admin: W-3.15, W-3.16, W-3.18, W-3.19 (W-3.19 with `project-manager@`, who is not an Admin).

## Seed data created on 21/11/2026 (through the app UI)

The web app has no screen that creates a Pending-review ticket: only the Solution Form of the T-Solve Jira app does
(`TicketRepository` inserts `SOLUTION_FORM` / `PENDING_REVIEW`). So DATA-03 was made in Jira, on the demo site
`bdang798.atlassian.net`, with the user's approval at each step.

| Step | What was done |
|---|---|
| Account link (UC-59) | Jira user "Duong pham tung" linked to the T-Solve account `ticket-resolver@` (T-Solve account page in Jira → Sign in to T-Solve → `bluefin.tsolve.io.vn/jira-link` → Link). Unlink it on the same page after the test round if needed |
| T1 (for Request changes) | Jira issue **DP-117** "Spike: VPN không kết nối được": To Do → Done, Solution Form → Finish. Symptoms: the VPN client says "Authentication failed" after a domain password change. Root cause: the old password is still saved in the connection profile. Staging ticket `89a6d402-e644-431b-92ce-ddb62bd70ece`, Project DP, Pending review, writer Ticket Resolver, entered the queue 21/11/2026 23:18 |
| T2 (for Approve) | Jira issue **DP-109** "Login báo lỗi, không vào được để xem đơn hàng": To Do → Done, Solution Form → Finish. Symptoms: "Tài khoản đang bị khóa" (error code ACC-LOCK-403) after 5 wrong passwords. Staging ticket `510cd232-7dc8-445a-a75f-fd7e6b9e46d5`, Project DP, Pending review, writer Ticket Resolver, entered the queue 21/11/2026 23:19 |
| Check | `project-manager@` Review Queue lists DP-109 and DP-117, review due date 24/11/2026 |

DATA-04 question for W-2.10 (wording different from T2): "Khách không đăng nhập được vì nhập sai mật khẩu quá nhiều lần".
For W-2.11 (exact keyword): "ACC-LOCK-403".

Also created for the BUG-323 re-test (W-1.18): CSV import `r2-import.csv` into DP as `project-manager@` (21/11/2026 23:08):
R2-001 "Wi-Fi văn phòng rớt kết nối liên tục" and R2-002 "Không mở được file Excel trên ổ chia sẻ" Published; R2-003
rejected as the error row. Import History batch 7: 2 published, 1 error, Completed.

Staging state seen on 21/11/2026 that differs from the 16/11 run report: a second Project **KAN** (IT Support,
`project-manager@` is PM, `ticket-resolver@` is Member); about 314 tickets already in the Ticket List of the PM (CSV
sample data); Project SAM1 (HR, PM `admin@`) has two Solution Form tickets ("[Sample 012]" Pending review, "[Sample 073]"
Changes requested); an older REJECTED ticket with the T1 title (from DP-118).

## Round 2: what changed from this plan (22/11/2026)

- Staging had a fifth workspace user, `knowledge-manager@` (IT Support, Knowledge Manager of HR and IT Support, no Google
  account), and `knowledge-manager-1@` had no Department and managed only "QA Test Depttt". With the user's approval the
  Admin moved Space IT Support to `knowledge-manager-1@` and gave it Department IT Support for W-1.23 / W-1.24, then moved
  the Space back and cleared the Department (a Knowledge Manager must keep at least one Space, so HR stayed with
  `knowledge-manager@`).
- Resolver B could not be `knowledge-manager-1@`: Project Detail > Add member now lists only Jira users linked to a
  T-Solve account, and the only linked Jira user ("Duong pham tung") belongs to `ticket-resolver@`. W-2.07, W-2.10,
  W-2.11, W-2.13 and W-2.14 ran as `ticket-resolver@`; the Ticket Resolver role added to `knowledge-manager-1@` was removed.
- IT Support's Default Project was set to DP in W-1.15 (it was None) and is kept for W-3.13; set it back to None after
  the chatbot cases.
- Extra data created: Jira DP-123 moved to Done with a Solution Form (W-2.02, now Pending review in the queue); T1 is
  Changes requested and T2 Published after W-2.05 / W-2.08; Wiki folder IT Support / ST2 Network with
  ST2-VPN-guide-company.pdf (Company-wide) and ST2-server-room-IT-only.pdf (Department); a Jira import draft of DP
  (3 rows, 22/11/2026 06:48).
- Gmail is not enabled for the `bluefin-demo.io.vn` Google Workspace, so W-2.06 (review email) cannot be checked.
- The staging AI server answered 503 MSG06 to every chatbot ask during the run (BUG-335); the chatbot cases and the
  golden set (`chatbot-golden-set.md`) wait for it.
