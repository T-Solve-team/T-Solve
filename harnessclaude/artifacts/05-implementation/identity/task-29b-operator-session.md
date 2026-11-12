# Task 29b: Operator session on `admin.` — KAN-313

## Session

### Hand-off
- done: Task 29b (KAN-313): callback allow-list (`OperatorAllowList`), `OperatorPrincipal`, Operator session on `admin.`; Console rule covers every request (not only `/api/**`), so the Operator role is refused on all Console controllers without a Role check; percent-encoded and path-normalisation bypasses are refused by the firewall (400) and the Console rule (403 MSG08); only the exact `POST /api/v1/auth/logout` runs. Review attempt 3: pass (approve), head 75281b55. Targeted tests 148/148 in 7 classes; CI on this head green (`gh pr checks 212`).
- done: Task 29b (KAN-313) approved at review attempt 4 (verdict pass) on head fa3c7a5f, merge of main with KAN-314 (#214) reviewed. Operator session on `admin.`: callback allow-list (`OperatorAllowList`), `OperatorPrincipal`, Console rule covers every request, percent-encoded and path-normalisation bypasses refused (400 firewall, 403 MSG08), only the exact `POST /api/v1/auth/logout` runs. Decision A4-2: keep the LOGOUT for a Workspace session ended by a Console callback (Hiếu, 2026-11-13). CI on fa3c7a5f green. Targeted tests 108/108.
- open: Minors A4-1 (`OperatorSignInTest:359` has no "no SessionEnded" check), A3-2 (no test row for the exact logout match or a non-API refusal), attempt-1 finding 7 (test tidy-up); Console process profile (AC-001.61); Task 28.
- next: merge PR #212; then the Console process profile (AC-001.61) and Task 28.
- branch: feat/KAN-233-identity-operator-session
- PR: #212 ready to merge

### Review rounds
- Attempt 1: Critical 1 — Operator on four Console controllers without a Role check; fixed by the Console rule. Further findings (Important 2, Minor 4) listed in the review file; attempt-1 findings 4 closed.
- Attempt 2: Critical 1 — percent-encoded path bypass; fixed. Attempt-2 issue list in the review file; the full text is in git (`75281b55`).
- Attempt 3: pass (approve). New: 0 Critical, 0 Important, 2 Minor (A3-1, A3-2). Open from attempt 1: finding 7 (Minor). Probe (scratch copy, MockMvc): double encoding, `;` parameters, `//`, `..` and `.` segments, `%2f`, `%00` give 400 from the firewall; trailing slash, encoded letters, wrong method, `X-HTTP-Method-Override` and `_method` give 403 MSG08; only the exact `POST /api/v1/auth/logout` runs. New log lines are constant text, no email or token.
- Attempt 4: pass (approve) on head fa3c7a5f. New: 0 Critical, 0 Important, 2 Minor (A4-1, A4-2). Decision A4-2: keep the LOGOUT for a Workspace session ended by a Console callback (Hiếu, 2026-11-13).
- Review: `artifacts/06-evaluation/identity/task-29b-operator-session-review.md`; test report: `artifacts/06-evaluation/identity/task-29b-operator-session-test-report.md`.
