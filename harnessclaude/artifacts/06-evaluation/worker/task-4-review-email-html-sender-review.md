---
module: worker
feature: review-email-html-sender
task: 4
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: worker — Task 4: Generic SMTP sender (STORY-001, KAN-333)

## Summary

Range `origin/main...875e6371`, judged against `spec.md` STORY-001 (AC-001.1–001.7). Verdict PASS: no Critical or Major finding; two Minor findings.

Verified by running: `mvn -o test -Dtest=ReviewNotificationSenderTest,…` from `backend/`, exit 0, `ReviewNotificationSenderTest` 9 tests, 0 failures. `sdd.ts trace worker STORY-001` gives 7/7. Read only: `ReviewNotificationSenderIT` (needs Docker; PR #230 CI reports it green, not re-checked by me).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `ReviewNotificationSender.send(recipient, subject, text, html)` builds a multipart message with `MimeMessageHelper(message, true, "UTF-8")`, `setText(text, html)` and From `new InternetAddress(from, "T-Solve")`. |
| Completeness | pass | 7/7 criteria; one EXTRA (M-1 below). |
| Correctness | pass | 9/9 sender tests pass. |
| Integration | pass | Only `ReviewNotificationJobHandler` calls it, after `ReviewEmailRenderer`; no review-specific content in the sender (`Decision` moved to `ReviewEmail`). The class keeps `@Profile("worker")` and `@ConditionalOnProperty(spring.mail.host)`. |

## Acceptance criteria

| AC | Implemented | Test | Evidence |
|----|-------------|------|----------|
| 001.1 | yes: `ReviewNotificationSender.java:25-37` | yes | `sendsSubmittedMultipartUnchanged_AC_001_1_AC_001_2` checks subject, From personal "T-Solve" and address, one recipient, multipart with exactly one text/plain and one text/html part; `ReviewNotificationSenderIT` |
| 001.2 | yes: text and html passed as submitted; no review wording in the class | yes | same test compares both bodies for equality (non-ASCII included); see M-1 for the subject |
| 001.3 | yes: `singleMailbox` rejects null, CR/LF, group, several addresses and invalid syntax before any mail call | yes | `rejectsAnythingOtherThanOneMailboxBeforeDelivery_AC_001_3` (4 inputs, `verifyNoInteractions(mail)`) |
| 001.4 | yes: one attempt; the catch throws `DeliveryException` with fixed text and no cause | yes | `reportsOneSafeFailureWithoutLoggingMailData_AC_001_4_AC_001_6` (`hasNoCause`, one `send`) |
| 001.5 | yes, unchanged (config) | yes | `configuresFiniteMailTimeouts_AC_001_5` |
| 001.6 | yes: no logger; output capture asserts recipient, content, credential absent | yes | same test as 001.4 |
| 001.7 | yes, unchanged | yes | `webContextStartsWithoutSmtpSettings_AC_001_7` |

## Traceability

```
  ✓ AC-001.1  ReviewNotificationSenderTest, ReviewNotificationSenderIT
  ✓ AC-001.2  ReviewNotificationSenderTest, ReviewNotificationSenderIT
  ✓ AC-001.3  ReviewNotificationSenderTest
  ✓ AC-001.4  ReviewNotificationSenderTest
  ✓ AC-001.5  ReviewNotificationSenderTest
  ✓ AC-001.6  ReviewNotificationSenderTest
  ✓ AC-001.7  ReviewNotificationSenderTest
trace worker (STORY-001): 7/7 acceptance criteria have a test
```

## Issues

Critical: none. Major: none.

Minor:
1. M-1 `ReviewNotificationSender.java:31` and test `replacesSubjectLineBreaks_AC_001_1_AC_001_2`: the sender replaces CR/LF in the subject with a space, but AC-001.2 says the subject is sent "exactly as submitted". Input: subject `"a\nb"` goes out as `"a b"`. This is a sound header-injection guard and ticket-review AC-010.9 requires the same replacement upstream, but the worker spec does not state it. Fix: add the replacement to AC-001.2 (spec change) or drop it from the sender; user decision.
2. M-2 `ReviewNotificationSender.java:36`: the catch also turns an NPE from a null subject, text or html into `DeliveryException`, so JobRunr retries a programming error. Input: `send(WRITER, null, "t", "h")` gives `DeliveryException`. Fix: `Objects.requireNonNull` on the three arguments before the try (the spec does not require it; the handler never passes null).

Header injection through the recipient is covered by AC-001.3 (CR/LF rejected, tested with a `Bcc:` payload). No log line or exception message carries recipient, subject or body.

## Test Report

`ReviewNotificationSenderTest`: 9 tests, 0 failures (exit 0 of the targeted Maven run). `ReviewNotificationSenderIT` not run locally (no Docker); CI on PR #230 green at 875e6371.
