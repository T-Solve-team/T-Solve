## Security Review — import-ticket-list — 2026-10-22

Scope: commit range `356ca3e..d15e991` (repo `default`, branch `feat/KAN-31-import-ticket-csv`), harness folder excluded. Files: `backend/src/main/java/vn/tsolve/ticket/**`, `common/error/**`, `common/config/MethodSecurityConfig.java`, `002-ticket-import-schema.sql`, `application.yml`, `.env.example`, `frontend/openapi/tsolve-api.yaml`.
Reviewer: harnessclaude reviewer (independent; judged only from spec, design and code).

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 2 | 3 |
| STRIDE | 0 | 1 | 1 | 1 |

### Checks run
- **Secrets:** grep of the added lines for `password|secret|api_key|token|PRIVATE KEY` assignments — only hit is `"DB_PASSWORD=unused"` in `ImportConfirmIT.java:49` (test placeholder, the container supplies the real connection). No real credential.
- **Tracked env files:** only `.env.example` (no values; the two new names `TSOLVE_STUB_PROJECTS`, `TSOLVE_STUB_INACTIVE_PROJECTS` are blank). `.gitignore:15-17` ignores `.env` and `.env.*` except `.env.example`.
- **Dependencies:** `npm audit` (frontend) → 0 vulnerabilities (no frontend dependency changed). Backend: new runtime dependency `org.apache.commons:commons-csv:1.12.0` (no known CVE); Testcontainers `1.21.4` and `spring-boot-testcontainers` are test scope. No Maven audit plugin (OWASP dependency-check) is configured, so the backend tree was not machine-audited — not installed without asking.
- **Dangerous patterns:** no native SQL, string-built queries, `Runtime.exec`, `ProcessBuilder` or Java deserialization in scope. All queries are JPQL/derived with bound parameters (`TicketRepository.java:13`, `ImportPreviewRepository.java:17-22`). The `errors` JSONB column is written via Hibernate JSON mapping, not string concatenation.

### STRIDE
| Threat | Result |
|--------|--------|
| Spoofing | Every endpoint needs authentication (Spring Boot default chain, pre-existing) and `@PreAuthorize("hasRole('PROJECT_MANAGER')")` at class level (`ImportController.java:39`). Real sign-in (OIDC) belongs to the identity module (out of scope). |
| Tampering | Extension, size (`CsvImportServiceImpl.java:73-78`), strict UTF-8 decoding with `CodingErrorAction.REPORT` (`CsvTicketParser.java:36-39`), RFC 4180 parsing, per-row validation. CSRF stays on (tests use `.with(csrf())`). Mutations are POST only. |
| Repudiation | The Import Batch records who/when/what for Completed and Failed confirms. The audit event is missing (accepted in design Risk Review until `AuditService` exists). |
| Information disclosure | Another user's preview → 404 (`ImportPreviewServiceImpl.java:76-80`, `ImportConfirmServiceImpl.java:83-84`). Failed-batch `error` holds only class name + MSG code (`ImportConfirmServiceImpl.java:101,107`). **But** ticket row content reaches the logs on a DB failure — finding 2. |
| Denial of service | 10 MB multipart limit + 11 MB request limit; page size capped at 100 (`ImportPreviewServiceImpl.java:83`). One preview per PM (unique index). Findings 3 and 5. |
| Elevation of privilege | Stub `ProjectAccess` fails closed (`StubProjectAccess.java:36-38`, tested in `StubProjectAccessTest`). Owner filter on every preview query. **Confirm does not re-check that the caller still manages the Project** — finding 1. |
| SSRF (A10) | Not applicable — no user-supplied URL is fetched. |

