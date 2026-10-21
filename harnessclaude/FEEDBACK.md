# HarnessClaude Feedback

Append-only log of framework friction captured during usage.

---

## 2026-10-22 — audit-workspace (`sdd.ts audit`)

**Type:** bug
**Context:** `audit` requires every artifact folder, but git does not keep empty folders, so a fresh clone fails with "missing". Adding `.gitkeep` to them then raised warnings (bad session-log / evaluation names, not in the registry).
**Suggestion:** Ship a `.gitkeep` in each required artifact folder, and have `filesUnder()` in `hooks/lib/audit.ts` skip `.gitkeep`. T-Solve applied both locally (KAN-53); `harnessclaude upgrade` will undo the `audit.ts` part until this ships.
