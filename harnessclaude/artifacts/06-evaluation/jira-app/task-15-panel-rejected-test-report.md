# Test report: jira-app — Task 15 panel-rejected (2026-11-15)

Run on `main` `8cfeec0e` (and first on `72dcf49d`, same result). Command (jira-app/TSolve_JiraApp/): `npm test` (Jest) — 12 suites, 156 tests, all passed; `npm run lint` — exit 0.

Backend side used by the panel (backend/): `TSOLVE_WORKER_ENABLED=false mvn -B verify -Dit.test=ReviewDecisionApiIT -Dtest=JiraReviewFeedbackControllerTest,JiraAppSolutionServiceImplTest` — 4 + 19 + 24 tests, 0 failures, `spotless:check` included, exit 0. (Without the override the IT context fails on the local `.env`, which sets `TSOLVE_WORKER_ENABLED=true`; see ticket-review Task 20 test report.)
