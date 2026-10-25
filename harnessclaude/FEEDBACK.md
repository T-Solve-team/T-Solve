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

## 2026-10-22 — discover / decompose (`progress.json` `phase`)

**Type:** gap
**Context:** The discover skill says to set `phase` in `progress.json` to `decompose`, but no `sdd.ts` command changes `phase` and the rules forbid editing `progress.json` by hand, so T-Solve stays at `phase: init` after discover and decompose (KAN-64). Also: `frontmatterList` splits an inline `[a, b]` list on commas, so a brace glob like `src/{a,b}/**` in `paths` breaks silently.
**Suggestion:** Add `sdd.ts phase <phase>` (or set it from `sdd.ts status … decomposed`); document that `paths` must use `- item` lists or glob without braces.

## 2026-10-25 — trace / check (`sdd.ts trace`, `lib/spec.ts`)

**Type:** gap
**Context:** `TEST_GLOBS` in `hooks/sdd.ts` (`**/*.{test,spec}.*`, `**/test_*.py`, `**/*_test.*`) does not match Java's JUnit names (`FooTest.java`, `FooIT.java`), so `sdd.ts trace` finds no backend test of T-Solve. Also, `parseSpec` keeps only the first line of a list-item criterion: a criterion wrapped over several lines must have `SHALL` on its first line, and `sdd.ts diff` does not see changes in its sub-bullets (found while planning `platform`, KAN-196).
**Suggestion:** Add `**/*Test.java`, `**/*IT.java` (or make the globs configurable in `manifest.yaml`); join a criterion's continuation lines and sub-bullets before linting and diffing.
