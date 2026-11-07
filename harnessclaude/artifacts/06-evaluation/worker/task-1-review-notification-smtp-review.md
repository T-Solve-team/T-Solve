---
module: worker
feature: review-notification-smtp
task: 1
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: Claude independent reviewer subagent plus parent verification
iteration: 1
---

# Review: worker — Task 1: review-notification-smtp

## Summary

PASS for STORY-001 (KAN-162). The independent reviewer checked the code in 49231eb8 against the spec only. It found every acceptance criterion implemented and proven by a test, and no CRITICAL issues. The parent session ran the feature's unit tests and the Mailpit integration test, and all passed. The sender is not called by any production code yet, which the spec puts out of scope (job wiring KAN-102/KAN-161, trigger KAN-155).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Reject and Request Changes produce plain-text mail to exactly one writer address; failures surface as a data-free DeliveryException. |
| Completeness | pass | AC-001.1–001.7 implemented; `.env.example` documents SMTP variables without credentials. |
| Correctness | pass | 7 unit tests and 1 Mailpit integration test pass; multi-address, group, CRLF, Unicode and failure cases covered. |
| Integration | pass | `send(Decision, recipient, ticketKey, feedback)` and `DeliveryException` match the spec's interface contract. |

## Traceability

```
  ✓ AC-001.1  backend/src/test/java/vn/tsolve/worker/client/ReviewNotificationSenderIT.java
  ✓ AC-001.2  backend/src/test/java/vn/tsolve/worker/client/ReviewNotificationSenderIT.java
  ✓ AC-001.3  backend/src/test/java/vn/tsolve/worker/client/ReviewNotificationSenderTest.java
  ✓ AC-001.4  backend/src/test/java/vn/tsolve/worker/client/ReviewNotificationSenderTest.java
  ✓ AC-001.5  backend/src/test/java/vn/tsolve/worker/client/ReviewNotificationSenderTest.java
  ✓ AC-001.6  backend/src/test/java/vn/tsolve/worker/client/ReviewNotificationSenderTest.java
  ✓ AC-001.7  backend/src/test/java/vn/tsolve/worker/client/ReviewNotificationSenderTest.java
trace worker (STORY-001): 7/7 acceptance criteria have a test
```

## Issues

None blocking. Informational only:

1. `ReviewNotificationSenderTest.java:82-88` (AC-001.7): the test runs without the `worker` profile, so `@Profile("worker")` alone removes the sender. The case "worker profile on, no `spring.mail.host`" is not tested. A `SPRING_MAIL_HOST` variable on the machine could also change the result. Suggested fix: add that case and expect startup with no sender bean.
2. `ReviewNotificationSender.java:33-35`: the cause is dropped entirely, so operators see no reason for a failure. Suggested fix: record only the exception class or the SMTP reply code, never the message.
3. `application.yml:10-12`: a timeout of 0 or a negative value silently disables the AC-001.5 timeout. With `spring.mail.protocol=smtps`, JavaMail reads `mail.smtps.*`, so these keys would not apply. Suggested fix: validate the timeouts are greater than 0 at startup, and either document STARTTLS-only or set the `mail.smtps.*` keys too.
4. `ReviewNotificationSender.java:25-30`: a null `decision` throws a NullPointerException, and a null `ticketKey` or `feedback` sends the text "null". The spec does not cover this. Suggested fix: reject null or blank values with IllegalArgumentException.
5. `application.yml:1-2`: the header says the file has no default values, but the timeouts have defaults. Suggested fix: reword the header.

The reviewer also suspected an address without a domain might be accepted. The parent checked this with jshell: `writer` and `writer@` are rejected. That finding is withdrawn.

AC-001.4 is proven against a mocked MailSendException, not a real refusal or timeout. This is accepted because Spring's JavaMailSenderImpl maps both to MailSendException.

## Test Report

See `task-1-review-notification-smtp-test-report.md`. ReviewNotificationSenderTest 7/7 and ReviewNotificationSenderIT 1/1 pass (BUILD SUCCESS, 1 min 28 s). The full suite and Spotless were not rerun, because the files have not changed since the verified merge.
