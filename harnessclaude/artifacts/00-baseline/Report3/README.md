# Report3 source index

The authoritative sources live in the team documentation folder `Report3/`:

- `UC_list_and_Actors (1).docx`: UC-24 Ticket Detail, UC-29 Review Queue, UC-30 Review tickets, UC-32 Review History.
- `Business_Rules.docx`: GBR-04/05/08/09/10/11/23/27 and related review rules.
- `System_Messages.docx`: review messages MSG13–MSG19. MSG20/MSG70 await the D2 correction.

This index provides a portable manifest target; original team documents remain in the documentation folder. No personal filesystem path is required in repository configuration. The SRS v3460 document is a layout template and has no precedence over these sources.

## Approved FE Lead overrides awaiting document correction

D1 (21/10), reaffirmed in the PR review on 2026-10-22: keep FE-34 multi-ticket Request changes. The selected tickets share one comment form; the same comment applies to every selected ticket. Single-ticket wording in GBR-09 / UC-30 will be corrected.

D2, reaffirmed in that review: every Ticket has a populated Solution. Empty Solution is not a supported review state; remove MSG20 handling and empty-Solution flags. GBR-04 / GBR-08 / GBR-09 and MSG20 / MSG70 will be corrected. This PR changes the review catalog range only; the foundation change owns the remaining catalog regeneration.

## Review message verification

Verified from System_Messages.docx on 2026-10-22: MSG13 empty queue, MSG14 approval success, MSG15 rejection confirmation, MSG16 rejection success, MSG17 change-request success, MSG18 source restriction, MSG19 bulk approval confirmation. No new message text is invented for the batch change request.
