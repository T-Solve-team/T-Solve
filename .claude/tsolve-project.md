# T-Solve (SEP490_G89) — Project context

> Summary of the project docs, read by `/tsolve-context`. DOCS = your local copy of the team's Google Drive docs
> folder (env `TSOLVE_DOCS`); DOCS/_context/ holds Markdown copies made by `scripts/build_context.py`.
> Keep this file short; details live in the docs. Update it when a source-of-truth doc changes.

## What T-Solve is
Ticket Solution Repository Application — a knowledge layer next to **Jira Cloud** (does not replace it).
Loop: resolved Jira issue → Ticket Resolver writes Solution (AI-drafted) in the Solution Form → Project Manager
reviews → Published ticket → reused via Search / Issue Panel suggestions in Jira, AI Ticket Triage on new issues
and the Knowledge Chatbot. Capstone FPT University, Fall 2026, supervisor Tô Thanh Hải.

## Source of truth in DOCS (first wins)
1. `Report3/` — `UC_list_and_Actors.md` (4.1 Actors, 4.2 UC-01…53, 4.4 Permission Matrix), `Business_Rules.md`
   (GBR-01…36), `System_Messages.md` (103 messages), `Architecture.jpg` (left as is, D6).
2. `Claude decisions/T-Solve_FE_spec_gap_Report3.md` — FE Lead decisions D1–D6 (win over Report3 where they differ;
   Report3 will be updated) · `T-Solve_UI_design_v3_decisions.md` (screens, menus, Figma) ·
   `T-Solve_Jira_ticket_convention.md`.
3. `Report/T-Solve_Report3_Software_Requirement_Specification_EN_v3460.docx` — screen layouts (section III).
4. `Report/Report_1_-_fixed_v24.docx` (intro, scope), `Report/Report 2.docx` (plan).
5. **Superseded**, cross-check only: older SRS versions (e.g. `T-Solve_Report3_Software_Requirement_Specification_VI_v2.docx`),
   `UC draft.docx` (roles Staff / Department Manager, Cluster, Sensitive triage).

## Domain (Report3 — use these exact English terms)
- Roles (4.1, GBR-28): **Employee** (every account), **Ticket Resolver**, **Project Manager**, **Knowledge Manager**,
  **Admin**. A user can hold several; permissions add up. Only a Project Manager has a Department (exactly one);
  a Ticket Resolver belongs to the Departments of their Projects.
- Workspace (one per installation, single-tenant) → Department → Project (linked to exactly one Jira project, exactly one Project Manager) → Ticket (exactly one Solution, no versions).
- Each Department has one Space (Wiki); a Space has folders (≤ 3 levels) and Wiki documents with visibility
  Department or Company-wide, and at most one Knowledge Manager.
- Ticket status: Pending review, Changes requested, Published, Rejected, Unpublished, Expired.
- Review Queue is real-time, per Project; review range default 3 days; JOB-02 expires overdue tickets at 00:00.
- Imported tickets (Jira import ≤100 issues by date range, CSV, manual) are Published immediately, no Review Queue.
- "issue" = Jira object, "Ticket" = T-Solve object. "Project Manager" = T-Solve role, not the team PM.

## Features (Report3 UC groups)
RBAC · Workspace & Department · Ticket tracker integration · Solution Authoring · Ticket Import & Repository ·
Intelligent Retrieval (Search) · Reuse & Feedback · Review & Approval · Dashboard & Audit · **AI Ticket Triage**
(Simple / Complex on a new Jira issue; a suggested solution on Simple ones) · **Wiki** (Space management, documents)
· **Knowledge Chatbot** (web only, cites its sources).

## Tech stack
FE React 19 + TypeScript + Vite + Ant Design 6 · BE Spring Boot 3 (Java 21, Maven, modular monolith) ·
PostgreSQL 16 + pgvector · Liquibase · Docker Compose + Caddy on a VPS (Vietnam) · Jira App on Atlassian Forge (Forge Remote) ·
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
- Grep DOCS/_context/Report3/*.md by ID (UC-xx, GBR-xx, MSGxx) instead of reading whole files; the SRS v3460 (~49k tokens) only for a screen's section III layout.
- Repo code may still use older names (e.g. README lifecycle DRAFT → ARCHIVED); the Report3 terms above are the target.
