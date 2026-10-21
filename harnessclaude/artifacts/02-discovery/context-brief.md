---
project: T-Solve
mode: blueprint
date: 2026-10-21
---

# Context Brief: T-Solve — Ticket Import & Repository (epic KAN-29)

> Scope: this brief covers only the **Ticket Import & Repository** epic (UC-20 to UC-23). The user chose this scope on 2026-10-21. Other epics get their own discovery when they start.

## Project Overview
T-Solve keeps the knowledge of resolved Jira issues as reusable Tickets: a snapshot of the issue plus a structured Solution. Most Tickets come from the Solution Form in Jira (UC-19), which a later epic covers. This epic covers the ways a Project Manager (PM) brings in tickets resolved **before** T-Solve existed, or outside Jira:
- UC-20 Import tickets from Jira
- UC-21 Import tickets from CSV (Jira task **KAN-31**, the first feature)
- UC-22 Create ticket manually
- UC-23 View import history

## Goals
- A PM can fill the knowledge base with past resolved tickets in one sitting per import. They become searchable at once, without a second review.
- Every import is traceable: one Import History record per import, Completed or Failed, never edited or deleted.
- No duplicate tickets: a Ticket ID exists only once.

## Key Requirements

### Functional
1. **Import is review (GBR-23):** imported tickets are saved directly as **Published**, with an approve decision by the importing PM. They skip the Review Queue and never become Expired.
2. **Solution template (GBR-33):** each ticket's Solution has five sections: Symptoms, Root cause, Resolution steps, Workaround, Scope of applicability. The sections total at most 10,000 characters (MSG02).
3. **CSV import (UC-21):**
   - Upload: one `.csv` file, UTF-8, ≤ 10 MB (MSG27, MSG28), with the required columns present (MSG29).
   - Import Preview: lists valid rows (Solution read-only) and invalid rows with error details (MSG30, MSG31). The PM can remove rows.
   - Confirm imports the valid rows (MSG32) and skips the invalid ones. Confirm is disabled when no valid row is left.
   - Every row belongs to the Project the PM selected. The writer comes from the optional `author` column.
4. **Jira import (UC-20):**
   - Pull: the PM picks a resolved date range (MSG01, MSG39). The pull returns at most 100 issues with no Ticket yet, oldest first (GBR-15); MSG35 when more match.
   - Preview: the PM writes a Solution per issue in the Import Ticket Preview popup, or one Solution for several issues (MSG36). The PM can remove issues.
   - Finish is enabled only when every remaining issue has the required sections. Issues that got a Ticket meanwhile are skipped (MSG38).
   - Errors: a Jira error records a Failed import (MSG50).
5. **Manual entry (UC-22):**
   - Fields: Ticket ID (≤ 50 characters, unique, MSG31), title, description, Solution, and optional Evidence (≤ 5 files of ≤ 10 MB each, allowed types; MSG34, MSG72; GBR-06).
   - Saved tickets are Published and their Evidence is locked.
6. **Import History (UC-23):** lists the imports of the PM's own Projects with source, date range or file name, published count, removed/error count, errors and date.
7. **Drafts (GBR-26):**
   - The PM can save the Import Preview (Jira or CSV) or the Manual entry tab as a private Draft (MSG41), reopen it, or delete it (MSG42).
   - Validation runs only on Finish/Confirm.
   - A Draft creates no Import Batch, and it is deleted after a successful import.
8. **Leaving and failure:**
   - Leaving the preview asks MSG37.
   - A Project that is no longer Active on Finish/Confirm blocks the import (MSG40).
   - A failed import imports nothing (MSG33) but is still recorded as Failed.
9. **Access:** only a PM, and only for Projects they manage.

### Non-Functional
1. CSV: a 10 MB file (about 5,000 tickets) is validated and previewed within **60 s**. One file per user at a time.
2. Jira import: the preview (≤ 100 issues) shows within **30 s**; Finish is confirmed within **5 s**.
3. All-or-nothing import: either every valid row becomes a ticket, or none does.
4. Imported tickets must become searchable (Search and Issue Panel) right after import.

