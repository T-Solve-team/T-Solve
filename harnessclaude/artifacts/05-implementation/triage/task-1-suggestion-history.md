# Task 1: Suggestion History list and detail (KAN-149)

**Module:** triage
**Assignee:** Trần Hải Anh
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-11-02

### Working on: suggestion-history
- User confirmed the two remaining Hải Anh FE areas are Knowledge Dashboard and Suggestion History, and chose to include the 10.2 detail popup with the 10.1 list.
- Triage spec has two stories and 20 criteria. The user approved it in chat and recorded its hash with `sdd.ts approve triage` at 2026-11-02T12:58:28.358Z after the Codex PreToolUse hook required a terminal-run approval.
- The active plan has one feature/commit for Jira KAN-149, with test-first list and detail stages. The Jira branch `feature/KAN-149-triage-suggestion-history` was created from current `main`; the harness records its hardcoded `feat/triage` branch, while the team's Jira branch rule controls the actual work.
- `sdd.ts status triage planned` then `implementing`, and `sdd.ts feature triage suggestion-history pending` then `in-progress`, completed successfully. Code implementation and verification are in progress.

### Blockers
None for the approved triage frontend slice. Its real backend history/detail endpoints remain OpenAPI proposals.

### Next up
Implement contract, MSW, list/filter and detail popup with AC tests; run trace, full frontend verification and browser check. Report changed files, checks and proposed commit before committing.

## Session 2026-11-03

### Working on: suggestion-history
- Added the proposed OpenAPI list/detail contract, generated client, role- and Project-scoped MSW data/handlers, URL filters, paged list and read-only detail popup. Ticket sources remain counts, unavailable Wiki sources have MSG110 with no link, and Jira links require HTTPS.
- Test-first UI work: the placeholder failed AC-001.1 before implementation. The targeted triage run passed 34/34 tests across page, mock API and date parsing. The mock UUID validator bug is recorded and fixed as BUG-093; the regression test names that bug.
- `bun harnessclaude/hooks/sdd.ts trace triage`: 20/20 ACs have tests. Removed the planned triage folder from fe-foundation's old placeholder ownership path to disambiguate trace.
- `npm run format:check`: pass. `npm run lint`: pass with one existing warning in `features/tickets/components/TicketListFilters.tsx`. `npm run typecheck`: pass. `npm run build`: pass. Two `npm run generate:api` runs produced identical SHA-256 manifests (`018AFD70210C06B78E7007A309DB9D8A1D0EFE86CC5818403C307A0F999A38BC`).
- Browser CDP check at 1366 x 768 passed the list, full suggestion detail, no-suggestion detail and `answer=NONE` plus paired date filter. Four temporary captures were visually inspected and removed after the check. The FormModal kit visibly displays both Cancel and Close actions; the Close action was exercised by AC-002.6.
- The default full `npm run test` run showed failures in ReviewTicketDetail and UserStatus under high worker load; a 2-worker full run later showed Chatbot failures and was stopped because it was taking over ten minutes. Selected ReviewTicketDetail and Chatbot tests passed in isolation. `UserStatusPage.test.tsx` AC-009.1/6/7/8 still timed out at its 15-second test limit in isolation, and passed with `--testTimeout=60000`; recorded as open BUG-094. The required default full suite is therefore not passing on this workstation.
- No code or harness commit was made. The feature remains `in-progress` until the full verification gate and user review are resolved.

### Next up
Review the KAN-149 diff and test evidence with the user. Resolve the unrelated full-suite timing failure under its own issue or obtain an explicit process decision, then commit one KAN-149 code feature and its harness record. Dashboard code remains locked until its updated spec approval is recorded by the user-run `sdd.ts approve dashboard-audit` command.

## Session 2026-11-03 — commit handoff

### Working on: suggestion-history
- The user explicitly asked to fix only Trần Hải Anh's bugs and commit KAN-149 without changing other owners' bugs. BUG-093 is fixed and staged in the code commit; BUG-094 remains logged but unstaged and untouched in User Status code/tests.
- Fresh pre-commit check: 34/34 targeted Triage tests passed; `sdd.ts trace triage` remained 20/20; staged diff had no whitespace errors and the staged content secret-pattern scan found no hits. The hook blocked an additional sensitive-filename regex scan because the command text itself contained `.env`; no sensitive file appeared in the staged filename list, which was inspected directly. The blocked scan was not retried.
- Code commit: `default@4440072cedbf7081edd6274ae82b89dcd67e221e` (`feat(triage): add suggestion history and detail (KAN-149)`). This includes only KAN-149 FE/OpenAPI/generated code and the BUG-093 row, with no Dashboard or BUG-094 changes.
- `sdd.ts commit triage default` recorded the SHA. `sdd.ts feature triage suggestion-history evaluating` moved the feature to evaluation. The default full FE test command still has the documented unrelated BUG-094 timing failure; the user's explicit direction is the exception for this commit.

### Next up
Commit the triage HarnessClaude records separately, then begin KAN-151 on its own Jira branch. No push or merge was requested.
