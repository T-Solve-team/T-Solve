---
module: workspace
feature: workspace-store
task: 1
verdict: pass
attempt: 1
date: 2026-11-12
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 1: Workspace table, entity and token cipher

## Summary
Commit `d9b87ae` (default repo, monorepo). It adds changeset 027, which renames the legacy table and creates the one-row
`workspace` table. It also adds the `WorkspaceInfo` entity and repository, `TokenCipher` (AES-256-GCM), the
`tsolve.workspace.secret-key` setting, the test key and `.env.example`. Every criterion this task owns (AC-009.5,
AC-009.6, AC-009.7) is implemented, and each has tests that check the behaviour. The supporting AC-003.1 and AC-003.11
tests check the schema and the singleton guard. No CRITICAL or IMPORTANT findings. Verdict: **pass**.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AES/GCM/NoPadding with a 128-bit tag. Each value gets a fresh 12-byte `SecureRandom` nonce. AAD = workspace UUID as 16 big-endian bytes. Stored as `v1:` + Base64(nonce‖ct‖tag). Key read from `tsolve.workspace.secret-key` → `${WORKSPACE_SECRET_KEY}` with no default. A missing key, a non-Base64 key or a key that is not 32 bytes throws `IllegalStateException("WORKSPACE_SECRET_KEY must be …")` with no cause and no value, so the context fails to start. The entity `toString()` shows only the id, and `@JsonIgnore` is on the token getter. |
| Completeness | pass | Stage 1 table below: no MISSING. Nothing EXTRA beyond the design's Data Model. `.env.example` documents the key and how to generate it (spec DoD). |
| Correctness | pass | Orchestrator's `mvn -B verify`: 1383 unit + 439 IT, 0 failures, BUILD SUCCESS. Includes TokenCipherTest 27, WorkspaceChangelogTest 1, WorkspaceInfoRepositoryIT 6. Not re-run here (brief's low-RAM limit). |
| Integration | pass | No other entity or SQL in `src/main` uses the old `workspace` table (`grep` ran clean). The legacy FKs of `resolved_ticket_snapshot` and `solution` follow the rename (IT `preservesLegacyForeignKeysAndRows`). The rollback restores them (IT `rollsBackAndReappliesWorkspaceMigration`). The new PK is named `pk_workspace_info`, so it cannot clash with the legacy `workspace_pkey`. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-009.5 AES-256-GCM, fresh 96-bit nonce, Workspace id as AAD | PASS | `TokenCipher.java:41-55,76-83`. `TokenCipherTest` "AC-009.5 encrypts…" decrypts with an independent JCE cipher and the same AAD. "AC-009.5 generates a fresh nonce…" checks the nonce. "AC-009.5 refuses ciphertext copied to another Workspace" checks the AAD binding. Tampering at bytes 0, 12 and the last byte is rejected, and so is a wrong key. |
| 2 | AC-009.6 missing / non-Base64 / not 32 bytes → startup fails, names setting not value | PASS | `TokenCipher.java:26-38`. `ApplicationContextRunner` tests load the real `application.yml` with the system environment removed. They cover a missing key, `""`, `" "`, non-Base64, an unresolved placeholder, and 16/24/31/33-byte keys. Each test asserts `hasFailed()`, that the stack trace contains `WORKSPACE_SECRET_KEY`, and that neither the trace nor the captured output contains the value. |
| 3 | AC-009.7 token not in response, log, audit, exception message, `toString()` | PASS (for this task's types) | `WorkspaceInfo.toString()` → `WorkspaceInfo[id=…]`. `@JsonIgnore` on `getServiceAccountApiToken`. Cipher exceptions carry fixed messages and no cause (`hasNoCause()` asserted). Tests: "AC-009.7 entity and cipher diagnostics and entity JSON hide secret values", "…decryption diagnostics never expose…", "…encryption failures never expose…". Responses and audit entries are Tasks 4 and 5. |
| 4 | AC-003.1 (support: schema, `workspace_id`, `sign_in_provider` GOOGLE, encrypted token stored) | PASS | `WorkspaceInfoRepositoryIT` "AC-003.1 stores the bound Workspace id…", "…schema uses the specified columns…", the changelog/rollback tests, and tenant B unaffected. |
| 5 | AC-003.11 (support: one row only) | PASS | IT "singleton constraint refuses a second row even with a different Workspace id"; `false`/`null` cannot bypass it (CHECK + NOT NULL). |

## Traceability
```
  ✓ AC-009.1  backend/src/test/java/vn/tsolve/workspace/jira/JiraServiceAccountCheckTest.java
  ✓ AC-009.2  backend/src/test/java/vn/tsolve/workspace/jira/JiraServiceAccountCheckTest.java
  ✓ AC-009.3  backend/src/test/java/vn/tsolve/workspace/site/InterimJiraSiteLinkerTest.java, backend/src/test/java/vn/tsolve/workspace/site/JiraSiteRuleTest.java
  ✓ AC-009.4  backend/src/test/java/vn/tsolve/workspace/site/InterimJiraSiteLinkerTest.java
  ✓ AC-009.5  backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java
  ✓ AC-009.6  backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java
  ✓ AC-009.7  backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java
trace workspace (STORY-009): 7/7 acceptance criteria have a test
```
This task owns AC-009.5..AC-009.7. AC-003.1 and AC-003.11 also have Task 1 tests in `WorkspaceInfoRepositoryIT` and
`WorkspaceChangelogTest` (`trace workspace STORY-003`: 13/13).

## Issues
No CRITICAL or IMPORTANT findings.

Informational (does not block):
1. `backend/src/main/java/vn/tsolve/workspace/crypto/TokenCipher.java:35,51,69` — catches `NullPointerException` to
   turn null input into the sanitised error. It works, but it hides programming errors as "decryption failed".
   Fix: check `Objects.isNull(...)` explicitly and throw the same `IllegalStateException`.
2. `backend/src/main/resources/db/changelog/changes/027-workspace-info.sql:7-18` — the new tenant table has no
   `DEFAULT gen_random_uuid()` and no timestamp columns, so it does not fully follow the tenant-table convention
   (`backend/AGENTS.md` §7, platform AC-001.20). `legacy_workspace` is not on the AC-001.21 allow-list. Both are
   deliberate: the key is the bound Workspace id, and the spec lists the AC-001.20 change request for platform. Fix:
   none here. When the platform guard test lands, add `workspace` (key rule) and `legacy_workspace` to the allow-list
   or the change request.
3. `backend/src/test/java/vn/tsolve/workspace/repository/WorkspaceInfoRepositoryIT.java:31-48` — its own
   `@TestPropertySource` set builds an extra Spring context (AGENTS.md §8, "Reuse the Spring context"). Fix: reuse the
   shared IT base or property set that the identity ITs use.

## Assumptions made
- Task 1 is judged against AC-009.5..AC-009.7 (tasks.md). AC-003.1 and AC-003.11 are only the storage part here. Their
  API behaviour belongs to Task 4.
- The schema's deviation from platform AC-001.20 (no UUID default, no timestamps) is accepted. The spec records it as a
  change request to platform. Open judgment call for the user: accept it, or add timestamp columns now.

## Test Report
See `task-1-workspace-store-test-report.md`. The orchestrator ran the full suite: `mvn -B verify` (JDK 21 JBR), 1383 unit
+ 439 IT, 0 failures, BUILD SUCCESS. The reviewer ran `sdd.ts trace`: STORY-009 7/7, STORY-003 13/13.