### Findings
1. **[HIGH][STRIDE-EoP]** `backend/src/main/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImpl.java:92` — Confirm checks only `projects.isActive(projectId)`, not `projects.managesActive(user, projectId)`. A PM who is removed as manager of Project X after uploading can still publish tickets into X for up to 24 h (until the preview expires). The spec's NFR 3 says "restricted … to Projects the caller manages; the backend re-checks this on every call", and Interface Contracts map "Project not managed → 403 (MSG08)". No AC covers this case at Confirm (a spec gap — see the csv-confirm review).
   - Fix: at Confirm, first `if (!projects.managesActive(user, preview.getProjectId()))` → `ProjectAccessDeniedException` (403 MSG08), keeping MSG40 for "managed but not Active" (e.g. `!projects.isActive` → MSG40 first, then `!managesActive` → 403). Add an AC (e.g. AC-004.11) and a test `rejectsWhenTheCallerNoLongerManagesTheProject_AC_004_11`.
2. **[MEDIUM][CODE]** Logging of ticket content on insert failure — observed in this evaluation's `mvn verify` output: `org.hibernate.orm.jdbc.batch` and `SqlExceptionHelper` log at ERROR `Batch entry 0 insert into ticket (...) values (... 'Login fails' ... 'Error 500' ... 'Bad token' ...)`. `backend/AGENTS.md` §9: "never log … ticket content or personal data". The `writer`/`description` columns can hold personal data.
   - Fix: add `logServerErrorDetail=false` to the JDBC URL (`application.yml:9`) and lower `org.hibernate.engine.jdbc.spi.SqlExceptionHelper` / `org.hibernate.orm.jdbc.batch` so parameters are not printed; check with `ImportConfirmIT.savingFailure…` that the log no longer contains row values.
3. **[MEDIUM][STRIDE-DoS]** `CsvImportServiceImpl.java:85` and `ImportConfirmServiceImpl.java:125` — `findExistingTicketIds` binds every Ticket ID in one `IN (...)`. A 10 MB file with many short rows (> 65,535 distinct IDs, e.g. ~140 bytes per row) exceeds PostgreSQL's bind-parameter limit → 500 instead of a preview, and the whole file is kept in memory and inserted in one transaction.
   - Fix: look up IDs in chunks (e.g. 1,000 per query) or with `ticket_id = ANY(:ids)` as one array parameter; consider a row-count cap with its own message (spec change).
4. **[MEDIUM][STRIDE-Tampering/Integrity]** `ImportConfirmServiceImpl.java:83-98` — the preview and its Valid rows are read outside the transaction and without a lock. Two Confirms of the same preview (double click) both proceed; the loser records a spurious **Failed** Import Batch and returns MSG33 (409 or 500) instead of 404 (design edge case 21). A remove-rows racing a Confirm can import a row the PM just removed.
   - Fix: load the preview inside the transaction with `@Lock(PESSIMISTIC_WRITE)` (`select … for update`), re-read the Valid rows there, and return 404 when the preview is gone.
