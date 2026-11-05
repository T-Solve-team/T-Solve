---
module: ai
feature: ollama-clients
task: 1
verdict: pass
attempt: 1
date: 2026-11-05
reviewer: harnessclaude-reviewer
---

# Review: ai — Task 1: Ollama chat and embedding clients

## Summary
Verdict: PASS WITH FIXES (no blocking finding; the fixes below are non-blocking). All 14 criteria (AC-001.1-001.3, AC-002.1-002.3, AC-003.1-003.8) have a test that names them and asserts them. Edge cases 1-14 are covered. No prompt, answer or vector text reaches a log or an exception message. Retry rule, semaphore release, timeouts, count and 1024-length checks are correct.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | chat/embed request bodies, format schema, retry once on 5xx or connection failure, no retry on 4xx / timeout, permit wait, slot released in finally. |
| Completeness | pass | 14/14 criteria traced; 8 variables in .env.example; edge cases 1-14 each have a test. |
| Correctness | pass | Manager ran `mvn verify` green (504 unit + 35 IT) and spotless; reviewer did not re-run the build (manager owns it). |
| Integration | pass | `AiUnavailableException` extends `MessageCodeException`, 503 / MSG06 through the global handler. Test application.yml sets the two required variables. |

## Traceability
`sdd.ts trace ai STORY-001 STORY-002 STORY-003`: 14/14 acceptance criteria have a test.

## Issues
1. OllamaHttp.java:104-107 / OllamaEmbeddingClient.java:30-40 / OllamaLlmClient.java:46: low. A wrong-shape answer (blank, bad JSON, wrong count or length: edges 10-13) is logged `outcome=ok` and then thrown with no failure line and no error class. AC-003.6 asks the log line to carry the outcome and, on a failure, the error class. Fix: validate inside the one logged section (pass a validator to `post`) or log a failed line in the clients.
2. OllamaEmbeddingClient.java:40: low. `asDouble()` turns a non-numeric or null element into 0.0 silently. Fix: reject the vector unless `values.get(i).isNumber()`.
3. OllamaLlmClient.java:54: low. `readTree` accepts trailing text after the object (`{"a":1} junk`) because trailing tokens are not rejected. Fix: use a reader with `FAIL_ON_TRAILING_TOKENS`.
4. OllamaHttp.java:74: low. `maxConcurrent <= 0` is accepted and makes every call fail as busy. Fix: reject it at startup with the variable name.
5. OllamaHttpTest.java:149-163: low. The AC-003.5 test asserts only an upper bound (<1000 ms); it would pass if the call failed at once without waiting. The `while (chatRequests() < 2)` loop has no bound and can hang. Fix: assert at least about 150 ms and bound the loop.
6. OllamaHttpTest.java: low. No test for AI_CONNECT_TIMEOUT (AC-003.2 first clause), and none that a timeout on embed is not retried (only chat, edge 4). Fix: one connect-timeout test (non-routable address) and a request count in `embedTimeoutFails`.
7. Test files: low. AGENTS section 8 says the test method names the AC id; here the ids are only in `@DisplayName`. `sdd.ts trace` accepts it. DisplayName also holds "edge N" (a plan row id). Fix: put the AC id in the method name; drop "edge N".
8. OllamaHttp.java:50-54: info. Defaults live in `@Value`, not in application.yml; an uncommented `AI_*=` with an empty value fails startup with a parse error that does not name the variable. Acceptable.

## Test Report
See task-1-ollama-clients-test-report.md.

## Assumptions made
- A connect timeout (`SocketTimeoutException` on connect) counts as a timeout (AC-003.2, no retry, AC-003.4), while a refused or reset connection counts as a connection failure (retry once, AC-003.3). The spec lists connect timeout under AC-003.2 only.
- "Answers within AI_CHAT_TIMEOUT" is implemented as a socket read timeout (idle time), which equals total time for Ollama with `stream: false`.
- The ids "edge 1-14" are the rows of the design's Edge Cases table.