## Constraints & Assumptions
- Modular monolith: Spring Boot 3.3.5, Java 21, PostgreSQL 16 + pgvector, Liquibase, REST `/api/v1/**` (architecture.md). Backend conventions: `backend/AGENTS.md`.
- CSV parsing per RESEARCH-001:
  - Apache Commons CSV.
  - Strip the BOM and decode strict UTF-8.
  - Raise the multipart limit to 10 MB (Spring's default is 1 MB).
  - Persist the parsed preview server-side, so it doubles as the Draft.
- Assumption: Project, Department, PM role and Jira project token come from other epics (workspace/identity). Until those exist, this module needs stubs or minimal tables for them.
- **Existing code conflict:** `backend/.../ticket/` already has `ResolvedTicketSnapshot`, `POST /api/tickets/import` and an **idempotent upsert** on `source + externalId`. The SRS requires **rejecting** an existing Ticket ID (MSG31). The existing model has no Project and no Solution sections; the Solution lives separately in `knowledge/Solution`. The plan must decide: extend or replace.

## Gaps and contradictions (to settle in the spec as `[NEEDS CLARIFICATION]`)
- Required Solution sections: GBR-33 says Symptoms and Resolution steps; the screen field and UC-19 say every section except Workaround.
- Change log 04/10 says "an import cannot be saved as a draft", but UC-20/UC-21 and GBR-26 (newer) allow Drafts. Assume Drafts are allowed.
- The full CSV template header is not specified: `description`? `resolved_date` and its format?
- Duplicate Ticket IDs inside one CSV file: no rule.
- Non-UTF-8 or `;`-delimited files: which message?
- Max length of title, description and author.
- Ticket ID format for Jira imports (the Jira key) and for CSV/manual: the same namespace?
- Retention of abandoned (non-Draft) previews.
- SRS v3460 on Drive is newer than the v3456 extract that was read.

## Risks
| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Project/Department/RBAC modules don't exist yet | High | High | Minimal tables or stubs behind service interfaces; replace them when those epics land |
| Existing `ticket` package conflicts with the SRS model | Medium | High | Decide extend vs replace in the plan; check callers with impact analysis first |
| 10 MB / 5,000-row preview within 60 s, with a uniqueness check | Medium | Low | Streaming parser, one batch ID query, batch insert |
| Search indexing (embeddings) on publish slows Confirm or fails it | Medium | Medium | Index after commit (async). Indexing is not part of the import transaction |
| SRS contradictions lead to rework | Medium | Medium | Settle the clarifications before approving the spec |

## Suggested Module Boundaries
- **import-ticket-list** (epic KAN-29): UC-20, UC-21, UC-22, UC-23 — Import Tickets screen (Jira / CSV / Manual tabs), Import Preview, Import Ticket Preview popup, Import Drafts, Import History, Import Batch. First feature: **KAN-31 CSV import (UC-21)**, then manual entry, import history, Jira import.
- Depends on (outside this brief, other epics): identity/RBAC (PM role), workspace (Project, Department, Jira token), search (indexing Published tickets). Until those modules are decomposed, they are not modelled in progress.json.

## Sources
- SRS Report 3 `T-Solve_Report3_Software_Requirement_Specification_EN_v3456` — extract at `SEP490_G89-TaiLieu/SEP490_G89/_context/Report/Minh_sua_report123/`. Sections used: UC-20 to UC-23, GBR-06/15/23/26/33, business rules, entities Ticket / Import History / Draft, screen fields, MSG27–MSG42, MSG50, Performance §2.2.
- `artifacts/01-research/RESEARCH-001-import-ticket-csv.md`
- `artifacts/03-modules/architecture.md`, `backend/` source (`ticket`, `knowledge` packages)
