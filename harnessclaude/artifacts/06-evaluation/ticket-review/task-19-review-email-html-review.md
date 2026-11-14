---
module: ticket-review
feature: review-email-html
task: 19
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-review — Task 19: Branded HTML review email (STORY-010, KAN-333)

## Summary

Range `origin/main...875e6371` (commits dbeb3db7, 4a36c9cc), judged against `spec.md` STORY-010 (AC-010.1–010.9). Verdict PASS: no Critical or Major finding. Five Minor findings (test gaps, one blank-name fallback, a stale bug-log cell).

Verified by running: `mvn -o test -Dtest=ReviewEmailRendererTest,ReviewNotificationSenderTest,ReviewNotificationJobHandlerTest,WorkspaceSiteServiceImplTest,ReviewNotificationServiceImplTest` from `backend/`, exit 0; renderer 10, sender 9, handler 5, site service 2 tests, all 0 failures. `sdd.ts trace ticket-review STORY-010` gives 9/9. Read only (not run): `ReviewNotificationIT` and `ReviewNotificationSenderIT` (Docker is not available; PR #230 CI at 875e6371 reports them green, which I did not re-check), the native SQL, the template.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | See the per-AC table. |
| Completeness | pass | 9/9 criteria implemented and named in tests. One EXTRA: the sender also replaces CR/LF in the subject (see worker review, M-1). |
| Correctness | pass | Targeted unit tests pass; the ITs reported green by CI were not re-run. |
| Integration | pass | Worker reaches identity only through `identity/service/WorkspaceSiteService` (interface + `service/impl`) and ticket through `ReviewNotificationService` + `ticket/dto`. `findNotification` is `@Transactional(readOnly = true)` and returns before `sender.send`; the handler has no transaction, so no SMTP call runs inside a transaction. |

## Acceptance criteria

| AC | Implemented | Test | Evidence |
|----|-------------|------|----------|
| 010.1 | yes, unchanged | yes | `ReviewNotificationJobHandlerTest.usesItsOwnJobType_AC_010_1`, `ReviewNotificationIT.enqueues…_AC_010_1` |
| 010.2 | yes: subjects `ReviewEmailRenderer.java:36-43`; loads decision, ticket, Project, reviewer, writer `ReviewDecisionRecordRepository.java:80-96`; multipart and From "T-Solve" `ReviewNotificationSender.java:25-37`; plain part from the same record `ReviewEmailRenderer.java:68-95` | yes | `ReviewEmailRendererTest.rendersDecisionSubject_AC_010_2` (exact strings), `ReviewNotificationJobHandlerTest.passesRenderedContentAndTicketLink_AC_010_2_AC_010_8`, `ReviewNotificationIT.loadsTicketProjectNamesAndDecisionTime_AC_010_2_AC_010_7` |
| 010.3 | yes, unchanged | yes | `ReviewNotificationIT.keepsTheDecisionWhenEnqueueFails…_AC_010_3_AC_010_6` |
| 010.4 | yes: `DeliveryException` propagates from the handler | yes | `JobHandlerTest.failsTheJobSoItIsRetried…_AC_010_4_AC_010_6` |
| 010.5 | yes, unchanged | yes | `ReviewNotificationIT.sendsNothingForApprove…_AC_010_5` |
| 010.6 | yes: no logger in any new class; exceptions carry fixed text, no cause | yes | `JobHandlerTest` and `SenderTest.reportsOneSafeFailure…_AC_001_4_AC_001_6` use output capture |
| 010.7 | yes: header, badge, heading and greeting, card, reason, button, footer in this order in `review-notification.html:18-52`; colours and texts `ReviewEmailRenderer.java:97-122`; format `d MMM yyyy, HH:mm '(GMT+7)'` in Asia/Ho_Chi_Minh (`:17-19`); "Hi there"; reviewer `coalesce(full_name, email)` (SQL); table layout, inline styles, no `<img>`, no `http` in the template | partly | `rendersDecisionBadgeHeadingAndButton_AC_010_7_BUG_212` (badge, heading, "Rejection reason"/"Review comment", `>Open ticket</a>`), `fallsBackToHiThereAndReviewerEmail_AC_010_7`, `formatsVietnamTimeAcrossUtcDateBoundary_AC_010_7`, `omitsProjectSeparator…_BUG_213`. No test asserts section order or "no images/external resources" (m-1). |
| 010.8 | yes: link `https://<host>/tickets/<ticketId>` in `ReviewNotificationJobHandler.java`; button section removed when no URL; plain text carries the link; blank host gives no link | yes | `JobHandlerTest.omitsTicketLinkWhenSiteUnavailable_AC_010_8`, `RendererTest.omitsButtonLinkAndWorkspaceWhenHostMissing_AC_010_8`, `WorkspaceSiteServiceImplTest.*_AC_010_8` |
| 010.9 | yes: `HtmlUtils.htmlEscape` on every value including the href; CR, LF and CRLF become `<br>` for the feedback only; subject CR/LF become a space; single-pass `appendReplacement` with `quoteReplacement`, so `{{x}}` or `$1` in stored text stays literal | yes | `escapesStoredValuesAndPreservesLineBreaks_AC_010_9`, `replacesTicketKeyLineBreaksInSubject_AC_010_9` |

## Traceability

```
  ✓ AC-010.1  JobHandlerTest, ReviewNotificationIT
  ✓ AC-010.2  JobHandlerTest, ReviewEmailRendererTest, ReviewNotificationIT
  ✓ AC-010.3  ReviewNotificationIT
  ✓ AC-010.4  JobHandlerTest
  ✓ AC-010.5  ReviewNotificationIT
  ✓ AC-010.6  JobHandlerTest, ReviewNotificationIT
  ✓ AC-010.7  ReviewEmailRendererTest, ReviewNotificationIT
  ✓ AC-010.8  JobHandlerTest, ReviewEmailRendererTest
  ✓ AC-010.9  ReviewEmailRendererTest
trace ticket-review (STORY-010): 9/9 acceptance criteria have a test
```

## Issues

Critical: none. Major: none.

Security checks (read, plus the unit tests above): every stored value is escaped once, after the template sections are chosen, so stored `{{#button}}`, `{{feedback}}` or `$1` cannot be re-expanded. The `href` is escaped (a quote becomes `&quot;`) and its host comes from the platform catalog, not from user input. Subject and recipient cannot carry header breaks (subject CR/LF replaced twice; recipient rejected on CR/LF or more than one mailbox). SQL: the `left join project … or …` could match two rows only if one Project's `public_id` equalled another Project's zero-padded legacy id, which random UUIDs make practically impossible; the same predicate is used in `TicketRankingRepository` and `SearchTicket`. The reviewer join is by primary key. Not a finding.

Minor:
1. m-1 `ReviewEmailRendererTest` (AC-010.7): no test would fail if the sections were reordered or an `<img>` or external URL were added, and the Update-ticket check is a bare `contains("Update ticket")`. Fix: assert the `indexOf` order of header, badge, heading, card, reason, button, footer; assert no `<img`, `http://`, `url(`; use `>Update ticket</a>`.
2. m-2 `ReviewEmailRendererTest` (AC-010.9): no test puts `{{ticketTitle}}`, `{{#button}}` or `$1` in a stored value, so the single-pass design is unguarded. Fix: add a case with that text and assert it appears literally.
3. m-3 `ReviewDecisionRecordRepository.java:84` and `ReviewEmailRenderer.java` (`reviewerName`): a reviewer `full_name` of "" or " " (not null) bypasses the email fallback and renders "Reviewed by  ·"; the writer name has `hasText`, the reviewer name does not. Input: `app_user.full_name = ' '`. A deleted reviewer account also renders an empty name (IT `loadsNotificationWhenReviewerAccountMissing_AC_010_2_BUG_213` accepts that). Fix: `coalesce(nullif(trim(r.full_name), ''), r.email)` or a renderer fallback.
4. m-4 `docs/bug-log.md` BUG-212 and BUG-213 "Branch" cell says "Pending (uncommitted)" although the fix is committed (dbeb3db7). The test names in both rows exist and match. Fix: put the branch `feat/KAN-333-ticket-review-html-email`.
5. m-5 `ReviewNotificationJobHandler.java` builds `new ReviewEmailRenderer()` itself (stateless class that reads a classpath file); informational only.

Spec notes: AC-010.8 says `https://<slug>.<TSOLVE_DOMAIN>/…`; the code uses the catalog's site host, as decided in design.md "Link". The footer names the Workspace by its site host (design decision; the spec says "naming the Workspace"). Acceptable, no spec change needed.

## Test Report

Targeted unit run (exit 0): ReviewEmailRendererTest 10, ReviewNotificationSenderTest 9, ReviewNotificationJobHandlerTest 5, WorkspaceSiteServiceImplTest 2; 0 failures. ITs not run locally (no Docker); CI on PR #230 reported green at 875e6371.
