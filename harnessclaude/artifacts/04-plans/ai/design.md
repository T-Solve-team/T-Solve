---
module: ai
date: 2026-11-05
---

# Design: ai

Spec: `artifacts/03-modules/ai/spec.md`

## Architecture Overview

Backend only, package `vn.tsolve.ai`, no web layer and no database.

```
callers (tracker, search, wiki, triage, chatbot)
   │  service/SolutionAiService          client/LlmClient   client/EmbeddingClient
   ▼            │ (draft, rewrite)             │                    │
service/impl/SolutionAiServiceImpl ──────────► OllamaLlmClient   OllamaEmbeddingClient
                                                    └──────┬────────────┘
                                                     client/OllamaHttp
                                    one semaphore per process · one retry · timing log · no content in logs
                                                           │  RestClient (chat: 60 s read, embed: 10 s read)
                                                           ▼
                                               Ollama at OLLAMA_BASE_URL (/api/chat, /api/embed)
```

- `OllamaHttp` (package-private, one bean) is the only class that talks HTTP. It owns the two `RestClient`s, the
  concurrency semaphore, the retry rule and the one log line per call. Both clients go through it, so the limits of
  STORY-003 hold for every caller.
- `OllamaLlmClient` builds the `/api/chat` body (`model`, `messages`, `stream: false`, optional `format`) and reads
  `message.content`. `OllamaEmbeddingClient` builds the `/api/embed` body (`model`, `input: [...]`) and reads
  `embeddings`.
- `SolutionAiServiceImpl` holds the two prompts (Java text blocks), the JSON schema of the five sections, the input
  cut of AC-004.4, the fallback draft (AC-004.2) and the checks of AC-004.2 / AC-005.3. It only returns values.
- Nothing is a Spring `@Transactional`; callers call `ai` outside their transactions (backend `AGENTS.md`).

## Data Model

No tables. Records (Interface Contracts in the spec): `ChatMessage`, `IssueContent`, `SolutionSections`,
`SolutionDraft`. Section length is counted with `String.length()` on the trimmed text, the same way the Solution
validation counts GBR-33's 10,000 characters.

Ollama wire format (Ollama API, non-streaming):

| Call | Request | Response read |
|------|---------|---------------|
| chat | `{"model", "messages":[{"role","content"}], "stream":false, "format"?: <schema>, "options":{"temperature"}}` | `message.content` |
| embed | `{"model", "input":[...]}` | `embeddings` (list of float lists) |

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Q1 Scope | One spec for KAN-139, KAN-142, KAN-143 | Clients only, draft / rewrite later by Change Mode | Hải Anh 05/11: all three are due this week; one approval |
| Q2 HTTP client | Spring `RestClient` on Ollama's own API, `SimpleClientHttpRequestFactory` timeouts (as `GoogleOidcClient`) | Spring AI Ollama starter | Hải Anh 05/11: no new dependency; Spring AI 1.x needs Boot ≥ 3.4 (repo on 3.3.5); retry and logging stay in our hands |
| Q3 Exposure | Java `SolutionAiService`; `tracker` calls it from its FIT-secured `/api/v1/jira-app/**` endpoints | `ai` REST behind FIT; REST taking the issue text from the Forge app | Hải Anh 05/11: `ai` is testable now without the FIT chain; no client-supplied issue text to trust; one owner per endpoint |
| Q4 Limits and failures | Connect 2 s; chat 60 s, embed 10 s; one retry on connection failure or 5xx only; 2 calls in flight per process, 1 s wait for a slot; `AiUnavailableException` (unchecked, `MessageCodeException` 503 MSG06) | Return `Optional` | Hải Anh 05/11: the reason survives; an uncaught failure still answers MSG06 through the existing handler |
| Rewrite failure | `AiRewriteUnavailableException` (503, MSG68) | Reuse `AiUnavailableException` (MSG06) | The form must show MSG68 (UC-19 Step 6); a caller that lets it through still shows the right message |
| Draft failure | Return the fallback draft with `isAiDraft` false, never throw | Throw and let the caller build the fallback | UC-19 Step 3 defines the fallback from the issue alone; one place builds it |
| Structured output | Ollama `format` with a JSON schema of the five string fields | Free text parsed by headings | Ollama constrains the output; parsing is one `ObjectMapper.readValue` |

