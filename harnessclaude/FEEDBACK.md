# HarnessClaude Feedback

Append-only log of framework friction captured during usage.

---

## 2026-10-20 — BUG-008: trace does not discover or recognize Java tests

In the approved ticket-review module, `mvn verify` ran 32 JUnit tests successfully, but `bun harnessclaude/hooks/sdd.ts trace ticket-review` reported 0/30 criteria with tests. `sdd.ts`'s TEST_GLOBS only discovers `*.test.*`, `*.spec.*`, `test_*.py` and `*_test.*`; it excludes conventional `*Test.java`. The spec library also recognizes canonical AC-001.1 mentions, while backend/AGENTS.md prescribes Java method names such as `behavior_AC_001_1`.

The backend tests and PostgreSQL evidence are retained. The official trace result is not represented as a pass, no gate has been bypassed, and the feature remains in-progress awaiting its commit checkpoint. Fixing framework discovery/ID normalization requires a separate authorized framework change; it is outside the approved review-module code paths. Frontend criteria are also still pending Task 2 regardless of the discovery defect.
