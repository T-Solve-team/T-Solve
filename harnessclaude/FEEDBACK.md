# HarnessClaude Feedback

Append-only log of framework friction captured during usage.

---

## 2026-10-22 — `sdd.ts trace` cannot see JUnit tests (import-ticket-list, task 3)

- The trace test globs are `**/*.{test,spec}.*`, `**/test_*.py` and `**/*_test.*`. Java tests named `*Test.java` / `*IT.java` (the Maven Surefire/Failsafe defaults) are never scanned.
- The AC mention pattern is `/\bAC-\d{3}\.\d+\b/`. `backend/AGENTS.md` §2 makes test methods cite ids as `…_AC_001_3`, which is a legal Java identifier, while `AC-001.3` is not, so the pattern never matches.
- Effect: `trace` reports 0/N for a Java module even when every AC has a passing test, so the "trace must exit 0 before evaluating" gate cannot be met.
- Suggested fix: add `**/*Test.java` and `**/*IT.java` to the test globs, and accept `AC_\d{3}_\d+` as a spelling of `AC-\d{3}.\d+` (normalise before matching).
