# Test report: ticket-review — Task 20 jira-rejected-feedback (2026-11-15)

Run on `main` `8cfeec0e` (and first on `72dcf49d`, same result). Command (backend/): `TSOLVE_WORKER_ENABLED=false mvn -B verify -Dit.test=ReviewDecisionApiIT -Dtest=JiraReviewFeedbackControllerTest,JiraAppSolutionServiceImplTest` — includes `spotless:check`. Exit 0.

| Suite | Tests | Failures | Errors |
|-------|-------|----------|--------|
| JiraReviewFeedbackControllerTest | 4 | 0 | 0 |
| JiraAppSolutionServiceImplTest | 19 | 0 | 0 |
| ReviewDecisionApiIT | 24 | 0 | 0 |

`frontend`: `npm run generate:api` leaves no diff in `src/shared/api/generated`.

Environment note: the first run without the override failed to load the IT context (24 errors, no bean
`WorkerTenantService`) because `application.yml` imports the local `../.env`, which sets `TSOLVE_WORKER_ENABLED=true`
for dev-site testing. Not caused by this change: `.env.example` and CI keep it `false`.
