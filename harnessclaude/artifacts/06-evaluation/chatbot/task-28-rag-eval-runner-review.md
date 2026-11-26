---
module: chatbot
feature: rag-eval-runner
task: 28
verdict: fail
attempt: 1
date: 2026-11-26
reviewer: harnessclaude-reviewer
iteration: 4
---

# Review: chatbot — Task 28: Offline retrieval evaluation (STORY-015)

Scope: the uncommitted working tree on `feat/KAN-463-chatbot-rag-eval`:
- `ChatbotRetrieval`, `chatbot/eval/{GoldFile,RagEvalReport,RagEvalRunner}`, `application-rag-eval.yml` and
  `rag-eval/gold.example.jsonl`, with their tests
- the edits to `ChatbotAnswerServiceImpl` and `ChatbotSource`
- the cross-module switches in `DevAccountSeeder`, `MockUserListSeeder`, `RegistryDataSourceConfig` and
  `EmbeddingCatchUpJob`

Checked against chatbot spec STORY-015 (AC-015.1–015.8), Requirement 12, the "Offline evaluation (STORY-015)"
contract, and Out of Scope. Note: progress.json still shows this feature as `pending`, not `evaluating`.

## Summary
Most of the feature is correct and well tested:
- **Ranking.** The merge moved into `ChatbotRetrieval` unchanged: same candidate order (tickets first, then Wiki), the
  same stable sort (similarity descending, ticket first on a tie), the same `limit` and numbering. The `remaining`
  check still runs between retrieval and ranking. `ask` and `askForProject` behave exactly as before.
- **Metrics.** They match AC-015.3 and AC-015.4:
  - Results are sorted by similarity descending, so the results at or above `minSimilarity` form a prefix. "First
    match at rank ≤ `sourceLimit` with similarity ≥ `minSimilarity`" is therefore the same as "a match among the
    sources the Chatbot keeps".
  - With `k ≥ sourceLimit` (enforced), the first `sourceLimit` results of the top-k merge equal the Chatbot's top
    `sourceLimit` merge.
  - The threshold is compared on the unrounded similarity. MRR uses 1/rank within `k`, with 0 when there is no match.
    `noSourceRate` counts a missing first result or one below `minSimilarity`.
  - All four scores are rounded half-up to 4 decimals. Groups are counted per `key=value`.
- **Failure handling.** Settings, `k ≥ sourceLimit`, every gold line, the Workspace and the Active user are all
  checked before the first embedding call. The report is written to a temp file and moved into place only after every
  line succeeds. Every failure exits 1, and only fixed reason strings are logged (the line number, never the text).
- **No chat model.** The runner has no `LlmClient` dependency.

**Blocking issue:** under the `rag-eval` profile, one scheduled Workspace-database writer is still on,
`PreviewCleanupJob`. AC-015.7 says the runner "SHALL write nothing to the Workspace database".

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | fail | AC-015.7 is not fully met: `PreviewCleanupJob` stays scheduled (Issue 1). AC-015.1–015.6 and 015.8 are met |
| Completeness | pass | 8/8 ACs have tests; all design files exist |
| Correctness | pass with findings | All tests pass. The AC-015.7 test checks YAML keys only, so it did not catch Issue 1 (Issue 4) |
| Integration | pass | The cross-module switches default to the old behaviour for normal runs (details below) |

