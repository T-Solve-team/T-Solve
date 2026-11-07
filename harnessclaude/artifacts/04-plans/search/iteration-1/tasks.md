---
module: search
date: 2026-11-05
total_tasks: 19
owner: Dev
---

# Tasks: search — iteration 1

Spec: `artifacts/03-modules/search/spec.md` · Design: `artifacts/04-plans/search/design.md`

Tasks 1–3 (web app, 25/10): one task = one Jira Story = one branch from an up-to-date `main` = one PR, merged in
this order. Each task is one vertical slice a user can try: it carries every layer it needs (contract, mocks, hooks,
components, tests). Every test's `it` name starts with the AC id it proves (guideline 10 T6). Each task ends with
the frontend `verify` (root `CLAUDE.md`): `format:check`, `lint`, `typecheck`, `test`, `build`, and `generate:api`
with no diff.

Tasks 4–18 (backend and connection, change of 05/11) follow "Backend tasks" below.

## Task 1: Ticket List

**Story:** STORY-001, STORY-004
**Feature:** ticket-list
**Jira:** KAN-227 · branch `feature/KAN-227-search-ticket-list` (also carries the plan commit); the backend API of UC-27 is KAN-147
**Description:**
- Contract: tag `tickets`, `GET /api/v1/tickets` (`getTickets`) with `TicketStatus`, `TicketProject`,
  `TicketDepartment`, `TicketSummary`, `TicketSummaryPage` (spec Interface Contracts); regenerate the client.
- Mocks: `ticket-data.ts` (28 tickets, every status and source, Departments IT
  `00000000-0000-4000-8000-0000000000d1` and HR `…0000000000d2` with Projects ITSUP `…000000000101`, ITNET
  `…000000000102`, HRHELP `…000000000201` — the UUID literals of `src/mocks/session.ts` after fe-foundation Task 10 —
  UUID `ticketId`s, Solution text);
  the handler applies `keyword`, `departmentId`, `source`, `status`, sort and paging; `reviewDueDate` only for
  Pending review and Changes requested; `resetMockData()` stays empty (the data never changes).
- Screen: `useTicketList` (URL state, default sort `updatedAt,desc`, generated `useGetTickets`),
  `TicketListFilters` (Keyword on Enter, `maxLength` 100, Department from the session as `String(id)`, Source,
  Status), `TicketListTable` (`DataTable`, Ticket ID link to `paths.ticketDetail(ticketId)`, `StatusTag`,
  `TICKET_SOURCE_LABELS`, `formatDateTime`, "—"), `TicketListPage` replacing the placeholder.
- Tests: the page test (`renderApp('/tickets', { role })`, MSW overrides for loading, empty, 500 then Try again,
  HTML in a title, a user with no Department) and the mock handler test.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify: `frontend/src/features/tickets/pages/TicketListPage.tsx`, `frontend/src/features/tickets/mocks/handlers.ts`
- Create: `frontend/src/features/tickets/pages/TicketListPage.module.css`,
  `frontend/src/features/tickets/components/TicketListFilters.tsx`,
  `frontend/src/features/tickets/components/TicketListFilters.module.css`,
  `frontend/src/features/tickets/components/TicketListTable.tsx`, `frontend/src/features/tickets/hooks/useTicketList.ts`,
  `frontend/src/features/tickets/mocks/ticket-data.ts`
- Test: `frontend/src/features/tickets/pages/TicketListPage.test.tsx`,
  `frontend/src/features/tickets/mocks/handlers.test.ts`
