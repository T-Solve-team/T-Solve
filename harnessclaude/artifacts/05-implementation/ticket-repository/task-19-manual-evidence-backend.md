# Task 19: manual-evidence-backend

**Module:** ticket-repository · **Assignee:** Codex (gpt-6.1-sol, high) · **Status:** evaluating

## Session 2026-11-14
### Working on: manual-evidence-backend
- ManualTicketController: both POSTs multipart; `entry` taken as a String part (the generated client sends it without a part Content-Type) and parsed with ObjectMapper; `files` optional.
- ManualTicketServiceImpl: file rules (D19 extensions, 10 MB, kept + new ≤ 5) → 400 InvalidFieldsException on `files` (MSG34 / MSG72), after the 400/403/404 checks and before storing; files stored outside the transaction (TransactionTemplate), deleted again on DB failure; StorageUnavailableException 503 MSG06 when no FileStorage bean or a store fails; Save writes TicketEvidence rows (kept + new); Save as draft writes ImportPreviewEvidence rows, drops the rows left out of keepEvidenceIds and deletes their objects after commit; getDraft lists the Draft's Evidence. New EvidenceUpload DTO, EvidenceFiles helper.
- ImportDraftServiceImpl.delete of a Manual Draft deletes its objects after commit (best effort).
- application.yml multipart limits 20MB per file / 52MB per request; CSV keeps its code-enforced 10 MB (MSG28). Only CSV and manual entry take multipart.
- YAML descriptions mention MSG34, MSG72, 503 MSG06; client regenerated.
- Verify (orchestrator; Codex cannot run Maven): spotless:apply; unit Manual*/Ticket*/ImportDraft* 267/267, ITs ManualTicketControllerIT 19 + ImportConfirmIT 12 OK; MSG28 tests (CsvImportServiceImplTest, GlobalExceptionHandlerTest, ImportControllerTest) 67/67; frontend format:check, lint, typecheck OK, generate:api no diff.
- Commit: default@4aebbca2
