---
module: ticket-repository
feature: manual-entry-schema
task: 15
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Test Report: ticket-repository — Task 15: Schema and upload limits for manual entry

Shared run (commands, totals, frontend timeout analysis):
`harnessclaude/artifacts/06-evaluation/ticket-repository/task-14-manual-entry-contract-test-report.md`.
Backend `mvn -B -Dapi.version=1.44 clean verify`: BUILD SUCCESS, 1725 unit and 555 integration tests, 0 failures,
spotless passes.

## Task 15 tests

| Test | Result |
|------|--------|
| `ManualEntryTicketTest.manualTicketIsPublishedWithItsWriter_AC_006_1` | pass |
| `ManualEntryTicketTest.writerNameLongerThanTheColumnIsCutTo100_AC_006_1` | pass |
| `ManualEntryTicketTest.evidenceKeepsTheStorageKeyNameTypeAndSize_AC_006_2` | pass |
| `ManualEntryTicketTest.manualDraftIsADraftOfSourceManualFromItsFirstSave_AC_007_1` | pass |
| `ManualEntryTicketTest.manualDraftEvidenceListIsReplacedOnEachSave_AC_007_5` | pass |
| Changeset 031 applied by Liquibase in every Testcontainers IT (`ManualEntryIT` 12/12 among them) | pass |
| `ReviewStorageIT` (changelog filter extended with `031-ticket-manual-entry`) | pass |

`ManualEntryIT.fiveFilesOf10MbEachAreAccepted_AC_006_7` (edge 26) calls the service directly. It shows that the
service accepts 5 × 10 MB. It does not go through the servlet multipart limits.

## Not covered
- Caddy body limits (edge 29): no automated check, and no Caddy binary was available to run one. Reviewed by reading
  `infra/caddy/Caddyfile:69-90`.
- The servlet multipart limits of `application.yml:39-40` are not run by any test (no MockMvc or HTTP test sends an
  11 MB+ part).
