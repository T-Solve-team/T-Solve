---
module: ai
date: 2026-11-05
owner: BA
---

# Spec: ai

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-05 | STORY-001..STORY-005 | Initial spec: the Ollama chat and embedding clients with their limits (KAN-139), the AI draft (KAN-142) and the AI rewrite (KAN-143) of a Solution as a Java service | Plan the `ai` module (KAN-139, KAN-142, KAN-143). Hải Anh 05/11: one spec for all three (Q1); plain `RestClient` to Ollama's own API, no Spring AI (Q2); draft and rewrite are a Java service that `tracker` calls from its FIT-secured Jira App endpoints, `ai` has no REST of its own (Q3); connect 2 s, chat 60 s, embed 10 s, one retry on a connection error or 5xx only, 2 calls at a time per process with a 1 s wait for a slot, failures thrown as an unchecked exception that ends as 503 MSG06 when nobody catches it (Q4) |
| 2026-11-06 | STORY-004 | AC-004.2 reworded: the fallback draft's Symptoms is cut to 10,000 characters and its Resolution steps to what remains of the 10,000, without splitting a character, so the fallback fits the Solution template | PR #122 review (Trần Trung Hiếu, 05/11): a description over 10,000 characters made a fallback the Solution form rejects (GBR-33), breaking AC-004.3 at the caller; Hải Anh 06/11 chose one 10,000 total, Symptoms first |

## Sources

UC-19 (Report3 SRS v3460: step 3 "AI drafts each section", Step 3 "AI cannot draft the Solution", Step 6 "Rewrite
with AI"), GBR-12 (AI is optional; the writer reads the rewrite and clicks Finish), GBR-33 (Solution template,
10,000 characters), MSG06, MSG67, MSG68; Jira KAN-139, KAN-142, KAN-143 (updated 01/11: no PII masking);
`architecture.md` (Ollama on the AI VPS only, D3, D41; no prompt logging); `platform` spec STORY-008 and STORY-011
(`OLLAMA_BASE_URL`, local and AI VPS Ollama); `tracker` spec (the Jira App endpoints and FIT auth). PII masking was
cancelled on 29/10 (KAN-138): the SRS lines that still say "PII masked" are superseded.

## Requirements

### Functional
1. `LlmClient`: chat completion on the Ollama chat model, optionally constrained to a JSON schema (STORY-001).
2. `EmbeddingClient`: bge-m3 embeddings of 1024 dimensions, many texts in one call (STORY-002).
3. Every call goes only to the Ollama at `OLLAMA_BASE_URL`, with a time limit, one retry for transient errors,
   a per-process cap on concurrent calls, and no logging of content; a failure is reported to the caller as an
   exception and never stops the backend (STORY-003, GBR-12).
4. `SolutionAiService.draft`: the five Solution sections drafted from an issue; on any AI failure, the UC-19 fallback
   draft instead (STORY-004, MSG67).
5. `SolutionAiService.rewrite`: the same Solution rewritten more clearly with the same meaning and template; on any
   AI failure, MSG68 and nothing changed (STORY-005).

### Non-Functional
1. No content leaves T-Solve's servers: the only AI host is `OLLAMA_BASE_URL` (D41, GBR-12).
2. No prompt, issue text, Solution text, answer or vector appears in any log line.
3. The `ai` module stores nothing: no table, no file, no cache; it needs no Workspace (the callers keep their own
   Workspace-scoped data).
4. Timeouts, retry and concurrency come from environment variables with defaults, so they can be tuned after the
   benchmark (KAN-140) without a code change.

## User Stories

### STORY-001: Chat completion client
- As a backend developer of `search`, `wiki`, `triage` or `chatbot`, I want one client for the Ollama chat model so
  that every AI feature calls the LLM the same way.
- Acceptance:
  - AC-001.1: WHEN a caller asks `LlmClient` for a chat completion with a list of messages (each with the role `system`, `user` or `assistant` and its text), THE ai module SHALL send them in one `POST {OLLAMA_BASE_URL}/api/chat` with the model `OLLAMA_CHAT_MODEL` and `"stream": false`, and SHALL return the text of the answer's message.
  - AC-001.2: WHERE the caller gives a JSON schema, THE ai module SHALL send it as the request's `format` and SHALL return the answer only if its text parses as a JSON object.
  - AC-001.3: IF the answer's text is empty or blank, or a JSON answer does not parse as a JSON object, THEN THE ai module SHALL throw `AiUnavailableException`.

### STORY-002: Embedding client
- As a backend developer of `search`, `wiki`, `triage` or `chatbot`, I want one client for bge-m3 so that every
  vector in T-Solve has the same model and size.
- Acceptance:
  - AC-002.1: WHEN a caller asks `EmbeddingClient` to embed a list of one or more texts, THE ai module SHALL send them in one `POST {OLLAMA_BASE_URL}/api/embed` with the model `OLLAMA_EMBED_MODEL` (default `bge-m3`) and SHALL return one vector per text, in the order of the texts.
  - AC-002.2: IF Ollama returns a number of vectors different from the number of texts, or any vector whose length is not 1024, THEN THE ai module SHALL throw `AiUnavailableException`.
  - AC-002.3: IF the list of texts is empty, THEN THE ai module SHALL return an empty list without calling Ollama.