**The cross-module switches (the dispatcher's question).** I read each change:
- `DevAccountSeeder` `@Profile("dev & !rag-eval")` and `MockUserListSeeder` `@Profile("identity-mock & !rag-eval")`:
  valid Spring profile expressions, and unchanged for every run without `rag-eval`.
- `RegistryDataSourceConfig:76-77`: `getProperty("tsolve.registry.liquibase.enabled", Boolean.class, true)` defaults
  to true, so normal and `worker` runs behave as before. The switch is needed: even with lazy initialization,
  Boot's database-initialization dependency makes the EntityManagerFactory create the `registryLiquibase` bean.
- `EmbeddingCatchUpJob` `@ConditionalOnProperty(..., matchIfMissing = true)`: on by default. Its only other
  references are `@MockitoBean` fields in two search ITs and a hand-built bean in its own test, so a missing bean
  breaks no injection.

**Other startup writers and jobs under `rag-eval`.** I checked every one:
- `ConversationRetentionJob` and `SessionExpiryPublisher`: their cron is `-` (but see Issue 3).
- The JobRunr registrations, `EmailOutboxConfiguration` / `OutboxOnlyConfiguration` (with their Liquibase) and
  `EmailOutboxLeaseCheck`: off with `tsolve.worker.*=false`.
- The `ApplicationReadyEvent` listeners (`ObjectFileStorage`, and the three registrations, which also need
  `worker.enabled`) never fire, because the runner calls `System.exit` before Boot publishes the ready event.
- `keycloakDiscoveryCheck` and `PlatformWorkspaceLookup`: lazy and read-only.
- `ReviewQueueEventServiceImpl` heartbeats: run only for an open SSE subscription.
- **Left on: `PreviewCleanupJob`** (Issue 1).

## Traceability
```
✓ AC-015.1  RagEvalRunnerTest.java
✓ AC-015.2  GoldFileTest.java
✓ AC-015.3  RagEvalReportTest.java
✓ AC-015.4  RagEvalReportTest.java
✓ AC-015.5  GoldFileTest.java, RagEvalRunnerTest.java
✓ AC-015.6  RagEvalRunnerTest.java
✓ AC-015.7  RagEvalRunnerTest.java
✓ AC-015.8  GoldFileTest.java
trace chatbot (STORY-015): 8/8
```

## Issues

1. **important — AC-015.7 — `PreviewCleanupJob` still deletes from the Workspace database during a `rag-eval` run.**
   `backend/src/main/java/vn/tsolve/ticket/service/impl/PreviewCleanupJob.java:22-47`;
   `backend/src/main/resources/application-rag-eval.yml` (no key for it).
   The job is an unconditional `@Component` that implements `SchedulingConfigurer`, and `TicketSchedulingConfig`
   turns on `@EnableScheduling` without any condition. `spring.main.lazy-initialization: true` does not stop it.
   `ScheduledAnnotationBeanPostProcessor.finishRegistration` calls `getBeansOfType(SchedulingConfigurer.class)`,
   which creates lazy singletons, and it registers the trigger on `ContextRefreshedEvent`, before the
   `ApplicationRunner` starts. It also cannot be turned off by configuration: `springCron("-")` throws
   `IllegalStateException`, so the start fails.
   *Failure scenario:* a run that is up at 01:00 Asia/Ho_Chi_Minh (started late at night, or slow on 50+ questions
   against a remote AI server) runs `deleteExpiredPreviews()`. It deletes every `import_preview` row older than 24 h
   from the evaluated database, the process's `DB_*` database, which design.md says is "a copy of the demo Workspace
   database". AC-015.7 is broken.
   *Fix (smallest):* in `PreviewCleanupJob.configureTasks`, skip the registration when the configured value is `-`
   (`Scheduled.CRON_DISABLED`), the same convention the other two crons use. Then add
   `IMPORT_PREVIEW_CLEANUP_CRON: "-"` to `application-rag-eval.yml` and the YAML assertion. A
   `@ConditionalOnProperty(..., matchIfMissing = true)` on the class, like `EmbeddingCatchUpJob`, also works. Log a
   `BUG-…` row in `docs/bug-log.md` and name the test with it. A stronger general guard is in Issue 4.

2. **minor — AC-015.1 — the runner embeds the untrimmed question, but the Chatbot embeds the trimmed one.**
   `backend/src/main/java/vn/tsolve/chatbot/eval/RagEvalRunner.java:139` calls
   `embeddings.embed(List.of(line.question()))`. In `ChatbotAnswerServiceImpl.java`, line 264
   (`String question = asked.trim();`) and lines 286/293 embed the trimmed question for a first web question
   (AC-009.5). `RagEvalRunnerTest.java:48,66,78` locks in the untrimmed behaviour.
   *Failure scenario:* a gold line `"question": " VPN báo lỗi 809 "` gets a different vector from the same question
   typed on 12.1, so its `rank` or similarity can differ slightly from what the Chatbot keeps. That defeats "the run
   measures exactly what the Chatbot keeps".
   *Fix:* embed `line.question().trim()` (use `trim()`, not `strip()`, to match the service) and change the test to
   expect the trimmed text.

3. **minor — AC-015.7 — the "write nothing" switches can be overridden by OS environment variables.**
   `backend/src/main/resources/application-rag-eval.yml:16-22`. In Spring's precedence, OS environment variables
   beat `application-rag-eval.yml`. `.env.example:171` sets `CHATBOT_CONVERSATION_CLEANUP_CRON=0 0 2 * * *`, and
   relaxed binding maps `TSOLVE_WORKER_ENABLED`, `TSOLVE_WORKER_WEB_JOBS_ENABLED` and
   `TSOLVE_WORKER_OUTBOX_ENABLED` onto `tsolve.worker.*`.
   *Failure scenario:* the run is started in a container launched with `env_file:` (as the staging compose does), or
   in a shell that exported `.env`. The retention job is armed for 02:00 and deletes conversations, or the worker,
   outbox and JobRunr beans start and write to the Jobs database. The documented `mvn spring-boot:run` path reads
   `.env` through `spring.config.import`, which I expect ranks below the profile file, so the documented run is
   probably safe. I did not confirm this by running.
   *Fix:* fail fast instead of trusting configuration. At the start of `evaluate()`, refuse to run (exit 1, before
   any AI call) when `ScheduledTaskHolder.getScheduledTasks()` is not empty, or when `tsolve.worker.enabled`,
   `web-jobs-enabled` or `outbox-enabled` resolves to true. This would also have caught Issue 1.

4. **minor — AC-015.7 test gap — no test starts the `rag-eval` profile.**
   `backend/src/test/java/vn/tsolve/chatbot/eval/RagEvalRunnerTest.java:189-202` asserts that YAML keys exist. It
   never builds a context with the profile, so it cannot show that no web server starts, that no scheduled task is
   registered, or that the context starts at all with lazy initialization. That is how Issue 1 got through.
   *Fix:* one IT with the existing Testcontainers Postgres, `@SpringBootTest(properties = ...)` and
   `@ActiveProfiles("rag-eval")`. Replace the runner's `exit` with a no-op bean, or start with an invalid
   `RAG_EVAL_FILE` and capture the exit code. Assert that the context is not a `WebServerApplicationContext` and
   that `ScheduledTaskHolder.getScheduledTasks()` is empty.

5. **minor — AC-015.2 / AC-015.5 — a UTF-8 BOM in the gold file fails line 1.**
   `backend/src/main/java/vn/tsolve/chatbot/eval/GoldFile.java:26,45`: `Files.readAllLines` keeps `﻿`, and
   Jackson rejects it. I confirmed this with Jackson 2.17.2: `readTree("﻿{...}")` throws
   `JsonParseException`.
   *Failure scenario:* a teammate saves the gold set from Windows PowerShell 5 (`Set-Content -Encoding UTF8`) or
   Notepad with a BOM. The run exits 1 with `line 1: invalid JSON` even though the line is valid. It fails loudly, so
   no wrong scores are written.
   *Fix:* strip a leading `﻿` from the first line before parsing, and add one case to `GoldFileTest`.

6. **minor — convention (AGENTS.md rules 2–3) — `ChatbotRetrieval` is a concrete class in `service/` that depends
   on `service/impl/`.**
   `backend/src/main/java/vn/tsolve/chatbot/answer/service/ChatbotRetrieval.java:9,14`. AGENTS.md says
   "Every service is an interface … with its implementation in `service/impl/`". Here, an API-package class imports
   the impl record `ChatbotSource`, which is now public with public factories (`ChatbotSource.java:14-58`). The
   design asked for exactly this (public `@Component`), so this is not blocking.
   *Fix (optional):* move `ChatbotRetrieval` to `answer/service/impl/` next to `ChatbotSource` (the runner is in the
   same module), or log the exception in `iteration-4/decisions.md`. The design named one method, `rank`; the code
   adds `rankProject` for `RetrievedTicketSolution`. That is fine, but design.md should say so.

7. **minor — process — three other modules' code changed outside Task 28's file list.**
   `identity/dev/DevAccountSeeder.java:21`, `identity/mock/MockUserListSeeder.java:14`,
   `platform/registry/RegistryDataSourceConfig.java:75-77` (a new property `tsolve.registry.liquibase.enabled`), and
   `search/embedding/service/impl/EmbeddingCatchUpJob.java:12-17` (a new property
   `tsolve.search.embedding.catch-up-enabled`). The design allows "the builder checks every other … startup writer
   and turns it off", but neither tasks.md nor decisions.md lists these files or the two new properties. The
   platform and search owners would not see them.
   *Fix:* add them to Task 28's **Files** list and one row to `iteration-4/decisions.md`, and name them in the PR
   body.

8. **minor — diagnostics — the generic failure paths drop the exception type.**
   `RagEvalRunner.java:108` logs `unexpected evaluation error` and `RagEvalRunner.java:126` logs
   `source retrieval failed`, with nothing else. An operator cannot tell a database outage from a Registry or schema
   problem (for example, Hibernate `validate` failing on a database copy that has not been migrated, since the
   profile turns Liquibase off).
   *Fix:* add `e.getClass().getName()`. The class name carries no question or source text, and this is the pattern
   `SessionExpiryPublisher` already uses.

## Assumptions made (open judgment calls)
- **Verdict is fail on Issue 1.** AC-015.7 clearly says "write nothing to the Workspace database". The risk is
  limited to a run that is up at 01:00 Vietnam time, but I judged against the criterion as written. If the user
  accepts that window for now, Issue 1 can become a logged bug and the verdict pass. That decision is the user's.
- I read "the question embedded as asked" (AC-015.1) as "without the classifier's rewrite, trimmed as the web path
  does". If the spec means byte-for-byte as written in the gold file, Issue 2 goes away.
- `"groups": null` is rejected as invalid. The spec says only "an optional `groups` object". I accepted the strict
  reading.
- AC-015.7 "write nothing" covers the Workspace database. Turning off the Registry Liquibase and the Jobs database
  goes further than the criterion requires; I did not count it against the feature.
- The run needs a Workspace known to the Registry (`REGISTRY_DB_URL` and `TSOLVE_SITE_ORIGIN_TEMPLATE` set) or the
  `dev` profile's fixed Workspaces. It also needs a database copy already migrated to `056`, because Liquibase is off
  and Hibernate validates. The spec does not ask for run docs; the PR body should state this.

## Test Report
See `task-28-rag-eval-runner-test-report.md`.
