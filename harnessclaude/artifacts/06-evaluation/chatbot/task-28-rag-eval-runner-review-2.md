---
module: chatbot
feature: rag-eval-runner
task: 28
verdict: pass
attempt: 2
date: 2026-11-26
reviewer: harnessclaude-reviewer
iteration: 4
---

# Review: chatbot — Task 28: Offline retrieval evaluation (STORY-015), attempt 2

Scope: commit `58ea4910` on `feat/KAN-463-chatbot-rag-eval`. The fixes after review 1 are folded into this commit;
the working tree has no other backend changes. Checked against chatbot spec STORY-015 (AC-015.1–015.8), the
"Offline evaluation (STORY-015)" contract, design.md "## RAG measurement" and `backend/AGENTS.md`.

Verdict: **PASS WITH FINDINGS**. The frontmatter says `pass` because the template allows only pass or fail. The
blocking issue from review 1 (AC-015.7, `PreviewCleanupJob`) is fixed, and normal runs are unchanged. A new sweep
found no other bean that writes at startup or on a schedule under `rag-eval`. The findings left are all minor.

## Summary

**Fix 1: `PreviewCleanupJob` is off under `rag-eval`. Correct.**
- `PreviewCleanupJob.java:24-30` adds `@ConditionalOnProperty(prefix = "tsolve.ticket.preview-cleanup",
  name = "enabled", havingValue = "true", matchIfMissing = true)`. `application-rag-eval.yml:13-15` sets it to
  `false`, and `RagEvalRunnerTest.java:203` checks the key.
- Nothing injects it, so removing the bean breaks no context. The only references are its own javadoc mention in
  `TicketSchedulingConfig.java:6` and `PreviewCleanupJobTest`, which builds the job by hand. No `ObjectProvider`,
  `@Autowired`, `@MockitoBean` or IT field refers to it.
- `@EnableScheduling` on `TicketSchedulingConfig` still works with no `SchedulingConfigurer` bean.
- Normal runs are unchanged. The key is absent in `application.yml`, `.env.example` and every other profile, so
  `matchIfMissing = true` creates the bean. The condition report in the surefire log confirms it in every test
  context that loaded: `PreviewCleanupJob matched: @ConditionalOnProperty … matched` (14 times).

**Fix 2: the runner embeds the stripped question. Correct in intent, with one minor gap.**
- `RagEvalRunner.java:139` embeds `line.question().strip()`, and the tests expect `QUESTION.strip()`.
- The web path uses `trim()` (`ChatbotAnswerServiceImpl.java:264`), and the two methods differ (Issue 1).

**Fix 3: `GoldFile` strips a leading UTF-8 BOM. Correct.**
- `GoldFile.java:30-33` reads the file as UTF-8 (`Files.readAllLines`). It drops a literal U+FEFF (bytes
  `EF BB BF`) from line 1 only, before the blank check and the parse.
- `CsvTicketParser.java:67` already uses the same literal-BOM pattern, and the Boot parent sets the source encoding
  to UTF-8.
- `GoldFileTest.acceptsByteOrderMark_AC_015_2` (`:38-42`) covers it. A BOM on any other line still fails that line,
  which is correct.

**Fix 4: decisions are logged.** `iteration-4/decisions.md` has three new rows: the off switches (with the
env-override warning), the stripped question, and the BOM.

**Other startup or scheduled writers under `rag-eval`.** I grepped all of `src/main/java` again for `@Scheduled`,
`SchedulingConfigurer`, `@EnableScheduling`, `ApplicationRunner`, `CommandLineRunner`,
`@EventListener`/`@TransactionalEventListener`, `ApplicationReadyEvent`, `ContextRefreshedEvent`, `SmartLifecycle`,
`@PostConstruct`, `InitializingBean`, `SmartInitializingSingleton`, `scheduleRecurrently`, executors and threads,
`SpringLiquibase`, Redis, and bucket creation. Each hit:

