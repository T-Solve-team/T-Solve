# T-Solve (SEP490_G89) — Project context

> Summary of the project docs, read by `/tsolve-context`. DOCS = your local copy of the team's Google Drive docs
> folder (env `TSOLVE_DOCS`); DOCS/_context/ holds Markdown copies made by `scripts/build_context.py`.
> Keep this file short; details live in the docs. Update it when a source-of-truth doc changes.

## What T-Solve is
Ticket Solution Repository Application — a knowledge layer next to **Jira Cloud** (does not replace it).
Loop: resolved Jira issue → Ticket Resolver writes Solution (AI-drafted) in the Solution Form → Project Manager
reviews → Published ticket → reused via Search / Issue Panel suggestions in Jira, AI Solution Suggestion on new issues
and the Knowledge Chatbot. Capstone FPT University, Fall 2026, supervisor Tô Thanh Hải.

## Source of truth in DOCS (first wins)
1. `Report/Sua_31-10/Report_3_BreakDown/` (31/10, master) — `UC_list_and_Actors.docx` (UC-01…53, UC-58 Chatbot
   creates an issue, UC-59 link ticket tracker account; 54–57 unused), `Business_Rules.docx` (GBR-01…40),
   `System_Messages.docx` (MSG01…157). Then `Report3/` (25/10): `CONTEXT.md` (glossary: use its terms, never its
   _Avoid_ words), `SRS_Delta.md` (decisions D1–D43, CR register); its ADR-0001 (pool model) is superseded by
   `docs/adr/0002-database-per-workspace.md` in this repo.
2. `Claude decisions/T-Solve_FE_spec_gap_Report3.md` — FE Lead decisions D1–D6 (win over Report3 where they differ;
   Report3 will be updated) · `T-Solve_UI_design_v3_decisions.md` (screens, menus, Figma) ·
   `T-Solve_Jira_ticket_convention.md`.
3. `Report/T-Solve_Report3_Software_Requirement_Specification_EN_v3460.docx` — **deprecated** (SRS_Delta lists what
   is wrong); still used only for the screen layouts of section III.
4. `Report/Report_1_-_fixed_v24.docx` (intro, scope), `Report/Report 2.docx` (plan).
5. **Superseded**, cross-check only: older SRS versions (e.g. `T-Solve_Report3_Software_Requirement_Specification_VI_v2.docx`),
   `UC draft.docx` (roles Staff / Department Manager, Cluster, Sensitive triage).

## Domain (Report3 — use these exact English terms)
- Roles (4.1, GBR-28): **Employee** (every account), **Ticket Resolver**, **Project Manager**, **Knowledge Manager**,
  **Admin**. A user can hold several; permissions add up. Only a Project Manager has a Department (exactly one);
  a Ticket Resolver belongs to the Departments of their Projects.
- Multi-tenant (ADR-0002, GBR-37): one deployment serves many companies, a **Workspace** per company on its
  **Workspace site** `{site name}.tsolve.io.vn`, **each with its own database** plus a catalog database. The T-Solve
  operations team creates, suspends and reactivates Workspaces with an ops script (GBR-24, GBR-38; no Platform
  Console, no Platform Operator actor). The Workspace is taken only from the host, the verified Jira `cloudId` or
  the job — never from the request. One email belongs to one Workspace (GBR-16).
- Workspace → Department → Project (linked to exactly one Jira project, exactly one Project Manager) → Ticket (exactly one Solution, no versions).
- Each Department has one Space (Wiki); a Space has folders (≤ 3 levels) and Wiki documents with visibility
  Department or Company-wide, and at most one Knowledge Manager.
- Ticket status: Pending review, Changes requested, Published, Rejected, Unpublished, Expired.
- Review Queue is real-time, per Project; review range default 3 days; JOB-02 expires overdue tickets at 00:00.
- Imported tickets (Jira import ≤100 issues by date range, CSV, manual) are Published immediately, no Review Queue.
- "issue" = Jira object, "Ticket" = T-Solve object. "Project Manager" = T-Solve role, not the team PM.

## Features (Report3 UC groups)
RBAC · Workspace & Department · Ticket tracker integration · Solution Authoring · Ticket Import & Repository ·
Intelligent Retrieval (Search) · Reuse & Feedback · Review & Approval · Dashboard & Audit · **AI Solution
Suggestion** (on a new Jira issue, a suggested solution when a source passes the relevance threshold; no Simple /
Complex) · **Wiki** (Space management, documents; re-upload overwrites, no versions, no review) · **Knowledge
Chatbot** (web only, cites its sources; creates a Jira issue when no answer is found, UC-58).

## Tech stack
FE React 19 + TypeScript + Vite + Ant Design 6 · BE Spring Boot 3 (Java 21, Maven, modular monolith) ·
PostgreSQL 16 + pgvector (one database per Workspace + a catalog database) · Liquibase · Docker Compose + Caddy wildcard
on an application VPS (Vietnam) · Jira App on Atlassian Forge (Forge Remote) · Google OIDC (central sign-in on `login.`) ·
Ollama for the LLM and bge-m3 embeddings on a dedicated AI VPS, private link only, no PII masking since content never leaves T-Solve's servers (D3, D41; no OpenRouter) ·
background jobs on JobRunr OSS (PostgreSQL storage, `worker` process, per-Workspace cap, D43) · SMTP email.
AI model choice pending benchmark (KAN-140, DOCS/`Notes/Chon_Model_AI.md`). Modules and owners:
`harnessclaude/artifacts/03-modules/module-map.md`.

## Team
| Member | Role | Owns (harness modules, per Jira assignees — D40) |
|---|---|---|
| Đặng Nhật Minh | PM, Scrum Master | `ticket-repository` (UC-20…25), `wiki` (UC-38…50); file storage, AI benchmark |
| Đặng Tấn Bình | BA, Document Owner | `workspace` (UC-10…15), `project-integration` (UC-16…18), `jira-app` |
| Trần Trung Hiếu | Dev FE / UI-UX, DevOps | `platform` (tenant layer, ops script, infrastructure), `fe-foundation`, `identity` UC-01…03, UC-59, `chatbot` web app (done) |
| Trần Hải Anh | Dev BE / DB | `be-foundation`, `ai`, `search` (UC-26…28), `triage` (UC-36…37), `chatbot` backend (UC-51…53, UC-58), dashboard (UC-34) |
| Phạm Tùng Dương | Tester / QA | `worker`, `identity` UC-04…09 + RBAC rules, `ticket-review` (UC-29…33), audit log (UC-35); test plan, E2E |

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
