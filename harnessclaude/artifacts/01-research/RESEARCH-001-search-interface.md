---
id: RESEARCH-001
title: Search interface and search_document table
question: What does the search package offer to chatbot, triage, tracker, review and import, and how does it store its index?
status: complete
date: 2026-10-21
related: [search]
---

# Search interface and search_document table

Agreed contract (2026-10-21) for UC-26 (owner: Trần Hải Anh), shared early so the chatbot (Trần Trung Hiếu) can code
against a stub in week 1. It goes into `artifacts/03-modules/search/spec.md`; the Open Questions below do not change the method signatures.
Comments on this file are welcome; change it in a commit on `feat/search`.

## Research Question
Which Java interface do other packages call to search Published knowledge, and which table holds the index?

## Context
- Search, AI Triage, the Issue Panel and the Knowledge Chatbot all search Published tickets, but each may
  see different data (Business Rules: ticket visibility rule, GBR-35, GBR-36).
- `main` has no Published-ticket table yet (`ticket_review` lives on the unmerged `feat/ticket-review`)
  and no Department / Project / membership tables.

## Findings

### What is matched against the user's text
The user types a problem description, not a title. Titles are short ("Lỗi VPN"), so semantic search compares
the query with the ticket's **problem side: title + Symptoms + Root cause**. The fix side (Steps, Workaround,
Scope of applicability) is shown in the result and used by keyword search, not embedded.

| Part | Semantic (embedding) | Keyword (full-text) | Shown in hit |
|------|----------------------|---------------------|--------------|
| Title | yes | yes | yes |
| Symptoms, Root cause | yes | yes | yes |
| Steps, Workaround, Applicability | no | yes | yes |

Score = semantic similarity and keyword rank combined (weights fixed in the design).

### Interface for callers (package `vn.tsolve.search`)

```java
// search/service/SearchService.java
public interface SearchService {
    /**
     * @param query  text to search; the caller masks PII first when it comes from an end user
     * @param scope  who is searching and for what; limits results to what that caller may see
     * @param limit  maximum number of hits, 1..50
     * @return hits ordered by score, highest first; empty when nothing matches
     * @throws SearchNotAvailableException for ForUser when the user belongs to no Project (MSG76)
     */
    List<SearchHit> search(String query, SearchScope scope, int limit);
}

// search/dto/SearchScope.java
public sealed interface SearchScope {
    /** Search screen and Issue Panel: Published tickets of the user's Departments, full ticket details. */
    record ForUser(Long userId) implements SearchScope {}
    /** AI Triage: Published tickets of the Project's Department, Solution content only (GBR-35). */
    record ForTriage(Long projectId) implements SearchScope {}
    /** Knowledge Chatbot: Solutions of every Published ticket, ticket details hidden (GBR-36). */
    record WorkspaceSolutions(Long userId) implements SearchScope {}
}

// search/dto/SearchHit.java — fields a scope may not expose are null:
// ticketId and a ticket url only for ForUser; a WIKI hit always has its url.
public record SearchHit(
        SourceType sourceType, Long ticketId, String title, String solutionText, String url, double score) {}

// search/dto/SourceType.java
public enum SourceType { TICKET, WIKI } // WIKI once the Wiki is indexed; handle it now
```

Week-1 stub for the chatbot (profile `stub-search`), returning what the real one returns for
`WorkspaceSolutions` — no ticket id or ticket link:

```java
@Service
@Profile("stub-search")
class StubSearchService implements SearchService {
    @Override
    public List<SearchHit> search(String query, SearchScope scope, int limit) {
        return List.of(
                new SearchHit(SourceType.TICKET, null, "VPN không kết nối",
                        "Cài lại FortiClient bản 7.2 rồi đăng nhập lại.", null, 0.91),
                new SearchHit(SourceType.WIKI, null, "Hướng dẫn VPN",
                        "Mục 3: cấu hình FortiClient.", "https://wiki.example/vpn", 0.78));
    }
}
```

### Interface for review and import

```java
// search/service/SearchIndexService.java
public interface SearchIndexService {
    /** Adds or refreshes the ticket in the index; call after the transaction that published it commits. */
    void index(Long ticketId);
    /** Removes the ticket from the index (unpublished, or its Department deleted). */
    void remove(Long ticketId);
}
```

Callers: review (approve → `index`, unpublish → `remove`, re-publish → `index`) and import (each imported
ticket → `index`).

### Table `search_document`

```sql
--liquibase formatted sql

--changeset search:00X-add-search-document
-- Search index of Published tickets, written only through SearchIndexService.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE search_document (
    id             BIGSERIAL    PRIMARY KEY,
    ticket_id      BIGINT       NOT NULL UNIQUE,     -- Published ticket id (review module); no FK across modules
    project_id     BIGINT       NOT NULL,
    department_id  BIGINT       NOT NULL,            -- visibility filter
    title          VARCHAR(500) NOT NULL,
    problem_text   TEXT         NOT NULL,            -- Symptoms + Root cause: embedded with the title
    solution_text  TEXT         NOT NULL,            -- Steps + Workaround + Applicability: shown, keyword only
    embedding      vector(1536),                     -- of title + problem_text; null when the AI call failed
    search_vector  tsvector GENERATED ALWAYS AS (
                       to_tsvector('simple', title || ' ' || problem_text || ' ' || solution_text)) STORED,
    indexed_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_search_document_department ON search_document (department_id);
CREATE INDEX idx_search_document_fts        ON search_document USING GIN (search_vector);
CREATE INDEX idx_search_document_embedding  ON search_document USING hnsw (embedding vector_cosine_ops);
```

| Choice | Why |
|--------|-----|
| Own table, no FK to the ticket table | Search works and is tested without unmerged code; AGENTS §1 forbids cross-module entity links |
| `embedding` nullable | AI is optional (Business Rules); keyword search still answers |
| `'simple'` text-search config | PostgreSQL has no Vietnamese configuration |
| Tickets only | Wiki belongs to another member; added by a later changeset when needed |

## Recommendations
- Chatbot codes against `SearchService` with the stub above; the real implementation replaces it in week 2.
- Review and import call `SearchIndexService` after their transaction commits.

## Open Questions
- User id type: `Long` or `String`? `ticket_review.writer_id` is `VARCHAR(150)`. (Hiếu, team)
- Who masks PII in chatbot questions — chatbot (proposed: it holds the raw question) or search? (Hiếu)
- Is `limit` up to 50 enough? (Hiếu)
- Which OpenRouter embedding model? `vector(1536)` must match its dimension. (team)
- Which Department / Project / membership tables give a user's Departments? (Bình, sample-data script)
- Changeset number: `002` is taken on `feat/ticket-review`; take the next free one at merge.
- Tracker's `TrackerSearchPort` "compute / store / read suggestions": proposed that search only searches and
  tracker stores per-issue suggestions itself. (Bình)

## Related Artifacts
- `artifacts/00-baseline/Business_Rules.docx` (visibility rule, GBR-35, GBR-36)
- `artifacts/00-baseline/UC_list_and_Actors (1).docx` (UC-26, UC-36)
- Module spec (to come): `artifacts/03-modules/search/spec.md`