Decided in planning:
- Configuration is read with `@Value("${OLLAMA_BASE_URL}")` and the other env names directly (with the defaults of
  the spec as `${AI_CHAT_TIMEOUT:60s}` etc.) in `OllamaHttp`, so `application.yml` and `common/config/` (owned by
  `be-foundation`) are not touched. A missing `OLLAMA_BASE_URL` / `OLLAMA_CHAT_MODEL` fails the context with
  Spring's "Could not resolve placeholder 'OLLAMA_BASE_URL'" (AC-003.1). Nothing calls Ollama at startup (AC-003.7).
- Retry classification: `ResourceAccessException` whose cause is a `ConnectException` (or an unknown host) and
  `HttpServerErrorException` retry once; a `SocketTimeoutException` cause, `HttpClientErrorException` and any
  parse failure do not.
- The semaphore permit is taken once per call (retry included) and released in `finally`. An interrupt while
  waiting restores the interrupt flag and throws `AiUnavailableException`.
- Exception messages hold only the operation and the error class, never a request or response body, so neither the
  exception nor the global handler's log can leak content (AC-003.6).
- Draft and rewrite send `options.temperature` 0.2 (steady, close to the source); the chat client sends no options
  unless the caller's service adds them later.
- The comments of `IssueContent` are oldest first (the order Jira returns them); "last comment" is the last item.
- Decided in planning (builder check): the 12,000 characters of AC-004.4 count the description and the comments
  only, not the summary; comments are taken newest first and the first one that does not fit stops the selection;
  the kept comments are sent oldest first.
- Decided in planning (builder check): the retry of AC-003.3 is sent at once, with no delay.
- Decided in planning (builder check): Ollama's numbers are read as `double` and stored as `float` in `float[]`.
- Decided in planning (builder check): the draft and rewrite prompts tell the model to write in the language of the
  issue / Solution (Vietnamese or English) and to return plain text in each field.
- Decided in planning (builder check): a successful call logs at INFO, a failed one at WARN; same fields (AC-003.6).
- Decided in planning (builder check): AC-003.8 is tested by calling `GlobalExceptionHandler` with an
  `AiUnavailableException` (status 503, `code` MSG06); no new handler code is needed.

## File Structure

```
backend/src/main/java/vn/tsolve/ai/
├── package-info.java
├── client/
│   ├── LlmClient.java, ChatMessage.java, OllamaLlmClient.java
│   ├── EmbeddingClient.java, OllamaEmbeddingClient.java
│   └── OllamaHttp.java                 (package-private: RestClients, semaphore, retry, log)
├── service/
│   ├── SolutionAiService.java
│   └── impl/SolutionAiServiceImpl.java (prompts, schema, cut, fallback, checks)
├── dto/IssueContent.java, SolutionSections.java, SolutionDraft.java
└── exception/AiUnavailableException.java, AiRewriteUnavailableException.java

backend/src/test/java/vn/tsolve/ai/
├── client/OllamaLlmClientTest.java, OllamaEmbeddingClientTest.java, OllamaHttpTest.java   (WireMock)
├── AiStartupTest.java                  (ApplicationContextRunner: AC-003.1, AC-003.7)
└── service/impl/SolutionAiServiceImplTest.java   (fake LlmClient)

.env.example                            (+ the eight AI variables, no real values)
```

## External Dependencies
- Ollama HTTP API (`/api/chat`, `/api/embed`) on the AI VPS, through `OLLAMA_BASE_URL` (`platform`).
- WireMock (already a test dependency) for the client tests; Jackson (already present) for the JSON.
- No new Maven dependency.

## Edge Cases & Risks

