---
module: worker
date: 2026-11-06
total_tasks: 1
owner: Dev
---

# Tasks: worker — active iteration

Spec: artifacts/03-modules/worker/spec.md

## Task 1: SMTP review notification delivery (KAN-162)

**Story:** STORY-001
**Feature:** review-notification-smtp
**Description:** Implement and verify the SMTP delivery boundary for Reject and Request Changes. Cover AC-001.1 through AC-001.7 and master design edge cases 1–5. Check both messages against a local Mailpit server, reject invalid recipients before attempting delivery, confirm that transport failure is safe and limited to one attempt, and verify that the web context starts without an SMTP host.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/worker/client/ReviewNotificationSender.java`
- Test: `backend/src/test/java/vn/tsolve/worker/client/ReviewNotificationSenderTest.java`
- Test: `backend/src/test/java/vn/tsolve/worker/client/ReviewNotificationSenderIT.java`
- Modify: `backend/pom.xml`, `backend/src/main/resources/application.yml`, `.env.example`
**Dependencies:** none for local SMTP delivery. Job wiring, Workspace recipient lookup and retry follow KAN-161 and KAN-102. Review integration follows KAN-155.
**Estimated size:** small
