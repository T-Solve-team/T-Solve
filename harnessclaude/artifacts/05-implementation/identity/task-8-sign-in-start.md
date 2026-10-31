# Task 8: Sign-in start, authorize and state values — KAN-230

**Module:** identity
**Feature:** sign-in-start
**Status:** passing
**Iteration:** 1

## Session 2026-10-31

### Hand-off
- done: state values, sign-in start (Workspace and `admin.`), authorize on `login.`, the minimal login page, two security chains; AC-001.1–.6, .28, .39 and edges 19, 20, 38, 43, 44 tested; review approved at attempt 1
- open: login-CSRF cookie (spec change before Task 9), Workspace with no Google domain, identity `Clock` bean (all on the Task 9 list); `sdd.ts trace` cannot see Java tests (TEST_GLOBS, Hiếu to fix); Workspace sign-in answers 404 until platform's lookup (KAN-98 / KAN-99) is on `main`
- next: Task 9 (KAN-231) after the login-CSRF spec change
- branch: `feat/KAN-230-identity-sign-in-start`
- PR: #90
