# Test report: ticket-repository — Task 18: manual-evidence-contract

**Commit:** default@ef484f0b · **Date:** 2026-11-14 · **Run by:** harnessclaude reviewer (independent subagent)

## Run by the reviewer
| Check | Result |
|-------|--------|
| `frontend: npm run typecheck` (`tsc -b --noEmit`) | pass, no errors |
| `frontend: npx vitest run src/features/ticket-import`, run 1 | 122/123: 1 failed after 206 s under load (flaky; name not captured) |
| `frontend: npx vitest run src/features/ticket-import`, run 2 | **123/123 pass** |
| `bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-006` | 14/22: AC-006.15…AC-006.22 are owned by Tasks 19 and 20 |

## Reported by the orchestrator (session log; not re-run here, as the dispatcher instructed)
- Backend unit tests Manual*, Ticket* and ImportDraft*, plus the ITs ManualTicketControllerIT, ImportConfirmIT, the
  review ITs and ReviewStorageIT: 0 failures.
- Frontend: format:check, lint, typecheck, vitest for ticket-import and pages (123/123) and build all pass;
  `generate:api` leaves no diff.

## Not run
- Full backend `mvn verify` and the Docker ITs (dispatcher: the orchestrator ran them).