### STORY-003: Calls to the AI server
- As the team, we want every AI call bounded, private and unable to break the system, so that a slow or stopped AI
  server only turns off the AI parts (GBR-12).
- Acceptance:
  - AC-003.1: THE ai module SHALL send requests only to `OLLAMA_BASE_URL`, which has no default value; IF `OLLAMA_BASE_URL` or `OLLAMA_CHAT_MODEL` is not set, THEN THE backend SHALL refuse to start and name the missing variable.
  - AC-003.2: IF Ollama does not accept the connection within `AI_CONNECT_TIMEOUT` (default 2 s), or does not answer within `AI_CHAT_TIMEOUT` (default 60 s) for a chat completion or `AI_EMBED_TIMEOUT` (default 10 s) for an embedding, THEN THE ai module SHALL stop waiting and throw `AiUnavailableException`.
  - AC-003.3: IF the connection to Ollama fails or Ollama answers with a 5xx status, THEN THE ai module SHALL send the same request once more, and IF that also fails, THEN THE ai module SHALL throw `AiUnavailableException`.
  - AC-003.4: IF Ollama answers with a 4xx status or the answer times out (AC-003.2), THEN THE ai module SHALL throw `AiUnavailableException` without sending the request again.
  - AC-003.5: WHILE `AI_MAX_CONCURRENT` (default 2) calls of one backend process are waiting for Ollama, THE ai module SHALL hold any further call for at most `AI_PERMIT_WAIT` (default 1 s) for a free slot, and IF no slot frees in that time, THEN THE ai module SHALL throw `AiUnavailableException` without calling Ollama.
  - AC-003.6: THE ai module SHALL log no message text, issue text, Solution text, answer or vector; the log line of a call SHALL hold only the operation (`chat` or `embed`), the model, the duration, the outcome and, on a failure, the class of the error.
  - AC-003.7: WHEN the backend starts while Ollama cannot be reached, THE backend SHALL start and serve the requests that need no AI.
  - AC-003.8: IF an `AiUnavailableException` is not caught by its caller and reaches the global error handler, THEN THE backend SHALL answer `503` with a `ProblemDetail` whose `code` is `MSG06`.

### STORY-004: AI draft of a Solution (UC-19 step 3)
- As a Ticket Resolver, I want the Solution Form to start from an AI draft of my issue so that I only correct it
  instead of writing it from nothing.
- Acceptance:
  - AC-004.1: WHEN a caller asks `SolutionAiService` to draft a Solution from an issue's summary, description and comments, THE ai module SHALL ask the LLM for the five sections of the Solution template (Symptoms, Root cause, Resolution steps, Workaround, Scope of applicability; GBR-33) as a JSON object and SHALL return them with `isAiDraft` true.
  - AC-004.2: IF the LLM call fails, or the answer leaves Symptoms, Root cause, Resolution steps or Scope of applicability empty, or the five sections together are longer than 10,000 characters, THEN THE ai module SHALL return the fallback draft — Symptoms is the issue's description cut to 10,000 characters, Resolution steps is the issue's last comment cut to the characters that remain of the 10,000, the other sections are empty, and no cut splits a character — with `isAiDraft` false, so the caller shows MSG67 (UC-19 Step 3).
  - AC-004.3: THE `draft` operation SHALL never throw because of an AI failure; its only failure result is the fallback draft of AC-004.2.
  - AC-004.4: WHEN the description and the comments together are longer than 12,000 characters, THE ai module SHALL send the summary, the description cut to 12,000 characters and then, newest first, only the comments that still fit in 12,000 characters in all.
  - AC-004.5: IF the issue has no description, or has no comment, THEN THE fallback draft SHALL leave Symptoms, or Resolution steps, empty.

### STORY-005: AI rewrite of a Solution (UC-19 Step 6)
- As a Ticket Resolver, I want AI to rewrite my Solution more clearly so that it is easy for others to judge and
  apply, while I stay the one who decides what is submitted.
- Acceptance:
  - AC-005.1: WHEN a caller asks `SolutionAiService` to rewrite a Solution, THE ai module SHALL send its filled sections to the LLM with the instruction to keep the meaning and the five-section template, and SHALL return the rewritten five sections.
  - AC-005.2: WHEN a section of the Solution given is empty, THE rewritten Solution SHALL have that section empty.
  - AC-005.3: IF the LLM call fails, or the answer leaves empty a section that was filled in the Solution given, or the rewritten sections together are longer than 10,000 characters, THEN THE ai module SHALL throw `AiRewriteUnavailableException`, which reaches the client as `503` with `code` `MSG68` when the caller does not catch it.
  - AC-005.4: IF every section of the Solution given is empty, THEN THE ai module SHALL return it unchanged without calling the LLM.
  - AC-005.5: THE ai module SHALL only return a draft or a rewrite; it SHALL never save, replace or submit a Solution (the writer reads it and clicks Finish themselves, GBR-12).

