# Task 23: AI check on Ticket Detail (KAN-462)

**Module:** ticket-review
**Assignee:** Claude (harnessclaude-developer subagent) for Đặng Nhật Minh
**Status:** in-progress

---

## Session 2026-11-26

### Working on: ai-review-assist (STORY-015, AC-015.1–AC-015.8)
- Backend: `POST /api/v1/tickets/{ticketId}/ai-review` in `AiReviewController` (Project Manager, Workspace scope through `ReviewWorkspaceAccess.managedProjectIds` like the decisions). `AiReviewServiceImpl` reads the ticket text, then calls `LlmClient.chat` once with a system prompt (AC-015.3 checks, language of the Solution, `<ticket>` text is data) and the findings JSON schema; it validates the answer (object, `findings` array, each item with enum `field`/`severity` and string `issue`/`suggestion`, no trailing tokens), sorts HIGH→MEDIUM→LOW, keeps 10, strips and cuts texts to 500 chars. Any AI exception or schema mismatch → `AiReviewUnavailableException` (503 `MSG_NEW_AI_REVIEW_1`), thrown without the cause and with no log line. Not transactional, so the AI call runs outside a transaction.
- Scope check: new `TicketReviewReadService.reviewText(managedProjectIds, ticketId)` in the ticket module repeats `TicketDecisionServiceImpl.decide`'s checks in the same order: empty managed set or unmanaged Project → 403 MSG08, missing (other Workspace = other database) → 404 MSG04, not Solution Form or not Pending review → 409 MSG06. It returns a new `TicketReviewView.ReviewText` record through `TicketReviewMapper`. Review may not read the ticket repository (backend AGENTS §1), so the read lives in the ticket module.
- Frontend: `AiReviewPanel` (button "AI check", findings grouped High/Medium/Low with the Ticket Detail field labels, plain text, MSG_NEW_AI_REVIEW_2 when empty, button loading + disabled while pending, `ErrorState` with retry showing the backend code) rendered in `ReviewTicketDetail` only under `canDecide`, next to the decision controls and independent of them. The mutation handles its own errors (no global toast).
- MSW: `POST */api/v1/tickets/:ticketId/ai-review` in `mocks/handlers.ts` with the decisions' scope, fixtures `AI_REVIEW_FINDINGS` in `mocks/data.ts`; stores nothing.
- Contract: `runAiReview`, `AiReviewResult`, `AiReviewFinding` in `frontend/openapi/tsolve-api.yaml`; client regenerated.
- Catalog: `MSG_NEW_AI_REVIEW_1` ("AI check unavailable") and `MSG_NEW_AI_REVIEW_2` ("No issues found") added to `src/shared/messages/catalog.ts` as provisional inline keys, like MSG_NEW_5–7.

### Files
- Backend created: `review/controller/AiReviewController.java`, `review/service/AiReviewService.java`, `review/service/impl/AiReviewServiceImpl.java`, `review/dto/AiReviewResult.java`, `review/dto/AiReviewFinding.java`, `review/exception/AiReviewUnavailableException.java`; tests `review/controller/AiReviewControllerTest.java`, `review/service/impl/AiReviewServiceImplTest.java`, `ticket/service/impl/TicketReviewTextTest.java`
- Backend modified: `ticket/dto/TicketReviewView.java`, `ticket/mapper/TicketReviewMapper.java`, `ticket/service/TicketReviewReadService.java`, `ticket/service/impl/TicketReviewReadServiceImpl.java`
- Frontend created: `features/review/components/AiReviewPanel.tsx`, `AiReviewPanel.test.tsx`, `features/review/mocks/ai-review.test.ts`, generated `model/aiReviewFinding*.ts`, `model/aiReviewResult.ts`
- Frontend modified: `features/review/components/ReviewTicketDetail.tsx`, `features/review/mocks/handlers.ts`, `features/review/mocks/data.ts`, `openapi/tsolve-api.yaml`, `shared/messages/catalog.ts`, `shared/messages/catalog.test.ts`, generated `model/index.ts`, `review/review.ts`, `review/review.msw.ts`

### Deviations from the plan
- Files outside the task list: the ticket module read (`reviewText`, `ReviewText`, mapper method), `review/exception/AiReviewUnavailableException.java`, and the `shared/messages` catalog and its test (frontend AGENTS rule 10: `shared/` changes need FE Lead review).
- The MSW handler lives in `mocks/handlers.ts` next to the other ticket routes; its test is `mocks/ai-review.test.ts`.
- No PII masking before the AI call (backend AGENTS §4): the code base has no masking utility, and the other LlmClient callers send text unmasked. Not added.

### Open questions
- MSG_NEW_AI_REVIEW_1 text: the spec gives only "(AI check unavailable)"; the catalog uses that literally. MSG_NEW_AI_REVIEW_2 uses "No issues found". Both await System_Messages.
- UI labels not fixed by the spec: group headings "High", "Medium", "Low" and the field label "General".

### Verification (2026-11-26)
- Backend `mvn -B -Dapi.version=1.44 verify` (IntelliJ jbr as JAVA_HOME): **FAIL, environment** — Docker Desktop was not running ("Could not find a valid Docker environment"); surefire `Tests run: 3291, Failures: 0, Errors: 186`, all errors in Testcontainers-backed identity/session/security classes, so failsafe ITs did not run. To repeat with Docker up.
- Backend `mvn spotless:check`: pass. `mvn test` for `vn.tsolve.review.**`, `vn.tsolve.ticket.**`, `vn.tsolve.ai.**` unit tests: `Tests run: 573, Failures: 0, Errors: 0` BUILD SUCCESS (includes AiReviewServiceImplTest 18, AiReviewControllerTest 5, TicketReviewTextTest 11, TicketDetailVisibilityTest 21).
- Frontend (Node 24.22.2): `format:check` pass; `lint` pass (0 errors, 1 pre-existing warning in `features/tickets/components/TicketListFilters.tsx`); `typecheck` pass; `build` pass; `generate:api` twice gives identical files.
- Frontend `npm run test`: 2018/2020 in the full run, run alongside `mvn verify`; the 2 failures were in unrelated files (`chatbot/pages/KnowledgeChatbotPage.test.tsx` AC-001.9, `jira-integration/components/AddMemberModal.test.tsx` AC-002.10, timing). Rerun alone: 53/53 pass. Review feature: 140/140.
- `bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-015`: exit 0, "8/8 acceptance criteria have a test".

### Commit
None — the user approves the feature commit.

### Next up
User review and commit of ai-review-assist.

## Session 2026-11-26 — verification and commit

- Full backend `mvn -B -Dapi.version=1.44 verify` with Docker up: BUILD SUCCESS, unit 3291/0/0, integration 1016/0/0.
- Re-run by the orchestrator: spotless pass; frontend format, lint (0 errors, 1 old warning), typecheck, test 2020/2020, build pass; `generate:api` stable; `sdd.ts trace ticket-review STORY-015` 8/8.
- Code commit: default@5946758d.

### Next up
Evaluate ai-review-assist (evaluate skill).

## Session 2026-11-26 — review findings

- Fixed review findings 1 and 2 test-first: BUG-398 (escape `& < >` in the prompt's ticket fields), BUG-399 (strip again after the 500-character cut). Logged in docs/bug-log.md.
- Full `mvn -B -Dapi.version=1.44 verify`: BUILD SUCCESS, unit 3293/0/0, integration 1016/0/0.
- Code commit: default@980c5116. Finding 3 (no server-side throttle) left: not in the spec.
