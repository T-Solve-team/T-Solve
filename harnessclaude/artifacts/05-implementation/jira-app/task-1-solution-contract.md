# Task 1: Contract

**Module:** jira-app
**Assignee:** BinhBinh13
**Status:** passing
**Iteration:** 2

> Written on 2026-11-13 from the commit and its message, after the PR review of KAN-126 found the log missing. It records what the commit holds; reasoning not in the commit message is not reconstructed.

---

## Session 2026-11-11

### Working on: solution-contract (KAN-126, branch `feat/KAN-126-jira-app-solution-form-v2`)
- STORY-001 to STORY-009.
- Seven Jira App paths in `frontend/openapi/tsolve-api.yaml` (solution form, submit, draft, AI rewrite, panel, ticket search, ticket preview), their schemas, and the client regenerated with `npm run generate:api` (`src/shared/api/generated/jira-app/*`, `model/*`).
- `TSOLVE_STUB_AI` and `TSOLVE_WEB_ORIGIN` added to `.env.example`.
- Commit: `default@f16c8a02`

### Blockers
none

### Next up
Independent review: `06-evaluation/jira-app/task-1-solution-contract-review.md`.

## Session 2026-11-13

### Working on: fixes for review attempt 1 (fail)
- The contract (`frontend/openapi/tsolve-api.yaml`) and the controllers' `@ApiResponse` now list every error the endpoints return: 403 MSG08 of the issue access on solution-form and panel, 503 MSG06 of the Forge token check, MSG62/MSG08 for search and preview, MSG31/MSG40 for the submit 409, 400 on draft, rewrite and preview. Client regenerated.
- BUG-205: `JiraAppTicketServiceImpl` refuses to start unless `TSOLVE_WEB_ORIGIN` is an absolute http(s) origin; the test config sets `https://web.example.test`, `.env.example` gives `http://localhost:5173`.
- BUG-204: the Forge client counts only a 409 MSG31 after a retry as submitted; a 409 MSG40 is given back.
- Commit: `default@ccfee5d6`

### Blockers
none

### Next up
Review attempt 2 passed; see `06-evaluation/jira-app/*-review-2.md`.