## Out of Scope
- REST endpoints: the Solution Form calls `tracker`'s FIT-secured `/api/v1/jira-app/**` endpoints, which call
  `SolutionAiService` (decision Q3). The endpoint paths, their auth and how MSG67 / MSG68 reach the form are the
  `tracker` / `jira-app` owners' work.
- Reading the issue from Jira (`tracker`'s `TicketTrackerClient`).
- The embedding pipeline of tickets and Wiki documents and the vector tables (KAN-141: `search`, `wiki`).
- The prompts and retrieval of search, triage and the Chatbot (their modules); the Chatbot's 60 s limit for a whole
  question stays in `chatbot`.
- The model benchmark (KAN-140, Đặng Nhật Minh) and the choice of `OLLAMA_CHAT_MODEL`.
- Running Ollama, pulling the models, the private link to the AI VPS (`platform` STORY-008, STORY-011).
- PII masking (KAN-138 cancelled, 29/10), streaming answers, caching, per-Workspace quotas, other AI providers.

## Interface Contracts

### Exposed by this module
Java, package `vn.tsolve.ai` (`ai/client/` for the Ollama clients):

```java
// ai/client
public interface LlmClient {
    /** Chat completion; jsonSchema may be null. Throws AiUnavailableException. */
    String chat(List<ChatMessage> messages, Map<String, Object> jsonSchema);
}
public record ChatMessage(Role role, String content) { public enum Role { SYSTEM, USER, ASSISTANT } }

public interface EmbeddingClient {
    /** One 1024-dimension vector per text, same order. Throws AiUnavailableException. */
    List<float[]> embed(List<String> texts);
}

// ai/service
public interface SolutionAiService {
    SolutionDraft draft(IssueContent issue);        // never throws for an AI failure (AC-004.3)
    SolutionSections rewrite(SolutionSections solution); // throws AiRewriteUnavailableException (MSG68)
}
public record IssueContent(String summary, String description, List<String> comments) {} // comments oldest first
public record SolutionSections(String symptoms, String rootCause, String resolutionSteps,
                               String workaround, String scopeOfApplicability) {}
public record SolutionDraft(SolutionSections sections, boolean isAiDraft) {} // false → caller shows MSG67

// ai/exception
public class AiUnavailableException extends MessageCodeException {}          // 503, MSG06
public class AiRewriteUnavailableException extends MessageCodeException {}   // 503, MSG68
```

Environment variables (read only by `ai`): `OLLAMA_BASE_URL`, `OLLAMA_CHAT_MODEL` (no default);
`OLLAMA_EMBED_MODEL` (default `bge-m3`), `AI_CONNECT_TIMEOUT` (2s), `AI_CHAT_TIMEOUT` (60s), `AI_EMBED_TIMEOUT`
(10s), `AI_MAX_CONCURRENT` (2), `AI_PERMIT_WAIT` (1s); all listed in `.env.example`.

### Required from other modules
- `platform`: `OLLAMA_BASE_URL` and `OLLAMA_CHAT_MODEL` set in every environment; Ollama with `bge-m3` and the chat
  model pulled (AC-008.3, AC-011.4).
- `be-foundation`: `MessageCodeException` and `GlobalExceptionHandler` (on `main`).
- Callers (`tracker` for draft / rewrite; `search`, `wiki`, `triage`, `chatbot` for the clients): make AI calls
  outside a `@Transactional` method (backend `AGENTS.md`) and handle `AiUnavailableException` with their fallback.

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] The Ollama clients are tested against WireMock (no real Ollama in the test suite)
- [ ] `mvn verify` passes (tests + `mvn spotless:check`)
- [ ] `.env.example` lists every variable above with no real value
- [ ] The `tracker` owner is told about `SolutionAiService` (draft / rewrite) and MSG67 / MSG68

<!-- ACCEPTANCE CRITERIA: one testable statement per item, id AC-<story>.<n> (never reuse a
     removed id), in EARS form: THE <system> SHALL <response>, optionally led by
     WHEN <trigger>, WHILE <state>, IF <unwanted condition> THEN, or WHERE <feature is included>.
     A story with a WHEN criterion also gets an IF … THEN criterion for its failure path.
     OUT OF SCOPE: name what is excluded, so it is not built by accident; adding it later is a
     spec change (Changelog row, re-approval).
     OPEN QUESTIONS: where the answer is missing, write [NEEDS CLARIFICATION: <question>] and ask
     the user — never fill the gap with a guess. `bun harnessclaude/hooks/sdd.ts check <module>`
     fails while any remain.
     SPEC EDIT RULE: Any edit to this file that changes business logic, requirements, stories, or
     acceptance criteria MUST add a new row to the Changelog table above (Date, Story if
     applicable, Change summary, Reason). Once the user has approved the spec, any edit locks the
     module's code until they approve the new version (`sdd.ts approve <module>`). -->