**Covers:** AC-001.1 – AC-001.14, AC-004.1, AC-004.2 (edge cases #1–#5, #16)
**Dependencies:** none
**Estimated size:** medium

---

## Task 2: Search Published tickets

**Story:** STORY-002, STORY-004
**Feature:** ticket-search
**Jira:** KAN-145 · branch `feature/KAN-145-search-page`
**Description:**
- Contract: tag `search`, `GET /api/v1/search/tickets` (`searchTickets`) with `TicketSearchResult` and
  `TicketSearchResultPage`, reusing Task 1's `TicketProject` and `TicketDepartment`; regenerate the client.
- Mocks: `search-data.ts` (14 Published results with Solution text, same Department and Project constants as
  Task 1, two with `helpfulRate: null`, one with `0`); the handler matches `q` on title and Solution text
  (case-insensitive) and applies `departmentId`, `source`, `resolvedFrom`, `resolvedTo` (days included) and paging.
- Screen: `useTicketSearch` (URL state for `q`, filters and page; `enabled` only with a non-blank `q`),
  `SearchFilters` (Search box on Enter, `maxLength` 200; Department; Source; `DatePicker.RangePicker` as
  `YYYY-MM-DD`), `SearchResultList` (`LoadingState`, `ErrorState` with Try again, `EmptyState` MSG04, antd
  `Pagination` with "Total N items"), `SearchResultCard` (title link, "Project · Department · Resolved …",
  "N% helpful" with a thumbs-up icon unless null, clamped excerpt), `SearchPage` replacing the placeholder.
- Tests: the page test (no call without `q`, Enter with blank text, filters with and without `q`, URL restore,
  `helpfulRate` 0 and null, loading, empty, 500 then Try again, HTML in title and excerpt) and the mock handler test.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify: `frontend/src/features/search/pages/SearchPage.tsx`, `frontend/src/features/search/mocks/handlers.ts`
- Create: `frontend/src/features/search/pages/SearchPage.module.css`,
  `frontend/src/features/search/components/SearchFilters.tsx`,
  `frontend/src/features/search/components/SearchResultList.tsx`,
  `frontend/src/features/search/components/SearchResultCard.tsx`,
  `frontend/src/features/search/components/SearchResults.module.css`,
  `frontend/src/features/search/hooks/useTicketSearch.ts`, `frontend/src/features/search/mocks/search-data.ts`
- Test: `frontend/src/features/search/pages/SearchPage.test.tsx`, `frontend/src/features/search/mocks/handlers.test.ts`
**Covers:** AC-002.1 – AC-002.14, AC-004.3 (edge cases #6–#11, #16)
**Dependencies:** Task 1 (its `TicketProject` and `TicketDepartment` schemas)
**Estimated size:** medium

---

## Task 3: Give Feedback popup

**Story:** STORY-003, STORY-004
**Feature:** give-feedback
**Jira:** KAN-148 · branch `feature/KAN-148-search-give-feedback`
**Description:**
- Contract: tag `feedback`, `PUT /api/v1/tickets/{ticketId}/feedback` (`giveFeedback`, `204`) with
  `TicketFeedbackRequest` (`isHelpful`, `comment`); regenerate the client.
- Mocks: the handler accepts the Published ticket ids of Task 2's `search-data.ts` (copied as constants), answers
  204 and stores the Feedback per ticket (a new one replaces the old one), 400 MSG01 on `isHelpful` when missing,
  400 MSG02 on `comment` over 500 characters, 404 for any other id; `resetMockData()` clears the stored Feedback.
- Popup: `useSendFeedback` (generated mutation, `meta: { handlesOwnErrors: true }`), `GiveFeedbackModal`
  (`FormModal` "Give Feedback", Send; Helpful? as two `aria-pressed` buttons bound to `isHelpful` with
  `rules.required('Helpful?')`; Comment `TextArea` with `rules.maxLength('Comment', 500)` and a counter; trimmed
  comment, blank → null; field errors through `applyApiErrors`, others `showMessage(code ?? 'MSG06')`; success →
  `onClose()` and MSG26); export it from `features/feedback/index.ts`.
- Tests: the popup test (`renderWithProviders`, each AC, double click on Send, 404, reopen after Cancel) and the
  mock handler test.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify: `frontend/src/features/feedback/index.ts`, `frontend/src/features/feedback/mocks/handlers.ts`
- Create: `frontend/src/features/feedback/components/GiveFeedbackModal.tsx`,
  `frontend/src/features/feedback/components/GiveFeedbackModal.module.css`,
  `frontend/src/features/feedback/hooks/useSendFeedback.ts`
- Test: `frontend/src/features/feedback/components/GiveFeedbackModal.test.tsx`,
  `frontend/src/features/feedback/mocks/handlers.test.ts`
**Covers:** AC-003.1 – AC-003.11, AC-004.4 (edge cases #12–#16)
**Dependencies:** Task 2 (the Published ids of its mock data)
**Estimated size:** small

---

## Backend tasks (change of 2026-11-05)

Spec: STORY-005 to STORY-009 · Design: master section "Backend". Rules: `backend/AGENTS.md`, root `CLAUDE.md`.

- **One task = one feature = one commit** (plus its harness commit), atomic so it can be reverted alone; a
  Liquibase changeset is always its own task. Commit header `type(scope): summary (KAN-<n>)`; body the `STORY-…`
  line; `Co-Authored-By:` trailer when an AI tool helped. Report the feature, files, test result and proposed
  message, and commit only when the user agrees.
- **Branches** (one Jira key each, from an up-to-date `main`; backend and frontend separate; push only when asked,
  never merge into `main`):

  | # | Branch | Repo part | Tasks | Starts after |
  |---|--------|-----------|-------|--------------|
  | 1 | `feat/KAN-141-search-ticket-embedding` | backend | 4–10 | — |
  | 2 | `feat/KAN-147-search-ticket-list-api` | backend | 11 | branch 1 merged |
  | 2 | `feat/KAN-147-search-ticket-list-fe` | frontend | 12 | branch 2 backend merged; Task 1 |
  | 3 | `feat/KAN-144-search-ticket-search-api` | backend | 13, 14 | branch 1 merged |
  | 3 | `feat/KAN-145-search-ticket-search-fe` | frontend | 15 | branch 3 backend merged; Task 2 |
  | 4 | `feat/KAN-148-search-feedback-api` | backend | 16 | branch 1 merged |
  | 4 | `feat/KAN-148-search-feedback-fe` | frontend | 17 | branch 4 backend merged; Task 3 |
  | 5 | `feat/KAN-146-search-suggestion-api` | backend | 18 | branch 3 backend merged |

  Branches 2, 3 and 4 (backend) run in parallel: their **Java** files are disjoint (`search/list`, `search/query`,
  `search/feedback`). The contract files are not: `frontend/openapi/tsolve-api.yaml` and
  `frontend/src/shared/api/generated/**` are changed by Tasks 11, 13, 18 and the connect Tasks 12, 15, 17, so those
  branches merge one at a time; each rebases on the latest `main`, re-runs `npm run generate:api`, checks it leaves
  no diff and runs the frontend `npm run typecheck` before its PR.
- **Backend check:** `mvn spotless:apply`, then `mvn verify` in `backend/` (Docker running for the `*IT` tests).
  Java test methods end with the AC id (`returnsOnlyVisibleTickets_AC_006_1`); `*IT` classes use Testcontainers
  `pgvector/pgvector:pg16` like `SuggestionResultRecordRepositoryIT`.
- **Frontend check:** the frontend `verify` above.
- **Answered 05/11:** `project.name` = `project.jira_project_key` (AC-006.6, a stand-in: one mapping line, the
  `project_name` expression of `SearchTicket` and `TicketRankingRepository`); `reviewDueDate` always null, sort key
  kept (AC-006.7); `resolvedAt` = start of `resolved_date` in Asia/Ho_Chi_Minh, nullable (AC-007.5, AC-002.15).
  No task is blocked.
- Paths below are from the repo root; `…/search/` means `backend/src/main/java/vn/tsolve/search/` and
  `…/test/search/` means `backend/src/test/java/vn/tsolve/search/`; changesets are in
  `backend/src/main/resources/db/changelog/changes/`, each also added to
  `backend/src/main/resources/db/changelog/db.changelog-master.xml`.

---

## Task 4: Public ids on ticket, project and department

**Story:** STORY-005
**Feature:** search-public-ids
**Jira:** KAN-141 · branch `feat/KAN-141-search-ticket-embedding`
**Description:**
- Changeset `008-search-public-ids.sql` (`--changeset search:008-search-public-ids`): `public_id UUID NOT NULL
  DEFAULT gen_random_uuid()` and `uq_ticket_public_id`, `uq_project_public_id`, `uq_department_public_id`;
  `--rollback` drops the three columns (development databases only: it loses the ids the API showed; the same holds
  for the rollbacks of 011 and 012).
- The schema IT (shared by Tasks 4–8) and the rollback IT: it rolls back every changeset of author `search`, checks
  that no search column, table, function or trigger is left, then applies them again.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/008-search-public-ids.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Test: `backend/src/test/java/vn/tsolve/search/SearchSchemaIT.java` (`givesEveryExistingRowItsOwnPublicId_AC_005_1`,
  `rejectsDuplicatePublicId_AC_005_1`),
  `backend/src/test/java/vn/tsolve/search/SearchChangesetRollbackIT.java` (`rollsBackEverySearchChangesetByCount_AC_005_3`:
  its own container; rolls back the count of changesets of author `search`, the `vector` extension excepted)
**Covers:** AC-005.1 (public ids), AC-005.3 (edge case #23)
**Dependencies:** none
**Estimated size:** small

---

## Task 5: ticket.updated_at kept by a trigger

**Story:** STORY-005
**Feature:** ticket-updated-at
**Jira:** KAN-141 · branch `feat/KAN-141-search-ticket-embedding`
**Description:**
- Changeset `009-search-ticket-updated-at.sql` with `splitStatements:false`: column `updated_at TIMESTAMPTZ NOT NULL
  DEFAULT now()`, `UPDATE ticket SET updated_at = created_at`, function `search_touch_updated_at()` and trigger
  `trg_ticket_updated_at BEFORE UPDATE ON ticket FOR EACH ROW`; `--rollback` drops trigger, function, column.
- The existing `ImportConfirmIT` still passes (the `Ticket` entity does not map the column).
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/009-search-ticket-updated-at.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`,
  `backend/src/test/java/vn/tsolve/search/SearchSchemaIT.java` (`copiesCreatedAtIntoUpdatedAt_AC_005_1`,
  `setsUpdatedAtOnEveryUpdate_AC_005_1`), `backend/src/test/java/vn/tsolve/search/SearchChangesetRollbackIT.java`
**Covers:** AC-005.1 (`updated_at`), AC-005.3
**Dependencies:** Task 4
**Estimated size:** small

---

## Task 6: ticket_embedding table

**Story:** STORY-005
**Feature:** ticket-embedding-schema
**Jira:** KAN-141 · branch `feat/KAN-141-search-ticket-embedding`
**Description:**
- Changeset `010-search-ticket-embedding.sql`: `CREATE EXTENSION IF NOT EXISTS vector`; table `ticket_embedding`
  (spec Interface Contracts); `--rollback DROP TABLE ticket_embedding` (the extension stays).
- If the platform's row-policy guard is on `main`: enable RLS and add its policy (master design, Tables).
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/010-search-ticket-embedding.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`,
  `backend/src/test/java/vn/tsolve/search/SearchSchemaIT.java` (`createsTicketEmbeddingWithTenantConvention_AC_005_2`,
  `refusesVectorOfOtherLength_AC_005_2`, `keepsOneEmbeddingPerTicket_AC_005_2`),
  `backend/src/test/java/vn/tsolve/search/SearchChangesetRollbackIT.java`
**Covers:** AC-005.2 (`ticket_embedding`, extension), AC-005.3
**Dependencies:** Task 5
**Estimated size:** small

---

## Task 7: search_log table

**Story:** STORY-005
**Feature:** search-log-schema
**Jira:** KAN-141 · branch `feat/KAN-141-search-ticket-embedding`
**Description:**
- Changeset `011-search-log.sql`: table `search_log` (FK `app_user(id)`, nullable FK `department(department_id)`,
  CHECK on `channel` and `result_count >= 0`, `is_fallback BOOLEAN NOT NULL`), index on `created_at`;
  `--rollback DROP TABLE search_log`. Row policy as in Task 6 if the guard is on `main`.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/011-search-log.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`,
  `backend/src/test/java/vn/tsolve/search/SearchSchemaIT.java` (`createsSearchLogWithTenantConvention_AC_005_2`,
  `refusesUnknownChannel_AC_005_2`), `backend/src/test/java/vn/tsolve/search/SearchChangesetRollbackIT.java`
**Covers:** AC-005.2 (`search_log`), AC-005.3
**Dependencies:** Task 6
**Estimated size:** small

---

## Task 8: ticket_feedback table

**Story:** STORY-005
**Feature:** ticket-feedback-schema
**Jira:** KAN-141 · branch `feat/KAN-141-search-ticket-embedding`
**Description:**
- Changeset `012-search-ticket-feedback.sql`: table `ticket_feedback` (FKs `ticket(id)`, `app_user(id)`, CHECKs on
  `rating`, `channel`, `char_length(comment) <= 500`, `uq_ticket_feedback_ticket_user`);
  `--rollback DROP TABLE ticket_feedback`. Row policy as in Task 6 if the guard is on `main`.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/012-search-ticket-feedback.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`,
  `backend/src/test/java/vn/tsolve/search/SearchSchemaIT.java` (`createsTicketFeedbackWithTenantConvention_AC_005_2`,
  `refusesSecondFeedbackOfSameUser_AC_005_2`, `refusesCommentOver500_AC_005_2`),
  `backend/src/test/java/vn/tsolve/search/SearchChangesetRollbackIT.java`
**Covers:** AC-005.2 (`ticket_feedback`), AC-005.3
**Dependencies:** Task 7
**Estimated size:** small

---

## Task 9: Visible scope port and the searchable-ticket read model

**Story:** STORY-006
**Feature:** visible-scope
**Jira:** KAN-141 · branch `feat/KAN-141-search-ticket-embedding` (shared by branches 2–5)
**Description:**
- `VisibleScope` with record `Scope(Set<Long> departmentIds, Set<Long> managedProjectIds, Optional<String> writer)`
  and `isEmpty()`; `@Component NoVisibleScope` answering empty sets and no writer, with
  `@ConditionalOnExpression("'${SEARCH_DEV_SCOPE:}' != 'all'")` (Javadoc: delete it when the real bean lands, as
  `NoManagedProjects`).
- Dev-only `DevAllVisibleScope` (`@ConditionalOnExpression("'${SEARCH_DEV_SCOPE:}' == 'all'")`, the exact
  case-sensitive mirror of `NoVisibleScope`; not `@ConditionalOnProperty`, whose `havingValue` ignores case): every
  `ACTIVE` Department and every Project through `ScopeTicketRepository`, no writer; one `WARN` at startup
  ("SEARCH_DEV_SCOPE=all: every user sees every Active Department — local demo only"). It gives data, not a
  signed-in user: no dev principal exists in the backend, and none is added.
- `.env.example`: add `SEARCH_DEV_SCOPE=` (empty) with the comment "`all` only for a local demo" (quick change if
  the gate blocks it).
- `SearchTicket` (`@Immutable @Subselect … @Synchronize`, master design "Read model", `p.jira_project_key as
  project_name`); `TicketProjectRef`,
  `TicketDepartmentRef` records (`UUID` id, `String name`).
- Fill `package-info.java` of `vn.tsolve.search` with one line on the feature subpackages.
**Files:**
- Create: `…/search/scope/service/VisibleScope.java`, `…/search/scope/service/impl/NoVisibleScope.java`,
  `…/search/scope/service/impl/DevAllVisibleScope.java`, `…/search/scope/repository/ScopeTicketRepository.java`,
  `…/search/scope/entity/SearchTicket.java`, `…/search/scope/dto/TicketProjectRef.java`,
  `…/search/scope/dto/TicketDepartmentRef.java`
- Modify: `backend/src/main/java/vn/tsolve/search/package-info.java`, `.env.example`
- Test: `…/test/search/scope/service/impl/NoVisibleScopeTest.java` (`answersNoDepartmentProjectOrWriter_AC_006_2`),
  `…/test/search/scope/service/impl/VisibleScopeSelectionTest.java` (`ApplicationContextRunner`:
  `usesDevScopeOnlyForAll_AC_006_11`, `failsClosedWhenUnsetEmptyOrOtherValue_AC_006_12` — unset, `""`, `ALL`,
  `true` each give `NoVisibleScope` and no `DevAllVisibleScope` bean),
  `…/test/search/scope/service/impl/DevAllVisibleScopeIT.java` (`seesEveryActiveDepartmentAndProject_AC_006_11`,
  `leavesOutDepartmentThatIsNotActive_AC_006_11`, `logsDevScopeWarningAtStartup_AC_006_11` with
  `OutputCaptureExtension`),
  `…/test/search/scope/entity/SearchTicketIT.java` (`readsTicketWithItsProjectAndDepartment_AC_006_5`,
  `usesJiraProjectKeyAsProjectName_AC_006_6`, `dropsTicketWithoutProject_AC_006_1`)
**Covers:** AC-006.2, AC-006.11, AC-006.12 (port and stand-ins), AC-006.5, AC-006.6 (read model) (edge cases #45,
#49). Committed with `(KAN-141)` on branch 1.
**Dependencies:** Task 8
**Estimated size:** small

---

## Task 10: Embedding pipeline

**Story:** STORY-005
**Feature:** ticket-embedding-pipeline
**Jira:** KAN-141 · branch `feat/KAN-141-search-ticket-embedding`
**Description:**
- `TicketEmbedding` entity (vector not mapped); `TicketEmbeddingRepository` (native `upsert`,
  `findPublishedWithoutEmbedding(limit, skipped)`); `EmbeddingTicketRepository` (`JpaRepository<SearchTicket, Long>`,
  `findByIdInAndStatus(ids, "PUBLISHED")` for the texts, no native SQL); `PgVector.literal(float[])`.
- `TicketEmbeddingService` / `TicketEmbeddingServiceImpl.embed(ids)`: batches of `SEARCH_EMBED_BATCH` (default 4),
  one batch at a time for the listener and the job (`synchronized` batch method; `ponytail:` comment: per-process
  lock); read texts (read-only tx), `EmbeddingClient.embed` (no tx), upsert (tx); `AiUnavailableException` or a
  runtime failure → log the ids only and continue; `EmbeddingText.of(…)` builds the text (master design).
- Skip rule (AC-005.9): in-memory consecutive-failure count per id; at 3 the job skips the id until restart and logs
  it once (id only); a success resets it; the listener still embeds a skipped id.
- `config/SearchSchedulingConfig` with its own `@EnableScheduling` (the job must not depend on the ticket module's
  `TicketSchedulingConfig`).
- `TicketsPublishedListener` (`@TransactionalEventListener(AFTER_COMMIT)`, `applicationTaskExecutor`, rejection
  logged, never thrown) and `EmbeddingCatchUpJob` (`@Scheduled(fixedDelayString = "${SEARCH_EMBED_CATCH_UP_DELAY:60s}")`).
- `.env.example`: add `SEARCH_EMBED_BATCH`, `SEARCH_EMBED_CATCH_UP_DELAY` (quick change if the gate blocks it).
**Files:**
- Create: `…/search/config/SearchSchedulingConfig.java`, `…/search/embedding/entity/TicketEmbedding.java`,
  `…/search/embedding/repository/TicketEmbeddingRepository.java`,
  `…/search/embedding/repository/EmbeddingTicketRepository.java`, `…/search/embedding/repository/PgVector.java`,
  `…/search/embedding/service/TicketEmbeddingService.java`,
  `…/search/embedding/service/impl/TicketEmbeddingServiceImpl.java`,
  `…/search/embedding/service/impl/EmbeddingText.java`,
  `…/search/embedding/service/impl/TicketsPublishedListener.java`,
  `…/search/embedding/service/impl/EmbeddingCatchUpJob.java`
- Modify: `.env.example`
- Test: `…/test/search/embedding/service/impl/TicketEmbeddingServiceImplTest.java`
  (`embedsPublishedTicketsInBatches_AC_005_4`, `logsOnlyIdsWhenOllamaFails_AC_005_6`,
  `skipsMissingAndUnpublishedIds_AC_005_8`, `sendsOneBatchAtATime_AC_005_7`,
  `skipsIdAfterThreeFailuresInARow_AC_005_9`, `resetsCountOnSuccessAndListenerStillEmbeds_AC_005_9`),
  `…/test/search/embedding/service/impl/EmbeddingTextTest.java` (`joinsPresentSectionsOnePerLine_AC_005_4`),
  `…/test/search/embedding/service/impl/TicketsPublishedListenerTest.java`
  (`neverThrowsIntoTheImportCommit_AC_005_5`),
  `…/test/search/embedding/service/impl/EmbeddingCatchUpJobTest.java` (`embedsLowestIdsWithoutVector_AC_005_7`),
  `…/test/search/embedding/repository/PgVectorTest.java` (`writesVectorLiteral_AC_005_4`),
  `…/test/search/embedding/TicketEmbeddingIT.java` (`storesVectorAfterImportCommits_AC_005_4`,
  `replacesOlderVector_AC_005_4`, `importSucceedsWhenOllamaIsDown_AC_005_5`) — `EmbeddingClient` replaced by a test
  bean; the import goes through `ImportConfirmService` as `ImportConfirmIT` does
**Covers:** AC-005.4 – AC-005.9 (edge cases #19–#22)
**Dependencies:** Task 6
**Estimated size:** medium

---

## Task 11: Ticket List API

**Story:** STORY-006
**Feature:** ticket-list-api
**Jira:** KAN-147 · branch `feat/KAN-147-search-ticket-list-api`
**Description:**
- `TicketListController` `GET /api/v1/tickets` (`@Tag("tickets")`, `operationId = "getTickets"`,
  `@PreAuthorize("hasAnyRole('TICKET_RESOLVER','PROJECT_MANAGER')")`, `@AuthenticationPrincipal TSolveUser`), raw
  parameters into `TicketListQuery` (400 MSG06 through `InvalidListQueryException`, MSG02 on `keyword`).
- `TicketListServiceImpl` (`@Transactional(readOnly = true)`): `VisibleScope.of(user)`; empty → empty page; a
  `departmentId` is resolved to its internal id (`TicketListRepository.findDepartmentId`) and checked against
  `scope.departmentIds`: unknown or outside → empty page, also for the user's own and managed-Project tickets;
  `TicketListSpecs` (visibility, filters, escaped keyword); sort mapping with `id desc` last; `TicketSummaryMapper`
  (`SearchTicket` → `TicketSummaryResponse`; `project.name` from `projectName`; `reviewDueDate` always null; the
  sort key `reviewDueDate` accepted with no order of its own, AC-006.7).
- Contract first (AGENTS rule 8): add `'400': $ref: '#/components/responses/Problem'` to `getTickets` in
  `frontend/openapi/tsolve-api.yaml` (AC-006.9); regenerate `frontend/src/shared/api/generated/**` in the same commit;
  run the frontend `npm run typecheck` and check `npm run generate:api` leaves no diff.
**Files:**
- Create: `…/search/list/controller/TicketListController.java`, `…/search/list/dto/TicketListQuery.java`,
  `…/search/list/dto/TicketSummaryResponse.java`, `…/search/list/exception/InvalidListQueryException.java`,
  `…/search/list/repository/TicketListRepository.java`, `…/search/list/mapper/TicketSummaryMapper.java`,
  `…/search/list/service/TicketListService.java`, `…/search/list/service/impl/TicketListServiceImpl.java`,
  `…/search/list/service/impl/TicketListSpecs.java`
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Test: `…/test/search/list/controller/TicketListControllerTest.java` (`answers401WithoutSession_AC_006_8`,
  `answers403ForEmployee_AC_006_8`, `rejectsLongKeyword_AC_006_9`, `rejectsBadPagingSortOrValue_AC_006_9`,
  `usesDefaultPageAndSort_AC_006_3`, `ignoresWorkspaceParameter_AC_006_10`),
  `…/test/search/list/service/impl/TicketListServiceImplTest.java` (`returnsEmptyPageForEmptyScope_AC_006_2`),
  `…/test/search/list/repository/TicketListRepositoryIT.java` (`returnsOnlyVisibleTickets_AC_006_1`,
  `hidesTicketsOfDeletedDepartment_AC_006_1`, `combinesFiltersWithLiteralKeyword_AC_006_4`,
  `returnsEmptyForDepartmentOutsideScope_AC_006_4`,
  `returnsEmptyForOtherDepartmentEvenWithManagedProjectThere_AC_006_4`, `sortsByEveryKeyWithIdTieBreak_AC_006_3`,
  `returnsPublicIdsAndNames_AC_006_5`, `returnsJiraProjectKeyAsProjectNameAndSortsByIt_AC_006_6`, `returnsNullReviewDueDateAndSortsByTieBreak_AC_006_7`)
**Covers:** AC-006.1 – AC-006.10 (edge cases #24–#29, #48)
**Dependencies:** Tasks 4, 5, 9
**Estimated size:** medium

---

## Task 12: Connect the Ticket List to the backend

**Story:** STORY-001, STORY-006
**Feature:** ticket-list-connect
**Jira:** KAN-147 · branch `feat/KAN-147-search-ticket-list-fe`
**Description:**
- Backend of Task 11 running locally (`docker compose up -d db`, `mvn spring-boot:run`); `npm run api:pull`; keep
  only the differences of `getTickets` and its schemas in `frontend/openapi/tsolve-api.yaml` (master design,
  "Contract and frontend connection"); `npm run generate:api`.
- Fix any drift in the feature: hooks, components, MSW mock data and handler typed with the regenerated types; the
  MSW mocks stay for tests and the dev mock mode. No change in `app/`, `shared/` (except the generated client),
  `mocks/` or `test/`.
- Run `/tickets` with `VITE_API_MOCKING=false` against the backend: the empty state (default scope), 401 and the
  network contract; with `SEARCH_DEV_SCOPE=all` the rows, once a user can sign in (no dev principal exists); note
  in the PR what could not be shown.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify (only on drift): `frontend/src/features/tickets/hooks/useTicketList.ts`,
  `frontend/src/features/tickets/components/TicketListTable.tsx`,
  `frontend/src/features/tickets/mocks/handlers.ts`, `frontend/src/features/tickets/mocks/ticket-data.ts`
- Test: `frontend/src/features/tickets/pages/TicketListPage.test.tsx`,
  `frontend/src/features/tickets/mocks/handlers.test.ts` (existing AC-001.*, AC-004.1, AC-004.2 tests stay green;
  a reworded criterion gets its test updated)
**Covers:** AC-001.1 – AC-001.14 and AC-004.1 – AC-004.2 against the real contract
**Dependencies:** Task 1, Task 11
**Estimated size:** small

---

## Task 13: Ranking service with keyword fallback

**Story:** STORY-007
**Feature:** ticket-ranking
**Jira:** KAN-144 · branch `feat/KAN-144-search-ticket-search-api`
**Description:**
- `TicketRankingService` / `TicketRankingServiceImpl.rank(text, keywordText, RankFilter, Scope, Pageable)` →
  `RankedPage`: empty scope → empty, no embedding; `EmbeddingClient.embed` outside any transaction;
  `AiUnavailableException` → keyword; `SEARCH_MIN_SIMILARITY` (0.5).
- `TicketRankingRepository`: native semantic and keyword queries with count queries, interface projection
  `RankedRow`; `helpfulRate` aggregate; `ponytail:` comment on the missing HNSW index; every optional filter bound
  as text and cast in SQL (`CAST(:departmentId AS uuid)`, `CAST(:resolvedFrom AS date)`), because Hibernate 6 sends a
  null `UUID`/`LocalDate` as `bytea`.
- `TicketSearchResultMapper` + `Excerpts`; `TicketSearchResultResponse` (`resolvedAt` =
  `resolved_date.atStartOfDay(Asia/Ho_Chi_Minh)` as `OffsetDateTime`, null without a date; `project.name` =
  `jira_project_key`).
- Contract first: `TicketSearchResult.resolvedAt` becomes `type: [string, 'null'], format: date-time` (still
  required) in `frontend/openapi/tsolve-api.yaml`; regenerate `frontend/src/shared/api/generated/**` in the same
  commit (`formatDateTime` already accepts null, so the frontend still builds; the card change is Task 15); run the
  frontend `npm run typecheck` and check `npm run generate:api` leaves no diff.
- `.env.example`: add `SEARCH_MIN_SIMILARITY` (quick change if the gate blocks it).
**Files:**
- Create: `…/search/query/service/TicketRankingService.java`,
  `…/search/query/service/impl/TicketRankingServiceImpl.java`, `…/search/query/service/impl/Excerpts.java`,
  `…/search/query/repository/TicketRankingRepository.java`, `…/search/query/dto/RankFilter.java`,
  `…/search/query/dto/RankedPage.java`, `…/search/query/dto/TicketSearchResultResponse.java`,
  `…/search/query/mapper/TicketSearchResultMapper.java`
- Modify: `.env.example`, `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Test: `…/test/search/query/service/impl/TicketRankingServiceImplTest.java`
  (`fallsBackToKeywordWhenOllamaFails_AC_007_2`, `skipsEmbeddingForEmptyScope_AC_006_2`),
  `…/test/search/query/service/impl/ExcerptsTest.java` (`collapsesSpaceAndCutsAt200_AC_007_4`),
  `…/test/search/query/repository/TicketRankingRepositoryIT.java` (`ranksBySimilarityAboveCutOff_AC_007_1`,
  `neverReturnsUnpublishedOrDeletedDepartment_AC_007_1`, `matchesKeywordLiterallyNewestFirst_AC_007_2`,
  `appliesFiltersAndInclusiveDates_AC_007_3`, `returnsEmptyForDepartmentOutsideScope_AC_007_3`,
  `answersWithNoFilterAtAll_AC_007_3`, `returnsRoundedHelpfulRateOrNull_AC_007_4`,
  `returnsResolvedAtAsStartOfVietnamDayOrNull_AC_007_5`, `usesJiraProjectKeyAsProjectName_AC_006_6`)
**Covers:** AC-007.1 – AC-007.5, AC-006.6 (edge cases #30–#35, #46, #51)
**Dependencies:** Tasks 6, 8, 9
**Estimated size:** medium

---

## Task 14: Search API and Search Log

**Story:** STORY-007
**Feature:** ticket-search-api
**Jira:** KAN-144 · branch `feat/KAN-144-search-ticket-search-api`
**Description:**
- `TicketSearchController` `GET /api/v1/search/tickets` (`@Tag("search")`, `searchTickets`, roles as Task 11);
  `TicketSearchQuery` takes the raw parameters as strings like triage's `HistoryQuery`: `departmentId` UUID, `source`
  contract value, dates only `yyyy-MM-dd` with a four-digit year (`uuuu-MM-dd`, `ResolverStyle.STRICT`, BUG-106),
  `page`, `size` (AC-006.9 rules) → else `InvalidSearchQueryException` (400 MSG06); `q` `@NotBlank @Size(max = 200)`
  (MSG01 / MSG02 on `q`); MSG39 when `resolvedFrom > resolvedTo`.
- `TicketSearchService` / `TicketSearchServiceImpl`: scope → rank → page-0 log; no transaction around the call.
- `SearchLog` entity, `SearchLogRepository.log(…)` (native insert, Department by `public_id`).
**Files:**
- Create: `…/search/query/controller/TicketSearchController.java`, `…/search/query/dto/TicketSearchQuery.java`,
  `…/search/query/exception/InvalidSearchQueryException.java`,
  `…/search/query/service/TicketSearchService.java`, `…/search/query/service/impl/TicketSearchServiceImpl.java`,
  `…/search/query/entity/SearchLog.java`, `…/search/query/repository/SearchLogRepository.java`
- Test: `…/test/search/query/controller/TicketSearchControllerTest.java` (`rejectsMissingOrBlankQ_AC_007_7`,
  `rejectsLongQ_AC_007_7`, `rejectsReversedDates_AC_007_7`, `rejectsBadFilterOrPaging_AC_007_7`,
  `rejectsDateNotInYyyyMmDd_AC_007_7`, `answers401And403_AC_007_8`,
  `ignoresWorkspaceParameter_AC_006_10`), `…/test/search/query/service/impl/TicketSearchServiceImplTest.java`
  (`logsPageZeroOnlyWithFallbackFlag_AC_007_6`, `neverLogsQueryText_AC_007_9`),
  `…/test/search/query/repository/SearchLogRepositoryIT.java` (`writesRowWithDepartmentOfFilter_AC_007_6`)
**Covers:** AC-007.6 – AC-007.9, AC-006.10 (edge cases #29, #30, #33, #35, #36, #50)
**Dependencies:** Tasks 7, 13
**Estimated size:** medium

---

## Task 15: Connect Search to the backend

**Story:** STORY-002, STORY-007
**Feature:** ticket-search-connect
**Jira:** KAN-145 · branch `feat/KAN-145-search-ticket-search-fe`
**Description:**
- As Task 12 for `searchTickets`: `npm run api:pull`, keep only its differences, `npm run generate:api`, fix drift
  in the feature, keep the MSW mocks.
- `resolvedAt` is nullable (AC-007.5): `SearchResultCard` shows "Resolved —" when it is null (AC-002.15); one mock
  result in `search-data.ts` gets `resolvedAt: null` and the handler's date filters skip it.
- Run `/search` with `VITE_API_MOCKING=false`: empty state, 400 on a long query, the fallback answer with Ollama
  stopped.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify (only on drift): `frontend/src/features/search/hooks/useTicketSearch.ts`,
  `frontend/src/features/search/mocks/handlers.ts`
- Modify: `frontend/src/features/search/components/SearchResultCard.tsx`,
  `frontend/src/features/search/mocks/search-data.ts`
- Test: `frontend/src/features/search/pages/SearchPage.test.tsx` (new
  `it('AC-002.15 shows "Resolved —" when resolvedAt is null')`; existing AC-002.* stay green),
  `frontend/src/features/search/mocks/handlers.test.ts` (existing AC-004.3 tests stay green)
**Covers:** AC-002.1 – AC-002.15 and AC-004.3 against the real contract (edge case #47)
**Dependencies:** Task 2, Task 14
**Estimated size:** small

---

## Task 16: Feedback API

**Story:** STORY-008
**Feature:** ticket-feedback-api
**Jira:** KAN-148 · branch `feat/KAN-148-search-feedback-api`
**Description:**
- `TicketFeedbackController` `PUT /api/v1/tickets/{ticketId}/feedback` (`@Tag("feedback")`, `giveFeedback`,
  roles as Task 11, `204`); `TicketFeedbackRequest` (`@NotNull Boolean isHelpful`, `@Size(max = 500) String comment`).
- `TicketFeedbackServiceImpl.give` (`@Transactional`): `FeedbackTicketRepository.findByPublicId` + searchable check
  → 404 MSG04; trimmed comment, blank → null; `TicketFeedbackRepository.upsert` (channel `WEB`).
- `TicketFeedback` entity (read in tests, written by the native upsert).
**Files:**
- Create: `…/search/feedback/controller/TicketFeedbackController.java`,
  `…/search/feedback/dto/TicketFeedbackRequest.java`, `…/search/feedback/entity/TicketFeedback.java`,
  `…/search/feedback/repository/TicketFeedbackRepository.java`,
  `…/search/feedback/repository/FeedbackTicketRepository.java`,
  `…/search/feedback/service/TicketFeedbackService.java`,
  `…/search/feedback/service/impl/TicketFeedbackServiceImpl.java`
- Test: `…/test/search/feedback/controller/TicketFeedbackControllerTest.java`
  (`rejectsMissingIsHelpful_AC_008_4`, `rejectsLongComment_AC_008_4`, `rejectsNonUuidTicketId_AC_008_4`,
  `answers401And403_AC_008_5`, `ignoresWorkspaceParameter_AC_006_10`),
  `…/test/search/feedback/service/impl/TicketFeedbackServiceImplTest.java`
  (`storesRatingTrimmedCommentAndWebChannel_AC_008_1`, `answers404ForTicketOutsideScope_AC_008_3`),
  `…/test/search/feedback/repository/TicketFeedbackRepositoryIT.java` (`replacesFeedbackOfSameUser_AC_008_2`,
  `keepsOneRowForConcurrentPuts_AC_008_2`, `answers404ForUnpublishedOrDeletedDepartment_AC_008_3`)
**Covers:** AC-008.1 – AC-008.5, AC-006.10 (edge cases #26, #29, #37, #38)
**Dependencies:** Tasks 4, 8, 9
**Estimated size:** small

---

## Task 17: Connect Give Feedback to the backend

**Story:** STORY-003, STORY-008
**Feature:** give-feedback-connect
**Jira:** KAN-148 · branch `feat/KAN-148-search-feedback-fe`
**Description:**
- As Task 12 for `giveFeedback`: `npm run api:pull`, keep only its differences, `npm run generate:api`, fix drift
  in the feature, keep the MSW mocks.
- Mount check with the real backend: 404 (stand-in scope) shows the message of its code and keeps the popup open
  (AC-003.9); 400 field errors land under their fields (AC-003.8).
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify (only on drift): `frontend/src/features/feedback/hooks/useSendFeedback.ts`,
  `frontend/src/features/feedback/components/GiveFeedbackModal.tsx`,
  `frontend/src/features/feedback/mocks/handlers.ts`
- Test: `frontend/src/features/feedback/components/GiveFeedbackModal.test.tsx`,
  `frontend/src/features/feedback/mocks/handlers.test.ts` (existing AC-003.*, AC-004.4 tests stay green)
**Covers:** AC-003.1 – AC-003.11 and AC-004.4 against the real contract
**Dependencies:** Task 3 (`give-feedback`, still `pending` in progress.json), Task 16
**Estimated size:** small

---

## Task 18: Similar-ticket suggestions API

**Story:** STORY-009
**Feature:** ticket-suggestion-api
**Jira:** KAN-146 · branch `feat/KAN-146-search-suggestion-api`
**Description:**
- Contract first: add `POST /api/v1/search/suggestions` (`suggestTickets`, tag `search`) and
  `TicketSuggestionRequest` to `frontend/openapi/tsolve-api.yaml` (spec Interface Contracts); regenerate
  `frontend/src/shared/api/generated/**` in the same commit (no screen uses it); run the frontend
  `npm run typecheck` and check `npm run generate:api` leaves no diff.
- `TicketSuggestionController` (roles as Task 11, web session only); `TicketSuggestionRequest`
  (`@NotBlank @Size(max = 255) summary`, `@Size(max = 32767) description`).
- `TicketSuggestionServiceImpl`: text = summary + blank line + description (summary alone when blank), keyword text =
  summary, `TicketRankingService.rank(…, PageRequest.of(0, 5))`, log channel `ISSUE_PANEL` through
  `SearchLogRepository`.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Create: `…/search/suggestion/controller/TicketSuggestionController.java`,
  `…/search/suggestion/dto/TicketSuggestionRequest.java`, `…/search/suggestion/service/TicketSuggestionService.java`,
  `…/search/suggestion/service/impl/TicketSuggestionServiceImpl.java`
- Test: `…/test/search/suggestion/controller/TicketSuggestionControllerTest.java`
  (`rejectsMissingOrLongSummary_AC_009_4`, `rejectsLongDescription_AC_009_4`, `refusesForgeTokenAlone_AC_009_5`,
  `answers403ForEmployee_AC_009_5`, `ignoresWorkspaceParameter_AC_006_10`),
  `…/test/search/suggestion/service/impl/TicketSuggestionServiceImplTest.java`
  (`ranksSummaryAndDescriptionTopFive_AC_009_1`, `fallsBackToSummaryKeyword_AC_009_2`,
  `logsIssuePanelRowWithReturnedCount_AC_009_3`)
**Covers:** AC-009.1 – AC-009.5, AC-006.10 (edge cases #29, #39, #40)
**Dependencies:** Tasks 13, 14
**Estimated size:** small

---

## Task 19: Solution retrieval for the Chatbot

**Story:** STORY-010
**Feature:** solution-retrieval
**Jira:** KAN-179 · branch `feat/KAN-179-chatbot-answers` (the chatbot backend's first commit; chatbot iteration 2)
**Description:**
- `SolutionRetrieval` / `SolutionRetrievalImpl.nearest(queryVector, minSimilarity, limit)`: argument checks
  (1024 dimensions, 0 ≤ minSimilarity ≤ 1, limit ≥ 1, else `IllegalArgumentException`, no query); maps rows to
  `RetrievedSolution`, blank Workaround → null.
- `SolutionRetrievalRepository`: the native query of the chatbot design ("Data"), vector through
  `PgVector.literal` and `CAST(:vector AS vector)`, interface projection; no Department or user filter.
**Files:**
- Create: `…/search/retrieval/service/SolutionRetrieval.java`, `…/search/retrieval/service/impl/SolutionRetrievalImpl.java`,
  `…/search/retrieval/repository/SolutionRetrievalRepository.java`, `…/search/retrieval/dto/RetrievedSolution.java`
- Test: `…/test/search/retrieval/service/impl/SolutionRetrievalImplTest.java` (`rejectsBadArguments_AC_010_5`,
  `returnsSectionsWithoutTicketDetails_AC_010_2`, `takesNoUserAndEmbedsNothing_AC_010_4`),
  `…/test/search/retrieval/repository/SolutionRetrievalRepositoryIT.java` (`returnsNearestAboveCutOff_AC_010_1`,
  `leavesOutUnpublishedInactiveOrFar_AC_010_3`, `ignoresTheUsersDepartments_AC_010_1`)
**Covers:** AC-010.1 – AC-010.5
**Dependencies:** Task 6 (ticket_embedding)
**Estimated size:** small