| Bean | What it would write | Why it does nothing under `rag-eval` |
|---|---|---|
| `PreviewCleanupJob` | deletes `import_preview` rows | bean absent (Fix 1) |
| `EmbeddingCatchUpJob` (`@Scheduled`) | embeddings | bean absent (`catch-up-enabled: false`) |
| `ConversationRetentionJob` (`@Scheduled`) | deletes conversations | cron `-`, so disabled |
| `SessionExpiryPublisher` (`@Scheduled`) | Redis sweep, events | cron `-`, so disabled |
| `DevAccountSeeder`, `MockUserListSeeder` (`ApplicationRunner`) | seed accounts | `@Profile("… & !rag-eval")` |
| Boot `liquibase`, `registryLiquibase` | Workspace / Registry schema | `spring.liquibase.enabled: false`; `setShouldRun(… && tsolve.registry.liquibase.enabled)`, which defaults to true |
| `EmailOutboxConfiguration` / `OutboxOnlyConfiguration` (with their Liquibase), `EmailOutboxLeaseCheck` (`@PostConstruct`, read-only), `WorkerConfiguration` → `JobRunrRuntime` (`SmartLifecycle`) | Jobs database | `tsolve.worker.enabled` / `outbox-enabled: false` |
| `WebJobsConfiguration` (JobRunr created on the first job) | Jobs database | `web-jobs-enabled: false` |
| `EmailOutboxRegistration`, `ReviewExpiryRegistration`, `DraftReminderRegistration` (`ApplicationReadyEvent` → `scheduleRecurrently`) | JobRunr recurring jobs | `worker.enabled: false`, and the ready event never fires (below) |
| `ObjectFileStorage` (`ApplicationReadyEvent`) | object storage | the ready event never fires: Boot calls runners before `listeners.ready`, and the runner calls `System.exit` inside `run` |
| `PlatformWorkspaceLookup` (`SmartInitializingSingleton`), `keycloakDiscoveryCheck` (`InitializingBean`) | none (a config check / an HTTP read) | read-only, and lazy |
| `InProcessSuggestionJobQueue`, `JiraAppJobs`, `JiraAppJobCompletions`, `ReviewQueueEventServiceImpl` (threads and executors) | — | only on a request or an SSE subscription; no web server runs |
| `TicketsPublishedListener`, `ReviewNotificationEnqueuer` (`@TransactionalEventListener`) | — | only after a committed write, and the run makes none |
| Hibernate | schema | `ddl-auto: validate` |

The run path itself is read-only:
- `AccountLookupServiceImpl.findActive` and `SolutionRetrievalImpl.nearest` are `@Transactional(readOnly = true)`.
- `WikiSourcesImpl` is `@Transactional(readOnly = true)` on the class.
- `OllamaEmbeddingClient` and `ThreadLocalTenantContext` touch no database.

There is no Spring Session, Quartz or Flyway dependency, and JobRunr is the core jar with no auto-configuration.

