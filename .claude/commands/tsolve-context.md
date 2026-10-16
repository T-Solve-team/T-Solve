---
description: Load T-Solve requirements/design context from the docs folder (optional topic, e.g. "Wiki", "UC-30", "Review Queue")
argument-hint: [topic]
allowed-tools: Bash(python scripts/build_context.py*), Read, Grep, Glob
---

This repo is the T-Solve source code. Requirements and design docs live outside it, in the team's docs folder.
Setup (once per machine): see "AI context" in README.md.

## Converter output (first line `DOCS=...` is the docs folder; only changed docs are re-converted)
!`python scripts/build_context.py`

## Your task
If the converter output above is an error (docs folder not found, markitdown missing), show the user that message and stop.

Otherwise load T-Solve project context so you can answer questions and write code that matches the requirements.
DOCS below means the path printed as `DOCS=...`.

1. Read `.claude/tsolve-project.md` (overview, source-of-truth order, domain terms, tech stack, team). Do not repeat it back.
2. Read `DOCS/_context/INDEX.md` to see every document and its size.
3. Topic: `$ARGUMENTS`
   - **Topic given:** Grep `DOCS/_context/` for the topic and its IDs/synonyms (UC-xx, GBR-xx, MSGxx, API-xx, FE-xx,
     screen names, Vietnamese and English terms). Read the matching sections. For the SRS, read only the matching
     headings, not the whole file. If a newer doc (see the source-of-truth order) contradicts an older one, follow the
     newer one and note the conflict. If the topic is a feature, also locate the related code in this repo
     (`backend/`, `frontend/`) and say what already exists.
   - **No topic:** Read only the current status: `DOCS/_context/Week Plan - Ke hoach 4 tuan (15-10 den 15-11).xlsx.md`
     and sections A.1–A.3 of `DOCS/_context/T-Solve_Bo_sung_tinh_nang_Triage_Wiki_Chatbot_v1.docx.md`.
4. Do not read the reference folders (templates, samples, textbooks) or `Daily Report/`; they are not part of this context.
5. Diagrams: `.drawio.md` files list nodes and edges. For a `.png` listed in INDEX.md, Read the image directly if needed.
6. Never edit anything in DOCS from this repo.

Reply in Vietnamese, at most 10 lines: what you loaded, current phase and next deadlines, and conflicts or gaps you found
(for example: an outdated doc, a converter failure in INDEX.md, a requirement with no code yet). Then wait for the user's question.