5. **[LOW][CODE]** `backend/src/main/java/vn/tsolve/ticket/dto/RemoveRowsRequest.java:8` — `rowNumbers` has no upper size. Fix: `@Size(max = …)` (e.g. the preview's max row count).
6. **[LOW][CODE]** `CsvImportServiceImpl.java:72,89` — the client file name is stored unchecked in `VARCHAR(255)` (`002-ticket-import-schema.sql:63`); a longer name gives an unhandled DB error (500). Fix: reject or cut names over 255 characters (MSG27).
7. **[LOW][CODE]** `StubProjectAccess.java:17` is a plain `@Component` while the design says `@ConditionalOnMissingBean(ProjectAccess.class)`. When the real implementation lands the context fails with two `ProjectAccess` beans — fails closed (safe), but noisy. Fix: follow the design or delete the stub in the same change.
8. **[LOW][STRIDE-Info]** Unhandled exceptions (e.g. `DataIntegrityViolationException` on two concurrent uploads by one PM, `MissingServletRequestParameterException`) fall back to Spring Boot's default error body, not `ProblemDetail` with `code`. No stack trace is exposed (Boot default), so Low.

Critical/High findings make the related feature's review verdict fail: finding 1 → **csv-confirm** (task 9).

## Attempt 2 (2026-10-23)

Scope: commit range `d15e991..58e0a06` (repo `default`, branch `feat/KAN-31-import-ticket-csv`): `3905cdc` (csv-confirm fix) and `58e0a06` (task 14, preview-rows-access). Files: `ImportConfirmServiceImpl.java`, `ImportPreviewServiceImpl.java`, `ImportPreviewRepository.java`, `TicketsPublishedEvent.java`, `application.yml`, and the three test classes. Judged against the spec version approved as b3015b47.

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 2 |
| STRIDE | 0 | 0 | 0 | 1 |

### Checks run
- **Secrets:** a grep of the range's added lines for `password|secret|api_key|token|PRIVATE KEY` finds nothing.
- **Dependencies:** no dependency changed in the range (`pom.xml` untouched).
- **Dangerous patterns:** no native SQL, `Runtime.exec` or deserialization was added. The new `lockByIdAndOwner` is JPQL with bound parameters (`ImportPreviewRepository.java:19-21`).
- **Logs:** the `mvn verify` log of this run contains no ticket values (grep for `insert into ticket`, `Login fails`, `Error 500` and `Bad token` finds nothing). Failure lines carry only the exception type and SQLState.

### Earlier findings
| # | Finding (2026-10-22) | Status |
|---|---|---|
| 1 | [HIGH][EoP] Confirm did not re-check Project management | **Resolved.** The spec gained AC-004.11 and AC-003.4. Confirm checks `ImportConfirmServiceImpl.java:97-102` (MSG40, then 403 MSG08, with no Import Batch). The rows list, row detail and remove-rows check `ImportPreviewServiceImpl.java:84-92` (403 MSG08 after the owner check). Both are tested. |
| 2 | [MEDIUM] Ticket content in logs | **Resolved.** `application.yml:31-36` (loggers OFF) and the service's own log line (`ImportConfirmServiceImpl.java:126-131`), verified in this run's log. |
| 3 | [MEDIUM][DoS] `IN (...)` bind limit | **Knowingly deferred** (design E8). The NFR file size stays far below the limit, and the follow-up is to chunk `findExistingTicketIds`. Still open at `ImportConfirmServiceImpl.java:165` and `CsvImportServiceImpl.java:85`. |
| 4 | [MEDIUM][Tampering] Unlocked Confirm read | **Resolved** for a double Confirm (`PESSIMISTIC_WRITE` lock, Valid rows read again, 404 without a Failed batch; IT on PostgreSQL). **Partly resolved** for remove-rows racing a Confirm: new finding A. |
| 5 | [LOW] `RemoveRowsRequest.rowNumbers` unbounded | Open (not in the range). |
| 6 | [LOW] File name over 255 characters gives 500 | Open (not in the range). |
| 7 | [LOW] Stub `ProjectAccess` is a plain `@Component` | Open (not in the range). |
| 8 | [LOW] Some unhandled exceptions are not `ProblemDetail` | Open (not in the range). |

### New findings
A. **[LOW][STRIDE-Tampering]** `ImportPreviewServiceImpl.java:84-87`: `removeRows` does not take the preview lock that Confirm holds, so a remove-rows that commits while a Confirm is running can still have its row imported. The same PM has to act twice at once.
   - Fix: load the preview with `lockByIdAndOwner` in `removeRows`.
B. **[LOW][CODE]** `ImportConfirmServiceImpl.java:110`: every `DataIntegrityViolationException` is reported as `TicketIdTaken` (409), including violations that have nothing to do with Ticket IDs. That misstates the cause in the Failed batch.
   - Fix: give 409 only for constraint `uq_ticket_ticket_id`.
C. **[LOW][STRIDE-Repudiation/ops]** `application.yml:35-36`: `SqlExceptionHelper` is OFF application-wide, so SQL errors elsewhere are silent unless their caller logs them. This was accepted by design E5. Each new module should log its own SQL failures, without values.

No Critical or High findings remain. Nothing here blocks **csv-confirm** (task 9, attempt 2) or **preview-rows-access** (task 14, attempt 1).
