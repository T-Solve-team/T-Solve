# Test report: workspace — Task 8 restore-department-screen (2026-11-15)

CI run 43648188990 (`workflow_dispatch`, `0603ae45`), all jobs success:

| Job | Result |
|-----|--------|
| frontend-checks (format:check, lint, typecheck, generate:api drift) | success |
| frontend-tests 1/4 to 4/4, frontend (build) | success |
| backend-tests unit, it-keycloak-a, it-keycloak-b, it-other; backend (`mvn verify`, spotless) | success |

KAN-122 alone (`aab8b75e`, run 43648185347): all jobs success as well.
Locally on the merge: `npm run generate:api` leaves no diff in `src/shared/api/generated`.
