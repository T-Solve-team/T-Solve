# Task 4: Generate the AI Solution Suggestion — KAN-47

**Module:** triage
**Assignee:** Trần Hải Anh
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-08

### Working on: suggestion-generation
- Re-planned before code (spec change 08/11, approved): `main` already runs the suggestion job (tracker STORY-011,
  KAN-22), so triage implements the tracker's port `SuggestionSearch` instead of its own job; the planned tracker
  Task 14 (`IssueRecordedEvent`, issue text columns) was dropped on rebase.
- `SuggestionGenerator implements SuggestionSearch`: blank summary → empty (AC-005.13); embed summary + blank line +
  description, cap 8000 (AC-005.3); tickets via `nearestInProjectDepartment` and Wiki via `WikiSourceRetrieval`, 5
  each at the threshold, merged stable by similarity, keep 5 (AC-005.4); none → empty (AC-005.7); prompt with data in
  `<issue>`/`<solution>`/`<wiki>` tags, escaped, tickets as numbered sections only, Wiki content ≤ 2000 (AC-005.6);
  blank answer → `IllegalStateException("Blank suggested solution")` (AC-005.8).
- `TriageProperties` (`tsolve.triage.min-similarity` ← `SUGGESTION_MIN_SIMILARITY`, default 0.7; 0..1 and NaN checked
  in the compact constructor) in `common/config` (AGENTS §1, not `triage/config` as first planned).
- `WikiSourceRetrieval`, `WikiSource`, stand-in `NoWikiSources` (bean name `triageNoWikiSources`: the chatbot module
  has a class with the same simple name, which first broke every Spring context).
- Deleted the tracker stand-in `StubSuggestionSearch`, its test, `tsolve.stub.suggestion` and `TSOLVE_STUB_SUGGESTION`
  (AC-005.14, user 08/11).
- Commit: default@e60a93b on `feat/KAN-47-triage-ai-suggestion`.

### Verification
- `bun harnessclaude/hooks/sdd.ts trace triage STORY-005`: 10/10 acceptance criteria have a test.
- `mvn verify` (JBR 21): 1012 unit + 272 IT, 0 failures; Spotless check passed.

### Blockers
none

### Next up
Independent review of `suggestion-generation`. Open with the user: align the tracker spec/design (still name
`search` and `StubSuggestionSearch`) and tell Bình the stub is gone.
