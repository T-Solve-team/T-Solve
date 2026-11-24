# Wiki: document type and document-type-aware chunking — decisions

Date: 2026-11-21. Not yet in `wiki/spec.md`; these feed the spec change.

## Decided by the user

| # | Question | Choice |
|---|---|---|
| U1 | What metadata does an upload get? | One document type, from the fixed list below (system-wide, not per Space) |
| U2 | Who assigns it? | The AI suggests it on the extract step (11.3); the Knowledge Manager confirms or changes it before publishing |
| U3 | How is structure kept for chunking? | A structure-aware converter service (not only PDFBox/POI plain text) |
| U4 | Which converter? | docling-serve (MIT, REST `/v1/convert/file`, Markdown out), on the AI VPS next to Ollama |
| U5 | Is the type used at RAG time? | No filtering or ranking by type in the MVP. The type is written into each chunk's prefix (`[policy] Title > Heading > …`), so the embedding and the LLM see it; `chatbot`, `search` and `WikiSources` do not change |

Labels: `policy`, `runbook`, `guide`, `faq`, `reference`, `spec`, `record`, `announcement`, `form`, `other`, with the user's definitions and rules 1–7 (one main label per document; obligatory → policy, recommended → guide; steps → runbook; plan/design → spec, past → record; expires → announcement; empty template → form, filled → record; `other` above ~10% means review the labels).

## Decided by Claude (user rule: pick the best option and log it)

| # | Question | Options | Choice | Why |
|---|---|---|---|---|
| C1 | Where does docling run in the flow? | (a) on the extract request (b) in the worker index job | (b) | Extract stays fast (PDFBox/POI text is enough to suggest a label); conversion takes seconds per page on CPU (22/11: the AI server is a cloud GPU server with 24 GB VRAM, not CPU; the choice still holds) |
| C2 | docling fails or times out | (a) fail the index (b) fall back to the current `PassageSplitter` | (b) | Matches "an AI failure does not fail an upload"; the document is still `INDEXED` |
| C3 | AI suggestion fails or times out on extract | (a) fail the preview (b) suggest `other` | (b) | Never block an upload on AI |
| C4 | Classifier input | whole text / first part | Title + first 4,000 characters of the extracted text, `LlmClient.chat` with a JSON schema whose `documentType` is an enum of the 10 labels; files classified one at a time (AC-007.2, BUG-355) | Bounded prompt on CPU Ollama (22/11: the AI server is a cloud GPU server with 24 GB VRAM, not CPU; the choice still holds) |
| C5 | Change the type after publishing | (a) yes, with re-index (b) not in the MVP | (b) | Out of Scope already says no edit after upload; the KM confirms the type before publish |
| C6 | Documents that exist today | (a) re-classify and re-index (b) set `other` by migration | (b) | `other` uses today's splitter, so their chunks stay valid; no re-index |
| C7 | Chunk size | keep 1,000 / raise | At most 1,500 characters per chunk, prefix included, cut at the type's unit boundary; a unit over the limit is split with the prefix repeated; still at most 200 chunks | A whole runbook procedure or policy clause often exceeds 1,000; bge-m3 accepts far more |
| C8 | Who owns the docling client | `ai` module / `wiki` module | `wiki` (`DOCLING_BASE_URL`, no default) | One module changes; the `ai` module stays Ollama-only |
| C9 | Rule 7 (`other` > 10%) monitoring | build now / later | Later | No screen asks for it yet |
| C10 | Classify the files of one extract in parallel or one at a time (PR #303 review, BUG-355) | (a) parallel (b) bounded to `AI_MAX_CONCURRENT` (c) one at a time, timeout stops the call | (c) | Ollama has 2 slots and a 1 s permit wait: parallel calls fail as Busy and become `OTHER`. One at a time leaves a slot for chatbot and triage; stopping the call at the timeout frees its slot. Cost: up to 10 × 20 s in the worst case, a few seconds per file normally |

## Chunking unit per type

| Type | Unit |
|---|---|
| policy | One clause (Chapter > Article > Clause) |
| runbook | One whole procedure (heading + prerequisites + steps); over the limit → split between steps |
| guide | One heading section; over the limit → paragraphs |
| faq | One question–answer pair |
| reference | One entry or table row with its header; glossary term; changelog version |
| spec | One section / use case / requirement |
| record | One section (agenda item, decision) |
| announcement | The whole document when it fits, else paragraphs |
| form | One chunk: the form name and its fields |
| other | Today's `PassageSplitter` |

## Spec and design impact

- `wiki/spec.md`: AC-004.1 (`documentType` suggestion in `WikiUploadPreview`), AC-004.6/AC-004.10 (`documentType` per row in `WikiPublishMetadata`, required, one of the 10), AC-004.12 (audit detail), AC-004.14 rewritten + new ACs per type and for the prefix and the docling fallback, AC-004.18 (type selector on 11.3), 11.1/11.2 show the type; Interface Contracts and OpenAPI.
- Database: `wiki_document.document_type` (not null, default `other` for existing rows), new Liquibase changeset.
- `architecture.md`: docling-serve on the AI VPS, private link only, no egress; Docker Compose service for local dev.
- `wiki` spec re-approval locks wiki code until approved.