Dimensions analyzed: Input extremes, Timing, Scale, Environment, Error cascades, Integration, Compliance, Business
logic · Skipped: User types and Authorization (no endpoint here; the callers' endpoints check roles), State
transitions and Data integrity (stateless, stores nothing).

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-003 | Timing | A call throws while it holds a semaphore slot | Critical | Slot released in `finally`; later calls still go through | T-1 |
| 2 | STORY-003 | Compliance | Ollama's error body echoes the prompt | High | Exception message and log line hold no body text | T-1 |
| 3 | STORY-003 | Timing | Third call while two are in flight | High | Waits ≤ 1 s, then `AiUnavailableException`, Ollama not called | T-1 |
| 4 | STORY-003 | Timing | Read timeout on chat | High | No retry; one request seen by WireMock | T-1 |
| 5 | STORY-003 | Error cascades | First call 503, second 200 | High | One retry, answer returned | T-1 |
| 6 | STORY-003 | Error cascades | Ollama 404 (model not pulled) | Medium | No retry, `AiUnavailableException` | T-1 |
| 7 | STORY-003 | Environment | Backend starts with Ollama down | High | Context starts; no call at startup | T-1 |
| 8 | STORY-003 | Environment | `OLLAMA_BASE_URL` missing | High | Context fails naming the variable | T-1 |
| 9 | STORY-003 | Timing | Thread interrupted while waiting for a slot | Medium | Interrupt flag kept, `AiUnavailableException` | T-1 |
| 10 | STORY-001 | Integration | Answer cut by the context length: JSON does not parse | High | `AiUnavailableException` | T-1 |
| 11 | STORY-001 | Input extremes | Answer content blank | Medium | `AiUnavailableException` | T-1 |
| 12 | STORY-002 | Integration | Wrong embed model: 768-length vectors | High | `AiUnavailableException` | T-1 |
| 13 | STORY-002 | Integration | Fewer vectors than texts | High | `AiUnavailableException` | T-1 |
| 14 | STORY-002 | Scale | Empty list | Low | Empty list, no call | T-1 |
| 15 | STORY-002 | Scale | Hundreds of texts in one call pass 10 s | Medium | `AiUnavailableException`; callers batch (search / wiki design) | — |
| 16 | STORY-004 | Input extremes | `description` null, `comments` empty or null | High | No NPE; fallback leaves those sections empty | T-2 |
| 17 | STORY-004 | Input extremes | Description of 50,000 characters | High | Cut to 12,000; newest comments only while they fit | T-2 |
| 18 | STORY-004 | Business logic | Required section only whitespace | High | Treated as empty → fallback | T-2 |
| 19 | STORY-004 | Business logic | AI sections total 10,001 characters | High | Fallback | T-2 |
| 20 | STORY-004 | Integration | Field of the wrong JSON type or missing | Medium | Fallback | T-2 |
| 21 | STORY-004 | Error cascades | `LlmClient` throws | High | Fallback, `isAiDraft` false, nothing thrown | T-2 |
| 22 | STORY-004 | Input extremes | Issue text tells the model to ignore its instructions | Medium | Output still only the five fields (schema); the writer reviews it (GBR-12) | — |
| 23 | STORY-005 | Business logic | Model fills a section that was empty | High | Section forced empty (AC-005.2) | T-3 |
| 24 | STORY-005 | Business logic | Model empties a filled section | High | `AiRewriteUnavailableException` (MSG68) | T-3 |
| 25 | STORY-005 | Error cascades | Rewrite times out | High | MSG68; the given Solution is not touched | T-3 |
| 26 | STORY-005 | Input extremes | Every section empty | Medium | Returned unchanged, no call | T-3 |
| 27 | STORY-004, 005 | Input extremes | Vietnamese text with diacritics | Medium | UTF-8 JSON round trip unchanged | T-2 |

Open questions for the spec owner: none (the values of AC-004.4 and the 10,000-character checks of AC-004.2 /
AC-005.3 were put to Hải Anh in the spec review).
