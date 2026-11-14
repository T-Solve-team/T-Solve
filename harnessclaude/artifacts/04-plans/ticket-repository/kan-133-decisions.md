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

## After the plan review (2026-11-14)

The plan was checked against the code and `origin/main`; the user chose "fix the plan". Decision 3 changed: PR #193
merged at 12:06, the branch was rebased onto `origin/main` (it was not pushed), so no task waits.

| # | Question | Options | Choice | Why |
|---|----------|---------|--------|-----|
| 13 | Caddy stops `/api/*` bodies above 12 MB | A) a 57 MB block for `/api/v1/imports/manual*`; B) raise `/api/*`; C) one file per request | A | Smallest opening; one request stays one transaction; `infra/caddy/Caddyfile` added to the module paths |
| 14 | The JSON `entry` part | A) backend reads it as `String` and parses it; B) `@RequestPart` object + a part content type from the client | A | B fails with 415 when the browser sends the part without `application/json`; A works with any generated client |
| 15 | Two requests on one Manual Draft (AC-007.9) | A) `lockByIdAndOwner` in each write, a kept id that is gone → 404 MSG04; B) no lock | A | Without it a Ticket can keep a file another request deletes after commit; MSG04 is the existing "not found" answer |
| 16 | `keepEvidenceIds` left out | A) keep every file; B) remove every file | A | A client that forgets the field must not delete Evidence; an empty list still removes all |
| 17 | Resolved date that is not a date (AC-006.5) | A) MSG39, the field is a string; B) Jackson's 400 | A | The contract already said MSG39; a `LocalDate` field would answer MSG06 before the validator runs |
| 18 | Stored Evidence type | A) lower-cased extension (`pdf`, `jpg`); B) MIME type | A | D18: the type follows the extension; no MIME table needed until Evidence is served inline |
| 19 | Failed batch without a preview | A) `recordJiraFailed` takes the source (`recordFailedWithoutPreview`); B) a second `recordManualFailed` | A | One method, not two copies |
| 20 | Ticket ID pre-check | A) existing `findExistingTicketIds`; B) new `existsByTicketId` | A | Already in `TicketRepository` |
| 21 | One request schema or two (Task 14) | A) one `ManualEntryRequest`, every field nullable, for Save and Draft; B) a second schema for the Draft | A | The spec says "the same parts"; the backend checks what Save needs (AC-006.3) |
| 22 | `MANUAL` in the contract | A) add it to `ImportSource`; B) a new enum for `ImportDraftSummary.source` only | A | One enum; `ImportPreviewResponse.source` never carries it because the preview endpoints answer 404 for a Manual Draft (AC-007.8) |
| 23 | MSG01 for several missing fields (AC-006.3) | A) one answer, `field_name` = the JSON names joined by ", ", shown below the first; B) a list of field errors | A | The coded-exception handler renders one field per exception and `common/` is not changed in a feature; the web app shows each field from its own checks (AC-006.12) |
| 24 | MSG02 for several too-long fields (AC-006.4) | A) the first one, in the order Ticket ID, title, description, Solution; B) all | A | MSG02 has one `max_length`, and the limits differ |
| 25 | Who the caller is in the tables | A) `TSolveUser.getName()` (the user id) for the batch, the decision and the Project check, as the other imports pass `Principal.getName()`; full name and id only for the writer | A | The same value the CSV and Jira imports store |
| 26 | The `entry` part in the controller | A) `@RequestParam String`; B) `@RequestPart String` | A | A text part of a browser `FormData` is a form field; both shapes arrive as a request parameter |
| 27 | Telling a taken Ticket ID from another constraint (AC-006.8) | A) the database message names `uq_ticket_ticket_id`; B) SQLState 23505 alone | A | The design asks for the constraint; 23505 would also match another unique index later |
| 28 | Database for `ManualEntryIT` | A) the container of `ImportConfirmIT`, same package; B) its own container | A | backend/AGENTS.md §8: a new IT starts no container of its own |
| 29 | When the files are stored (Task 17) | A) after the MSG31 pre-check, before the transaction; B) before the pre-check, as the design drew it | A | A taken Ticket ID then stores nothing; still outside the transaction (no object-storage call in it, backend/AGENTS.md §4) |
| 30 | No object storage configured (`tsolve.storage.endpoint` unset) | A) `Optional<FileStorage>`; files sent → MSG33 and a Failed batch, no files → saves; B) fail the app at start | A | Workspaces without storage can still enter tickets without files; a file is never dropped silently |
| 31 | A failed delete of a stored file | A) log the key and go on, answer stays MSG31/MSG33; B) fail the request differently | A | The ticket is not saved either way; an orphan object costs space only. The name is not logged (personal data) |
| 32 | The size saved in `file_size` | A) the bytes the storage wrote (`StoredFile.sizeBytes`); B) `MultipartFile.getSize` | A | The stored size is what the row describes |
| 33 | Edge 26 (5 × 10 MB in one request) | A) shown in `ManualEntryIT` at service level; B) a MockMvc test | A | MockMvc does not apply the servlet multipart limits, so B would prove nothing |
| 34 | A reopened Draft saved with another Project (Task 18) | A) the Draft takes the form's Project (`project_id` made updatable); B) keep the first Project | A | The form is the Draft; B would silently drop the PM's choice. Access is checked on the new Project |
| 35 | Object storage fails while a Draft is saved | A) 500 MSG33, the stored files deleted, no Import Batch; B) save the Draft without the files | A | A Draft is not an import, so no batch; dropping files silently would lose them |
| 36 | Where the Draft ITs live | A) `ManualEntryIT` (same Spring context, same container); B) a new `ManualDraftIT` | A | backend/AGENTS.md §8: a class with other beans builds a new context; trace still finds `Manual*` |
| 37 | Name of the Draft Evidence DTO | A) `DraftEvidenceResponse`, as in the contract; B) `dto/DraftEvidence` as tasks.md wrote | A | `entity/DraftEvidence` already exists (Task 15); the contract names the schema `DraftEvidenceResponse` |
| 38 | Delete of a Jira Draft | A) takes `lockByIdAndOwner` too; B) only a Manual one does | A | One code path; the lock costs nothing there |
| 39 | Saving a Draft again | A) every field takes the form's value, a cleared field is cleared; B) only the sent fields change | A | The web app sends the whole form, so A is what the PM sees |
| 40 | Reopening a Draft of a Project no longer managed | A) allowed (only the 404 cases of AC-007.8); B) 403 | A | The spec names no check there; Save and Save as draft check the Project |
| 41 | Evidence file picker (Task 19) | A) a native `<input type="file" multiple>` in the feature; B) a new multi-file kit component in `shared/ui` | A | `shared/ui` changes need the FE Lead (frontend AGENTS rule 10) and raw antd `Upload` is banned; the native input works now. `ponytail:` move it to the kit when another screen needs it |
| 42 | A chosen file of a wrong type or over 10 MB | A) not added, MSG34 under Evidence; B) added and blocks Save | A | Nothing wrong is ever sent; more than five files are kept and block Save with MSG72, so the PM picks which to remove |
| 43 | Opening a Manual Draft | A) the page keeps the open Draft's id and switches to the Manual entry tab; B) a URL `?manualDraftId=` | A | No route change (`app/` is not edited); the form is read from the Draft each time it opens |
| 44 | Button labels | A) "Save" and "Save as draft"; B) other words | A | GBR-26 names "Save as draft"; tasks.md names Save. No SRS layout of the tab was found in the docs |
| 45 | AC-008.1 of `import-ticket-list` said Manual entry is disabled | A) change request: AC-008.1 reworded, approved by the user, module reopened, Task 31 there; B) quick change | A | The user's choice on 2026-11-14; the spec must say what the code does |
