---
module: wiki
feature: wiki-view-rule
task: 1
attempt: 2
date: 2026-11-14
iteration: 1
---

# Test report: wiki — Task 1: wiki-view-rule

The dispatcher told the reviewer not to run `mvn`, because the orchestrator runs a full Docker-backed `mvn verify` and gives its result.

| Suite | Result | Source |
|-------|--------|--------|
| `vn.tsolve.wiki.service.impl.WikiPortsTest` | 7 run, 0 failed | surefire-reports, 21:59 (attempt 1) |
| `vn.tsolve.triage.service.impl.SuggestionGeneratorTest` | 11 run, 0 failed | surefire-reports, 21:59 (attempt 1) |
| `vn.tsolve.triage.service.impl.SuggestionResultServiceImplTest` | 14 run, 0 failed | surefire-reports, 21:59 (attempt 1) |
| `vn.tsolve.wiki.WikiAccessIT` (now with the BUG_249 case) | pending: the orchestrator's Docker run | — |
| `mvn spotless:check` | pending: part of the same `mvn verify` | — |

Traceability (`bun harnessclaude/hooks/sdd.ts trace wiki STORY-001`): 11/11 acceptance criteria have a test.

The review verdict (pass) depends on `WikiAccessIT` and `mvn verify` being green.
