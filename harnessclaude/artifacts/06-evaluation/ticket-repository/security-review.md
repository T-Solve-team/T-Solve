## Security Review — ticket-repository (KAN-45 Jira import, backend) — 2026-11-07

Scope: backend code in `1586bddd^..423b8f17` (tasks 2, 3, 4, 5, 6, 7, 11): `vn.tsolve.ticket.client`, `controller`
(`JiraImportController`, `ImportDraftController`, fill-solution in `ImportController`), `service/impl`
(`JiraImportServiceImpl`, `ImportPreviewServiceImpl`, `ImportConfirmServiceImpl`, `ImportDraftServiceImpl`,
`ImportBatchRecorder`, `StubProjectAccess`), entities, repositories, `010-ticket-jira-import.sql`, `application.yml`,
`.env.example`. Frontend code is out of scope (a parallel reviewer covers it).

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 3 |
| STRIDE | 0 | 0 | 0 | 3 |

**Result: no Critical or High finding. Security does not block any task.**

### Checks run
- **Secrets:** the high-confidence patterns of `references/patterns.md`, plus the Atlassian `ATATT3` prefix, over
  every added line in the range: 0 hits. The context patterns give one hit,
  `JiraRestIssueSourceTest.java: TOKEN = "test-token-value"`, a test placeholder. `.env.example` holds empty
  `JIRA_SITE_URL=`, `JIRA_CLOUD_ID=`, `JIRA_API_TOKEN=`, `TSOLVE_STUB_JIRA_KEYS=`.
- **Tracked env files:** `git ls-files | grep -E "(^|/)\.env"` → only `.env.example`. `.gitignore` has `.env`,
  `.env.*`, `!.env.example`.
- **Dependencies:** no `pom.xml` change in the range, so no new library (design: "No new library"). No Maven audit
  tool (OWASP dependency-check) is configured, and none was installed.
- **Dangerous patterns:** no native or concatenated SQL in `ticket/repository` (all JPQL or derived queries).
  The JQL is built by concatenation, but the project key must match `[A-Z][A-Z0-9_]*` first
  (`JiraRestIssueSource.java:43, :97`), the dates are `LocalDate`, and the JQL goes out as a URI template variable,
  so it is encoded. The test `refusesAProjectKeyThatCouldChangeTheQuery_AC_001_7` covers this.

### STRIDE
| Threat | Result |
|--------|--------|
| Spoofing | All new endpoints sit under `/api/**`, which requires authentication (`SecurityConfig`), with class-level `@PreAuthorize("hasRole('PROJECT_MANAGER')")`. Controller tests cover the 403 without the role. CSRF is on (`CookieCsrfTokenRepository`). The Jira credential is a server-side Bearer token sent only to the fixed host `https://api.atlassian.com`. |
| Tampering | Bodies are validated (`@Valid`, `@NotNull projectId`, `@NotEmpty`/`@Min(1)` row numbers); the 10,000-character Solution limit is enforced. The Unicode-blank gap in fill-solution is a correctness defect (Task 5 Issue 1), not a security one. No GET mutates. |
| Repudiation | A failed pull and a failed Finish record an Import Batch with actor, Project and time. A successful Finish records the batch and an approve decision per Ticket. The logs hold Project id, Jira key, HTTP status and exception type only (NFR 4): `JiraRestIssueSource.java:223-231`, `JiraImportServiceImpl.java:133`, `ImportConfirmServiceImpl.java:175-186`. `GlobalExceptionHandler` logs only the class name at ERROR. |
| Information disclosure | The token is never logged, returned or stored (NFR 2). `JiraUnavailableException` carries only "Jira answered HTTP n" or "Jira did not answer (Type)", and no cause is chained (tests assert `hasNoCause()` and that neither the message nor the captured log contains the token). The batch error is `JiraUnavailableException MSG50`. Preview and Draft ids are sequential, but every lookup is by `id AND owner`, so another user's id gives 404 (AC-002.8, AC-004.5). |
| Denial of service | Every Jira request has a 10 s connect and 30 s read timeout, with at most 2 retries of ≤ 5 s. The scan stops at 20 pages. Pages are capped at 100 rows. See Low 4 and Low 5. |
| Elevation of privilege | Pull, preview requests, fill-solution, save as draft and Finish re-check `managesActive` or `isActive` on every call. Draft delete and list check the owner only (Low 6). |

SSRF (OWASP A10): the backend never fetches `JIRA_SITE_URL`; it only uses it to build `/browse/{key}` links, and it
must be https (`JiraRestIssueSource.java:74-79`). No user-supplied URL is fetched.

### Findings
1. **[LOW][CODE]** `backend/src/main/java/vn/tsolve/ticket/client/JiraRestIssueSource.java:66`. `JIRA_CLOUD_ID` is
   concatenated into the gateway base URL unchecked, so a value with `/`, `?` or `#` changes the request path on
   `api.atlassian.com`. The value comes from the operator's environment.
   - Fix: accept only a UUID; otherwise treat the connection as not configured.
2. **[LOW][CODE]** `JiraRestIssueSource.java:67`. `JIRA_API_TOKEN` is not stripped. A trailing CR/LF from a
   hand-edited `.env` gives a 401 (MSG50), or an `IllegalArgumentException` from the HTTP client that escapes as a
   generic 500.
   - Fix: `apiToken.strip()`.
3. **[LOW][CODE]** `JiraRestIssueSource.java:183-191`. A malformed date from Jira escapes as a raw
   `DateTimeParseException`, which becomes a generic 500 with no Failed batch. No data leaks: the handler logs the
   class only, and the full exception only at DEBUG.
   - Fix: map it to `JiraUnavailableException` (Task 3 review Issue 1).
4. **[LOW][STRIDE-DoS]** `POST /api/v1/imports/jira` has no per-user rate limit, and a pull has no overall deadline.
   One PM who repeats pulls over a large range can use up the shared service account's Jira rate limit, so other
   PMs get MSG50 for a while. The 429 handling limits the damage.
   - Fix (later): a per-user concurrency limit of 1 on pull, or an overall pull deadline.
5. **[LOW][STRIDE-Info]** Drafts never expire (AC-004.6 by design), and they hold full snapshots of Jira issues,
   including comments that may contain personal data. Note for the data-retention policy; no change required by the
   spec.
6. **[LOW][STRIDE-EoP]** `ImportDraftServiceImpl.java:56-77`. `list` and `delete` do not re-check that the caller
   still manages the Draft's Project (NFR 3 says "every call"). The impact is nil: a user can only see or delete
   their own Draft. This is a judgment call in the Task 7 review.

No credential was found, so none needs to be rotated.
