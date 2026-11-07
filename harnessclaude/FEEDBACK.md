# HarnessClaude Feedback

Append-only log of framework friction captured during usage.

---

## 2026-10-22 — audit-workspace (`sdd.ts audit`)

**Type:** bug
**Context:** `audit` requires every artifact folder, but git does not keep empty folders, so a fresh clone fails with "missing". Adding `.gitkeep` to them then raised warnings (bad session-log / evaluation names, not in the registry).
**Suggestion:** Ship a `.gitkeep` in each required artifact folder, and have `filesUnder()` in `hooks/lib/audit.ts` skip `.gitkeep`. T-Solve applied both locally (KAN-53); `harnessclaude upgrade` will undo the `audit.ts` part until this ships.

## 2026-10-22 — update-tracking / commit (`sdd.ts status … implementing`)

**Type:** friction
**Context:** T-Solve uses one branch per Jira issue (`<type>/KAN-<n>-<module>-<desc>`) and a trailer for AI help. The harness assumes one branch per module: `sdd.ts status <module> implementing` records `feat/<module>` (`hooks/sdd.ts:230`), the commit skill commits on `feat/{module}` and says to add no AI attribution, and `audit` warns when the recorded branch is gone. The project rules in CLAUDE.md / AGENTS.md now override this in words only (KAN-55).
**Suggestion:** Let `manifest.yaml` set the branch pattern (or `sdd.ts status … implementing --branch <name>`), and use it in the commit skill and in `audit`; let the commit skill follow the project's commit-message rules (header, trailer).

## 2026-10-22 — `sdd.ts trace` cannot see JUnit tests (import-ticket-list, task 3)

- The trace test globs are `**/*.{test,spec}.*`, `**/test_*.py` and `**/*_test.*`. Java tests named `*Test.java` / `*IT.java` (the Maven Surefire/Failsafe defaults) are never scanned.
- The AC mention pattern is `/\bAC-\d{3}\.\d+\b/`. `backend/AGENTS.md` §2 makes test methods cite ids as `…_AC_001_3`, which is a legal Java identifier, while `AC-001.3` is not, so the pattern never matches.
- Effect: `trace` reports 0/N for a Java module even when every AC has a passing test, so the "trace must exit 0 before evaluating" gate cannot be met.
- Suggested fix: add `**/*Test.java` and `**/*IT.java` to the test globs, and accept `AC_\d{3}_\d+` as a spelling of `AC-\d{3}.\d+` (normalise before matching).

## 2026-10-22 — discover / decompose (`progress.json` `phase`)

**Type:** gap
**Context:** The discover skill says to set `phase` in `progress.json` to `decompose`, but no `sdd.ts` command changes `phase` and the rules forbid editing `progress.json` by hand, so T-Solve stays at `phase: init` after discover and decompose (KAN-64). Also: `frontmatterList` splits an inline `[a, b]` list on commas, so a brace glob like `src/{a,b}/**` in `paths` breaks silently.
**Suggestion:** Add `sdd.ts phase <phase>` (or set it from `sdd.ts status … decomposed`); document that `paths` must use `- item` lists or glob without braces.

## 2026-10-25 — trace / check (`sdd.ts trace`, `lib/spec.ts`)

**Type:** gap
**Context:** `TEST_GLOBS` in `hooks/sdd.ts` (`**/*.{test,spec}.*`, `**/test_*.py`, `**/*_test.*`) does not match Java's JUnit names (`FooTest.java`, `FooIT.java`), so `sdd.ts trace` finds no backend test of T-Solve. Also, `parseSpec` keeps only the first line of a list-item criterion: a criterion wrapped over several lines must have `SHALL` on its first line, and `sdd.ts diff` does not see changes in its sub-bullets (found while planning `platform`, KAN-196).
**Suggestion:** Add `**/*Test.java`, `**/*IT.java` (or make the globs configurable in `manifest.yaml`); join a criterion's continuation lines and sub-bullets before linting and diffing.

## 2026-10-25 — diff (`sdd.ts diff`)

**Type:** gap
**Context:** `sdd.ts diff` drops the text inside backticks from the criteria it prints (e.g. "with `code` `MSG119`" prints as "with  "), so changed criteria are hard to read at re-approval. Found while re-approving five modules (KAN-196).
**Suggestion:** Print the criterion text verbatim, backticks included.

## 2026-10-28 — trace for Java (`sdd.ts trace`, `lib/spec.ts`)

**Type:** gap
**Context:** Besides `TEST_GLOBS` (entry of 2026-10-25), `AC_MENTION` (`\bAC-\d{3}\.\d+\b`) only reads `AC-001.1`, while `backend/AGENTS.md` names Java test methods `…_AC_001_1` (a method name cannot hold `-` or `.`). So `sdd.ts trace be-foundation` reports 0/9 for a fully tested STORY-001 (be-foundation Task 1, KAN-25).
**Suggestion:** Also match `AC_\d{3}_\d+` and normalise it to `AC-001.1`, together with the Java globs.

## 2026-10-31 — spec approval from Codex (KAN-153)

**Type:** friction
**Context:** The user approved the concrete 17-criterion spec in chat, but the PreToolUse hook blocked `sdd.ts approve dashboard-audit` because Codex cannot display the hook confirmation prompt. The hook required the user to run the command themselves. The user did so; `sdd.ts diff dashboard-audit` confirmed the current spec was approved at 2026-10-31T07:28:12.270Z. No retry or approval-file edit was used.
**Suggestion:** Support a native, reviewable approval action for Codex while preserving the spec-hash gate.

## 2026-11-02 — spec approval from Codex (KAN-149)

**Type:** friction
**Context:** The user approved the 20-criterion triage frontend spec in chat, but the PreToolUse hook blocked `sdd.ts approve triage` because Codex cannot confirm through the hook prompt. The hook instructed the user to run the command in their own terminal. No retry or approval-file edit was attempted.
**Suggestion:** Provide a native approval prompt for Codex that records the approved spec hash, while keeping the same user confirmation requirement.

## 2026-11-04 — preserve Java trace customization during upgrade (PR #107)

**Type:** maintenance gap
**Context:** The user-applied Java trace correction is recorded in T-Solve commit
`21096b37c74f455a1f772d4391c1529f1072f7ac` (BUG-093). It recognizes Java Test/IT files and
underscore AC ids, with four passing regressions. Framework upgrades can replace these files;
the current globs still do not recognize the alternative `*Tests.java` naming convention.
The BE lead requested upstreaming this during PR #107 review. Protected hook files were not
edited in this follow-up, and the redundant proposed patch artifact was removed.

**Suggestion:** Integrate the Java support upstream, include `*Tests.java`, and preserve or
explicitly report local customization during upgrade. Until then, compare the applied commit
and rerun hook tests plus a Java STORY trace after every upgrade. This is a local feedback
record; no external upstream issue or message was sent by the agent.

## 2026-11-07 — Jira branch metadata during worker KAN-102

Project AGENTS.md requires one branch per Jira issue, so actual branch is feat/KAN-102-worker-tenant-context. sdd.ts status worker implementing recorded feat/worker. Statuses were changed only with sdd.ts; no hook or status field was edited to hide this mismatch. sdd.ts commit worker default correctly recorded the code SHA and printed the actual issue branch, but final verification of progress.json showed that its branch field still says feat/worker. Audit therefore adds a missing-module-branch warning after the commit. Suggested upstream improvement: persist the current issue branch at the implementing transition and commit recording when a project overrides the default module branch convention.
