# HarnessClaude Feedback

Append-only log of framework friction captured during usage.

---

## 2026-10-20 — BUG-008: trace does not discover or recognize Java tests

In the approved ticket-review module, `mvn verify` ran 32 JUnit tests successfully, but `bun harnessclaude/hooks/sdd.ts trace ticket-review` reported 0/30 criteria with tests. `sdd.ts`'s TEST_GLOBS only discovers `*.test.*`, `*.spec.*`, `test_*.py` and `*_test.*`; it excludes conventional `*Test.java`. The spec library also recognizes canonical AC-001.1 mentions, while backend/AGENTS.md prescribes Java method names such as `behavior_AC_001_1`.

The backend tests and PostgreSQL evidence are retained. The official trace result is not represented as a pass, no gate has been bypassed, and the feature remains in-progress awaiting its commit checkpoint. Fixing framework discovery/ID normalization requires a separate authorized framework change; it is outside the approved review-module code paths. Frontend criteria are also still pending Task 2 regardless of the discovery defect.
## 2026-10-22 — audit-workspace (`sdd.ts audit`)

**Type:** bug
**Context:** `audit` requires every artifact folder, but git does not keep empty folders, so a fresh clone fails with "missing". Adding `.gitkeep` to them then raised warnings (bad session-log / evaluation names, not in the registry).
**Suggestion:** Ship a `.gitkeep` in each required artifact folder, and have `filesUnder()` in `hooks/lib/audit.ts` skip `.gitkeep`. T-Solve applied both locally (KAN-53); `harnessclaude upgrade` will undo the `audit.ts` part until this ships.
