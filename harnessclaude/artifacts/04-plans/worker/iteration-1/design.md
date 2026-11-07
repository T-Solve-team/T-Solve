---
module: worker
date: 2026-11-06
---

# Design: worker — active iteration

Spec: artifacts/03-modules/worker/spec.md
Master: artifacts/04-plans/worker/design.md

## Architecture Overview

Deliver the KAN-162 SMTP sender for Reject and Request Changes. A future worker job supplies an address resolved in its Workspace. This slice exposes an internal sender and verifies delivery with a local SMTP fixture.

## Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Delivery | One Spring Boot Mail send per call under the worker profile, with a configured SMTP host | Keep SMTP wiring out of the web process and leave retry ownership with the future job |
| Message | Fixed subjects and UTF-8 plain-text bodies | Keep review feedback out of SMTP headers |
| Error | Safe delivery exception with no raw transport diagnostic | Avoid exposing recipient or message data |
| Verification | Mailpit in Testcontainers and focused sender and startup tests | Check actual SMTP delivery to synthetic recipients |

## Edge Cases & Risks

The SMTP delivery task covers rows 1–5 of the master design. Workspace recipient lookup in row 6 belongs to the later job integration.