**Precedence check (new evidence on review 1's Issue 3).** I ran a minimal Boot 3.3.5 app on the backend's runtime
classpath, with the same `spring.config.import: optional:file:.env[.properties],…` as `application.yml`, a `.env` that
holds `.env.example`'s `CHATBOT_CONVERSATION_CLEANUP_CRON=0 0 2 * * *`, and an `application-rag-eval.yml` with `"-"`.
- Property-source order: `application-rag-eval.yml` > `.env` > `application.yml`.
- The resolved value was `-`.
- So the documented `mvn spring-boot:run` path, which reads `.env`, is safe. Only real OS environment variables
  (for example a container `env_file:`) override the profile. That is the accepted, logged risk.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-015.1–015.8 met. AC-015.7 is now met: no scheduled or startup writer is left on (sweep above) |
| Completeness | pass | 8/8 ACs have a named test; the review-1 fixes have tests (BOM, stripped question, YAML key) |
| Correctness | pass | Targeted 91/91 pass; full surefire 3279 run, 0 failures, 186 errors, all from Docker not running (below), the same count as review 1 plus the one new test; `spotless:check` passes |
| Integration | pass | The new conditional is on by default; nothing injects `PreviewCleanupJob`; the other cross-module switches are unchanged from review 1 |

## Traceability
```
✓ AC-015.1  backend/src/test/java/vn/tsolve/chatbot/eval/RagEvalRunnerTest.java
✓ AC-015.2  backend/src/test/java/vn/tsolve/chatbot/eval/GoldFileTest.java
✓ AC-015.3  backend/src/test/java/vn/tsolve/chatbot/eval/RagEvalReportTest.java
✓ AC-015.4  backend/src/test/java/vn/tsolve/chatbot/eval/RagEvalReportTest.java
✓ AC-015.5  backend/src/test/java/vn/tsolve/chatbot/eval/GoldFileTest.java, backend/src/test/java/vn/tsolve/chatbot/eval/RagEvalRunnerTest.java
✓ AC-015.6  backend/src/test/java/vn/tsolve/chatbot/eval/RagEvalRunnerTest.java
✓ AC-015.7  backend/src/test/java/vn/tsolve/chatbot/eval/RagEvalRunnerTest.java
✓ AC-015.8  backend/src/test/java/vn/tsolve/chatbot/eval/GoldFileTest.java
trace chatbot (STORY-015): 8/8 acceptance criteria have a test
```

## Issues

1. **minor — AC-015.1 — the runner uses `strip()`, but the web path uses `trim()`.**
   `backend/src/main/java/vn/tsolve/chatbot/eval/RagEvalRunner.java:139` vs.
   `backend/src/main/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImpl.java:264`.
   - The two differ. `strip()` also removes Unicode spaces such as U+3000, U+2000–U+200A and U+2028. `trim()` also
     removes control characters U+0000–U+0008 and U+000E–U+001F that `strip()` keeps. I confirmed this on JDK 21:
     `"VPN 809　"` gives length 8 after `trim()` and 7 after `strip()`.
   - Review 1 said "use `trim()`, not `strip()`". `decisions.md` says "Trimmed".
   - *Failure scenario:* a gold question pasted from a document ends with an ideographic space. The runner embeds
     `"VPN 809"`, but the same text typed on 12.1 embeds `"VPN 809　"`. The vectors, and possibly the rank,
     differ from what the Chatbot keeps. The chance is low and the effect small.
   - *Fix:* `line.question().trim()`, and `QUESTION.trim()` in `RagEvalRunnerTest.java:66,78,138,164,177,183`.

2. **minor — process (project rule "Log every bug") — the three defects review 1 found have no `docs/bug-log.md`
   rows.**
   - `docs/bug-log.md:3-4` says every bug found "in testing, code review, …" gets a row. Project CLAUDE.md says a fix
     comes with a test named with the bug id.
   - The `PreviewCleanupJob` writer (AC-015.7), the BOM rejection and the untrimmed question were fixed. None has a
     row, and no test carries a `BUG_…` name.
   - *Fix:* add the rows with Status `Fixed`, and rename the covering tests or add the bug id to them. The bug
     tests are `RagEvalRunnerTest`'s AC-015.7 test, `GoldFileTest.acceptsByteOrderMark_AC_015_2` and the embed
     assertion. This could also be waived, see Assumptions.

3. **minor — carried over from review 1, accepted by the developer, re-checked:**
   - **OS environment variables override the profile** (`application-rag-eval.yml`). The `.env` import does not
     override it (precedence check above). The risk is only real environment variables, and `decisions.md` warns
     about it.
   - **No IT starts the `rag-eval` profile.** `RagEvalRunnerTest.java:189-204` still checks YAML keys only.
   - **The generic failure logs omit the exception type** (`RagEvalRunner.java:108`, `:126`).
   - **`ChatbotRetrieval` in `service/` imports `service/impl/ChatbotSource`** (AGENTS.md rules 2–3; the design
     asked for it).

   None of these breaks an AC as the code stands. The missing IT is the only guard that would catch a future
   scheduled writer added to another module.

4. **low — process — `iteration-4/tasks.md` Task 28 Files does not list the cross-module files.** These are
   `DevAccountSeeder`, `MockUserListSeeder`, `RegistryDataSourceConfig`, `EmbeddingCatchUpJob` and
   `PreviewCleanupJob`. The new `decisions.md` row names them and their properties, which covers review 1's
   Issue 7 in part. *Fix:* add them to Task 28's Files list, and name them in the PR body for the platform, search
   and ticket owners.

## Assumptions made (open judgment calls)
- **Verdict is PASS WITH FINDINGS, written as `verdict: pass`.** The template allows only `pass | fail`, and every
  remaining finding is minor or low.
- **The `strip()`/`trim()` gap (Issue 1) is minor, not blocking.** AC-015.1 says "as for a first web question". The
  gap only shows for Unicode spaces or control characters at the ends of a gold question.
- **Whether review findings on an unmerged feature need bug-log rows (Issue 2) is the user's call.** The bug-log
  text says code-review finds count, so I listed it. The user may waive it for defects that never reached `main`.
- **The accepted minors (env override, no profile IT, log detail, package placement) are not blocking**, as the
  developer decided. The user may still want the profile IT before the gold-set run on the AI server.
- **I wrote no separate test report file**, because the dispatcher limited me to this one file. The test results
  are below.
- AC-015.7 "write nothing" is read as the Workspace database. As in review 1, the Registry and Jobs switches go
  further than it requires.

## Test Report
Date 2026-11-26, JDK = IntelliJ JBR 21, command prefix `mvn -B -Dapi.version=1.44`.

| Run | Result |
|-----|--------|
| `test -Dtest=GoldFileTest,RagEvalReportTest,RagEvalRunnerTest,ChatbotAnswerServiceImplTest,PreviewCleanupJobTest,EmbeddingCatchUpJobTest` | 91 run, 0 failures, 0 errors |
| Full surefire `test` | 3279 run, 0 failures, 186 errors, in 15 identity/security classes (`JiraLinkControllerTest`, `LogoutControllerTest`, `SessionCreationTest`, …). Causes: "Previous attempts to find a Docker environment failed", `TwoTenantDatabases` init, Liquibase/EMF context failures. Docker is not running here. None comes from this change, and the count matches review 1 (3278 + the new BOM test) |
| `spotless:check` | pass |
| ITs (`ChatbotSchemaIT`, `ChatbotAnswerTimeoutIT`, `ChatbotConversationStoreIT`) | not run: the Docker daemon is not reachable (`dockerDesktopLinuxEngine` pipe missing) |
| `sdd.ts trace chatbot STORY-015` | 8/8 |
| Config precedence probe (scratch app, Boot 3.3.5 runtime classpath) | `application-rag-eval.yml` > `.env` import > `application.yml`; `CHATBOT_CONVERSATION_CLEANUP_CRON` resolves to `-` |
