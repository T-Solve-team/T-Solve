# Task 2: Add user account — KAN-108

**Module:** identity
**Assignee:** Phạm Tùng Dương
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-26

### Working on: add-user
- Spec: artifacts/03-modules/identity/spec.md, STORY-005 / AC-005.1–8. The user chose MSW and authorized a separate issue branch, commit and push for each task.
- Restored the saved KAN-108 work after KAN-107 review repair. Branch includes final KAN-107 de19ed9 and current main f22515d as dependency history. The retained stash is a backup.
- Implemented creation mode of User Detail, generated POST/create-options hooks, company-email/role/Department/Space validation, mutable Workspace-scoped fixtures, atomic creation and query invalidation with MSG12.
- UserAccount ids are UUIDs, including newly created accounts. Shared DepartmentRef and SpaceRef use their existing contracts. Employee is always retained; Project Manager needs Department; Knowledge Manager needs at least one Space with uniqueness enforced by the mock.
- Reused AppForm, FormField, shared messages and existing registered routes. No new dependency or shared/app/session implementation change. Existing-user editing remains for KAN-109/110.
- BUG-013: double submit raced the rendered pending state; synchronous ref guard now blocks repeat completion. Regression reproduced two POSTs before and one after.
- BUG-014: default required rule expected a string and rejected a selected numeric Department. Feature-local number validation fixes it; regression covers successful PM creation.
- Parent cleanup uses generated Role/UserAccountStatus constants and typed lookup/creation responses. Generated files came only from YAML/orval.
- Code commit: default@1697121. Parent independently verified users/forms52/52, format/lint/typecheck/build and identical API generation. STORY-005 trace8/8. Independent source review found no critical issue.
- Browser at1366×768: wrong-domain email rejected with input retained; a valid Project Manager with IT Department was created and appeared with a UUID link and total25. Screenshots are registered under artifacts/assets/identity/.
- Local full-suite limitation BUG-016 was established during KAN-107 and reproduced on main. Its Linux CI run passed516 tests. KAN-108 full Linux CI will use the existing workflow_dispatch on this branch without creating a PR or changing workflow/test settings.

### Blockers
Full branch CI result pending; no source-specific blocker.

### Next up
Push this issue branch, run existing CI workflow for its exact head, then record the full outcome before starting KAN-109.
