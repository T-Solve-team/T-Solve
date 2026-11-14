---
module: ticket-repository
story: KAN-133 (UC-22 Create a ticket with its Solution manually)
date: 2026-11-14
---

# KAN-133 planning decisions

Taken by Claude while planning, under the user's standing instruction of 2026-11-11 (pick the best option, log it,
ask only for push / merge / PR). Each is also in the spec (D12–D18) or the design. The user can overturn any of them
before approving the spec.

| # | Question | Options | Choice | Why |
|---|----------|---------|--------|-----|
| 1 | Branch | Keep working on `feat/KAN-127-…`; new branch from `origin/main` | `feat/KAN-133-ticket-repository-manual-entry` from `origin/main` (b3c7fe60, has KAN-132) | Team rule: one branch per Jira issue from an up-to-date `main` |
| 2 | Size | Quick; Change; Module | Change Mode of `ticket-repository` (new STORY-006, STORY-007) | Adds acceptance criteria to an existing, approved module |
| 3 | Evidence in KAN-133 (D12) | A) now, on `platform` `FileStorage` of PR #193 (KAN-96, open); B) ship without Evidence, add it later; C) own storage | A — the Evidence tasks start after PR #193 merges, like KAN-45 was stacked on PR #110 | The story's AC names Evidence; `FileStorage` already exists in #193; C duplicates platform work |
| 4 | Where a Manual Draft lives (D13) | A) an `import_preview` of source MANUAL with one row; B) a new `ticket_draft` table | A — plus a JSONB `evidence` column on `import_preview` | The Drafts list, ownership, 404 rules and cleanup exemption already work on `import_preview`; no new table, so no tenant-convention exemption |
| 5 | Writer of a manual Ticket (D14) | A) caller's full name + id from `TSolveUser`; B) a free-text field | A | UC-22 step 6: writer = Project Manager; `TSolveUser` already carries `id` and `fullName` |
| 6 | Resolved date in the future (D15) | A) MSG39; B) new message; C) no backend check | A, and the date picker has no future day | SRS screen 5.1 says "not in the future" but names no message; MSG39 already says "neither date may be in the future"; never invent message text |
| 7 | Validation of a Draft (D16) | A) nothing; B) Project + files only | B — Project required (MSG01) and managed (403); Evidence type / size / count checked (MSG34, MSG72); nothing else | GBR-26: fields are not validated; a Draft needs its Project for the Drafts list and access, and files are checked before they are stored |
| 8 | Import Batch of a manual entry (D17) | One per Save; none | One Completed batch (1 published) per Save; a Failed batch only when saving fails after validation (MSG33) | BR "Each import is one Import Batch", incl. Failed; validation errors are not imports, as in AC-003.4–003.6 |
| 9 | Evidence file check | Extension only; extension + content sniffing | Extension (case-insensitive) + size; stored type from the extension; files are only ever served as downloads | Smallest check that meets MSG34; `ponytail:` content sniffing when Evidence is shown inline |
| 10 | Multipart limits | Keep 10 MB / 11 MB; raise | `max-file-size: 11MB`, `max-request-size: 56MB`; the service checks 10 MB per file (MSG34) | 5 × 10 MB must fit one request; above 11 MB the global handler still answers MSG28 (web app pre-checks, so users meet MSG34) |
| 11 | Risk review | Run risk-review; skip | Skip; edge-cases run inline | Not auth/payments/migration of data; the upload trust boundary is covered by AC-006.6/006.7 and edge cases |
| 12 | Builder check | Subagent; inline | Inline | Session rule: no subagents unless the user asks |
