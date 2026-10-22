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
