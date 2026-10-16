# T-Solve (SEP490_G89) — Project context

> Summary of the project docs, read by `/tsolve-context`. DOCS = your local copy of the team's Google Drive docs
> folder (env `TSOLVE_DOCS`); DOCS/_context/ holds Markdown copies made by `scripts/build_context.py`.
> Keep this file short; details live in the docs. Update it when a source-of-truth doc changes.

## What T-Solve is
Ticket Solution Repository Application — a knowledge layer next to **Jira Cloud** (does not replace it).
Loop: resolved Jira issue → Staff writes Solution (AI-drafted) in the Solution Form → Project Manager reviews →
Published ticket → reused via Search / Issue Panel suggestions in Jira. Capstone FPT University, Fall 2026,
supervisor Tô Thanh Hải.

## Source of truth in DOCS (newest wins)
1. `Report/T-Solve_Report3_Software_Requirement_Specification_EN_v3456.docx` — SRS: 37 UC, GBR-01..33, MSG01..89, API-01..05, JOB-02.
2. `T-Solve_Bo_sung_tinh_nang_Triage_Wiki_Chatbot_v1.docx` (14/10) — adds single-tenant, FE-10..12, UC-38..49 (being merged into Reports 1/3).
3. `Report/Report_1_v26.docx` (intro, scope), `Report/Report2_v1.4.docx` (plan), `Report/Report 4 - Phan cong chi tiet v3.docx` (Report 4 tasks).
4. **Outdated**, cross-check only: `T-Solve_Use_Case_List.docx`, `UC draft.docx` (29/09, still has Department Manager, Cluster, 18:00 daily queue — all removed 03/10).

## Domain (from SRS glossary — use these exact English terms)
- Roles (one fixed role per user): **Guest, Staff, Project Manager, Admin**. No Department Manager role.
- Workspace (one per installation, single-tenant) → Department → Project (linked to exactly one Jira project, exactly one Project Manager) → Ticket (exactly one Solution, no versions).
- Ticket status: Pending review, Changes requested, Published, Rejected, Unpublished, Expired.
- Review Queue is real-time, per Project; review range default 3 days; JOB-02 expires overdue tickets at 00:00.
- Imported tickets (Jira import ≤100 issues by date range, CSV, manual) are Published immediately, no Review Queue.
- "issue" = Jira object, "Ticket" = T-Solve object. "Project Manager" = T-Solve role, not the team PM.

## Features
FE-01 Semantic Search · FE-02 RBAC · FE-03 Solution Authoring · FE-04 Review & Approval · FE-05 Workspace & Department ·
FE-06 Jira Integration · FE-07 Ticket Import & Repository · FE-08 Reuse & Feedback · FE-09 Dashboard & Audit ·
New (14/10): FE-10 AI Ticket Triage (Simple/Complex/Sensitive on Jira Create issue) · FE-11 Wiki Management · FE-12 Knowledge Chatbot (web only, cites sources).

## Tech stack
FE React 19 + TypeScript + Vite + Ant Design 6 · BE Spring Boot 3 (Java 21, Maven, modular monolith) ·
PostgreSQL 16 + pgvector · Flyway · Docker Compose + Caddy on a VPS (Vietnam) · Jira App on Atlassian Forge (Forge Remote) ·
Google OIDC · Ollama + Qwen3 (4B classify, 8B generate; local, PII-masked) · Embedding bge-m3 via OpenRouter · SMTP email.
AI model choice pending benchmark (DOCS/`Notes/Chon_Model_AI.md`).

## Team
| Member | Role | Owns (code, from 15/10 plan) |
|---|---|---|
| Đặng Nhật Minh | PM, Scrum Master | Import, Audit Log, Wiki, Vietnamese labelled dataset |
| Đặng Tấn Bình | BA, Document Owner | User, Department, Project, Jira App (Create-issue suggestion) |
| Trần Trung Hiếu | Dev FE / UI-UX | Login, Solution Form (Jira App), Chatbot |
| Trần Hải Anh | Dev BE / DB / DevOps | Search, Feedback, Dashboard, AI Triage |
| Phạm Tùng Dương | Tester / QA | Review, Background Job, RBAC, Deployment, code base |

## Timeline
- 15/10–15/11: finish product (Report 3 features + FE-10..12) and Report 4. Testing after 15/11.
- Report 4 (SDS): internal 31/10, official 07/11. Demo on VPS 15/11.
- Live plan: DOCS/`Week Plan - Ke hoach 4 tuan (15-10 den 15-11).xlsx`.

## Known open questions
- Forge: can the Jira App fill suggestions into the Create issue form? Fallback = Issue Panel after creation.
- Ollama speed without GPU (Qwen 3B/7B); bge-m3 quality on Vietnamese.
- Report 1 LI-05 says "English and Vietnamese" but its change log (08/10) says English only — undecided.

## DOCS folder map
`Report/` team reports · `Notes/` Q&A notes (one .md per topic) · `MOM các buổi họp/` meeting minutes ·
`Business Flow/` BF-01 diagram (drawio/png) · `JIRA Connector/` Jira API notes · `Thiet Kế Giao Diện/` Figma link ·
`Workshop/` course intro & planning · Reference only, not T-Solve content: `SEP490 Templates-*`, `Sample Doc Do An/`,
the two textbook PDFs. `Daily Report/` is excluded from the AI context.

## Working with docs
- Read DOCS/_context/ copies (`<original name>.md`), never edit them or anything else in DOCS from this repo.
- Index with token sizes: DOCS/_context/INDEX.md.
- SRS is ~49k tokens: grep DOCS/_context/Report/T-Solve_Report3_*.md by heading/ID (UC-xx, GBR-xx, MSGxx) instead of reading it all.
- Repo code may still use older names (e.g. README lifecycle DRAFT → ARCHIVED); the SRS ticket statuses above are the target.
