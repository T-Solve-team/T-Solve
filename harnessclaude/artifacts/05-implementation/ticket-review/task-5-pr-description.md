## Jira issue
KAN-24

## What & why
Implements the Project Manager Review Queue and Approve / Reject / Request Changes with one shared comment per selection, Ticket Detail actions, atomic history/audit/notification handoffs, and a generated API contract.

Backend, FE, OpenAPI and the original migration were developed together to verify one transactional review flow end to end. This exceeds the team's ~400-line guideline; generated clients account for substantial churn. Following the latest review, the backend is NOT merge-ready: it must use KAN-31's canonical ticket/history schema and be-foundation's shared audit/legacy exception contracts before merge. The user chose this direction on 24 October. This PR must remain blocked until those dependencies are integrated and clean-environment backend checks pass.

The main Chatbot paths, tags and UnavailableDocuments contract are being retained while resolving conflicts. Published branch history is preserved with a merge from main because repository rules prohibit force-push.

Ancillary shared changes are intentional and require FE Lead review: forms.test.tsx contains the Form timer teardown regression; msg.test.ts contains review message-code regressions; http.ts forwards CSRF tokens for relative API paths. These are not additional product features.

Verified fixes: refresh queue/detail on settled mutations including 409/404, remove no-longer-eligible selections without dropping off-page tickets, and reset selections on Project Back/Forward navigation. After syncing main 5197837, all 503 frontend tests pass; format, lint, typecheck, build and repeated API-generation SHA-256 verification pass. Latest code commit: 9ab8d2d. Backend test-compile and Spotless pass; a fresh complete backend verify is not claimed. Formal re-review is not requested yet because canonical backend integration remains pending.

## How to test
1. Use Node 24. In frontend/, run npm ci, then npm run format:check, npm run lint, npm run typecheck, npm test, npm run build. Run npm run generate:api and confirm src/shared/api/generated is unchanged; UnavailableDocuments and the chatbot unavailable-document endpoint/hook must remain.
2. Mock walkthrough: start frontend with VITE_API_MOCKING=true and an empty API base URL. Use the dev user switcher to select the Project Manager (pm), open /review-queue, and click ITSUP-1031 (or open /tickets/1). Ticket Detail displays saved Solution/Evidence; Approve needs no single-ticket confirmation; Reject and Request Changes require one form. Reload to reset mock fixtures.
3. Select several tickets or Select all; batch Approve asks MSG19, Request Changes applies one comment to all selected. Above 500 IDs actions are disabled and the complete selection is retained. Changing Project, including Back/Forward, must clear selection.
4. Run named BUG-045 regressions to simulate a concurrent PM decision returning 409 or a missing ticket returning 404. Verify detail/buttons refresh, removed queue IDs are dropped and the remaining selection can be retried. Keep input on recoverable failed saves.
5. Backend: the prior host-PostgreSQL verify result is historical, not proof of a clean-machine setup. Testcontainers, non-secret test configuration, isolated test lifecycle and a backend CI job are pending alignment with foundation. Do not run the old integration suite against a shared/deployed database.
6. Production auth/Project adapter and email consumer/retry are not implemented by this PR. No production login, email delivery or complete live integration is claimed.

## Screenshots (UI changes)
No screenshots of the new conflict-recovery states are attached yet. Review evidence and pending checks are tracked in the prepared implementation report; screenshot verification is unchecked below.

## Checklist
- [ ] CI green for the latest revision, including backend verification
- [ ] Latest review regressions and full checks pass
- [ ] Canonical KAN-31/foundation migrations integrated and verified together
- [ ] FE Lead approves the scoped shared/app changes
- [ ] Updated UI screenshots attached
- [x] No production authentication fallback or email delivery claim
- [x] Known defects retained in docs/bug-log.md, including already-fixed defects
