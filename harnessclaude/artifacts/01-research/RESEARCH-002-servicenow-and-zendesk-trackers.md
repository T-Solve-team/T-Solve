---
id: RESEARCH-002
title: ServiceNow and Zendesk as reference ticket trackers
question: How do ServiceNow and Zendesk integrate with an external SaaS and what data do they send, compared with Jira (Forge), so that the tracker schema and code can be made provider-neutral?
status: in-progress
date: 2026-10-28
related: [artifacts/03-modules/tracker/spec.md, artifacts/04-plans/tracker/design.md, artifacts/03-modules/jira-app/module.md]
---

# ServiceNow and Zendesk as reference ticket trackers

> 2026-10-28 addendum: the team will **not** integrate Zendesk now. Zendesk is studied only as a third
> reference, so the neutral model is checked against three differently shaped trackers instead of two
> (section "Zendesk (third reference)" and "Cross-check" below).

## Research Question
T-Solve is a SaaS product that will connect to more than one ticket tracker. The tracker module, its schema
(`jira_site`, `issue_event`) and the original ERD (Workspace.jira site address, Credential.Jira account email,
Project.Jira project key) are all shaped around Jira. What does a structurally different tracker — ServiceNow —
look like on each integration point, and which parts of our model must become provider-neutral?

## Context
- The tracker module is `implementing` (0/14 features) and changeset `002-jira-issue-events.sql` is only on
  the branch `feat/KAN-22-tracker-issue-created`, not on `main`. Changing the shape now is cheap; after
  customer data exists it needs a data migration.
- No spec, architecture or module artifact names any tracker other than Jira, so multi-tracker support is a
  **new requirement**: it goes into the tracker spec first, then the design, then code.
- Found while comparing: `resolved_ticket_snapshot` has `UNIQUE (source, external_id)`. Jira issue ids are
  unique only within one site, so two tenants that both have issue `10001` collide, and the second import
  overwrites the first tenant's snapshot (`ResolvedTicketSnapshot.update`). 2026-10-29: the team moves to
  one database per tenant, so two tenants never share this table and the collision cannot happen; not
  logged as a bug. The constraint still needs the connection in it once one tenant has several trackers.

## Findings

Confidence: **[docs]** = ServiceNow official docs · **[community]** = ServiceNow Community / third-party
integration guides, verify on a Personal Developer Instance (PDI) before relying on it.

### 1. Tenant and instance identity
- Every ServiceNow customer has its **own instance** (`https://<name>.service-now.com`), with its own custom
  fields, tables, roles and ACLs. [community]
- There is no global "cloud id" handed to us by the platform in each call, as Forge does with `context.cloudId`.
  The instance URL (or a value we issue at connection time) is the identity.
- Jira today: tenant = site, identified by `cloudId` inside a signed Forge Invocation Token (AC-001.1).

### 2. Connection and authentication
- Inbound REST (us calling ServiceNow) supports [docs, release notes]:
  - OAuth 2.0 **client credentials** (from the Washington DC release; off by default, enabled by the property
    `glide.oauth.inbound.client.credential.grant_type.enabled`), token at `POST /oauth_token.do`,
    `expires_in` about 1800 s [community];
  - **API key** (header `x-sn-apikey`) and HMAC, plugin `com.glide.tokenbased_auth` (Washington DC+);
  - OAuth authorization code / JWT bearer; Basic auth and password grant are legacy.
- The **customer's admin** creates the OAuth client or API key in their own instance (System OAuth >
  Application Registry) and gives it to us, plus a service user with roles `snc_platform_rest_api_access` and
  `itil` (HR cases: `sn_hr_core.*`). [community]
- Pitfall: a missing role can return **200 with an empty result** instead of 403, so a broken permission looks
  like "no data". [community]
- Jira today: no stored credential at all — Forge sends a short-lived system token (`x-forge-oauth-system`)
  with every event; we keep it encrypted only while the event is processed (AC-004.7/004.8).
- **Consequence:** with ServiceNow we must store a **long-lived secret per connection** (client id/secret or
  API key, encrypted at rest), refresh access tokens ourselves, and handle revocation. The per-event token model
  does not transfer.

### 3. How changes reach us
- ServiceNow has **no built-in app webhook** comparable to Forge product events. The usual patterns [docs,
  community]:
  1. **Push**: a Business Rule (after insert/update, condition e.g. `state` changes to Resolved) or a Flow
     Designer flow calls an **Outbound REST Message** (`sn_ws.RESTMessageV2`, ideally `executeAsync()` or via
     `gs.eventQueue` + Script Action so the user's transaction is not blocked). This configuration lives in
     the **customer's instance** — shipped as a scoped app / update set, or set up by their admin.
  2. **Pull**: we poll the Table API with `sysparm_query=sys_updated_on>…^ORDERBYsys_updated_on`, in time
     windows (offset paging gets slow at large offsets). [community]
- Outbound requests are not signed by the platform; we must agree on our own scheme (a shared secret header or
  an HMAC computed in the script; mutual TLS is available). [docs: outbound auth profiles, mutual auth]
- Delivery: no platform-guaranteed retry or ordering for a custom outbound call — whatever the script does.
- Jira today: Forge pushes `avi:jira:created:issue` / `updated:issue` with a signed FIT, retries on failure;
  we de-duplicate on `(cloud_id, issue_id, event_type, event_timestamp)` (AC-003.4).
- **Consequence:** the receive path must accept a **push from a script we provide** (auth = per-connection
  secret, not FIT) **and/or** a **poller** that creates the same internal events. Both must feed one event
  table and one processing worker.

### 4. Payload and the issue read
- A push contains whatever our Business Rule sends — we define it. Recommended: send only identifiers
  (`sys_id`, `number`, table, `sys_updated_on`, old/new state) and read the record back, the same
  "notify then fetch" shape we use for Jira (AC-004.2).
- Read: `GET /api/now/table/incident/{sys_id}?sysparm_fields=…&sysparm_display_value=all` (raw value plus
  display value for reference and choice fields). [community]
- Identifiers: `sys_id` (32-hex GUID, the stable key) and `number` (human key, e.g. `INC0010001`, the analogue
  of a Jira issue key). [community]
- Record type is a **table**, not an issue type: `incident`, `sc_req_item`, `problem`, HR cases in
  `sn_hr_core_case`. The same connection can carry several tables.

### 5. What "resolved" means
| Concept | Jira | ServiceNow incident |
|---|---|---|
| Done signal | status category = Done | `state` = 6 Resolved (7 Closed, 8 Canceled) [community; values can differ on upgraded instances] |
| Resolution text | resolution field + comments | `close_code` (resolution code) + `close_notes` (resolution notes) — mandatory on resolve [community] |
| Resolved time | resolution date | `resolved_at` |
| Resolver | assignee | `resolved_by` / `assigned_to` (reference to `sys_user`) |
| Grouping unit | project key | `assignment_group`, `category`, or the table itself |
| Reporter | reporter accountId | `caller_id` (reference to `sys_user`) |

ServiceNow gives a **structured resolution** (`close_notes`), which is better input for drafting a Solution
than Jira, where the fix is often buried in comments.

### 6. Writing back (the MSG80 comment)
- Write a comment by `PATCH /api/now/table/incident/{sys_id}` with `comments` (customer-visible "Additional
  comments") or `work_notes` (internal). Inserting into `sys_journal_field` directly does not show on the
  record. [community]
- Read comments: `sys_journal_field` filtered by `element_id = <sys_id>` and `element = comments|work_notes`;
  the table may need to be exposed to REST. [community]
- The PATCH returns the incident, **not the new journal entry's id** — so "did we already post?" (AC-005.4)
  cannot rely on a returned comment id; keep our own `comment_posted` flag (we already do).
- Visibility is a field choice (`comments` vs `work_notes`), not Jira's comment visibility property.

### 7. Users
- People are `sys_user` references (sys_id, email, user_name). Mapping a ServiceNow user to a T-Solve user
  needs a per-provider external identity (Jira: accountId).

### 8. Limits
- No universal published rate limit; admins set inbound rate-limit rules per user/group/instance; exceeding
  returns 429. [community; docs: inbound rate limiting]
- The existing retry policy (AC-004.5: 1, 5, 15 minutes on 5xx/429/timeout) fits as-is.

### 9. UI inside the tracker
- Jira has the Issue Panel through Forge (STORY-007/008/010). ServiceNow equivalents (UI Builder component,
  related list, UI action) would be a separate scoped app. Out of scope of this question; the backend API for
  the panel should not assume a Forge principal.

## Zendesk (third reference)

Confidence: **[docs]** = developer.zendesk.com / support.zendesk.com · **[community]** = third-party guides,
verify on a Zendesk trial before relying on it.

### Z1. Tenant identity
- Every customer is an account on its own subdomain (`https://<subdomain>.zendesk.com`); webhook payloads
  carry an `account_id`. [docs; envelope fields from community guides]
- Like ServiceNow and unlike Jira, the platform hands us no signed tenant id per call: the subdomain (or a
  connection id we issue) is the identity.

### Z2. Connection and authentication (us calling Zendesk)
- Two methods [docs]: **OAuth access tokens** (recommended; scoped, short-lived, `expires_in`; authorization
  code flow with optional refresh token, or client credentials acting as the user who created the client) and
  **API tokens** (Basic auth `{email}/token:{api_token}`, long-lived, act as that agent).
- A standard OAuth client works on **one account only**; a multi-account product needs a **global OAuth
  client**, which Zendesk must grant. [docs]
- **Consequence:** as with ServiceNow, we store a **long-lived secret per connection** (refresh token or API
  token, encrypted) and refresh access tokens ourselves.

### Z3. How changes reach us
- **Webhooks** [docs]. Two connection methods, fixed at creation:
  1. *Connected to a trigger or automation* — the way to receive ticket activity. The trigger's conditions
     choose when (e.g. ticket created; status changed to solved) and its action sends a **payload the admin
     writes** (we can ship a template).
  2. *Subscribed to Zendesk events* — the guide lists organization, user, help center and community events.
     A separate reference lists `zen:event-type:ticket.*` events (`created`, `status_changed`,
     `comment_added`, …). **Conflict between the two pages: verify on a trial whether ticket events can be
     subscribed to directly.**
- Webhooks are created by an admin in Admin Center or through the Webhooks API, in the customer's account.
- **Signature** [docs]: headers `X-Zendesk-Webhook-Signature` and `X-Zendesk-Webhook-Signature-Timestamp`;
  signature = `base64(HMAC-SHA256(secret, timestamp + body))`; the secret is per webhook (revealed in Admin
  Center or by API; can be reset, so we must support rotating it).
- Optional outbound auth on the request: API key, basic or bearer token. [docs]
- **Delivery** [docs]: best effort; duplicates possible; an event can be lost while the circuit breaker is
  open. Retries: HTTP 409 up to 3 times; 429/503 only with `retry-after` < 60 s; 12 s timeout, timeouts
  retried up to 5 times. Circuit breaker: ≥ 70 % errors or > 1,000 errors in 5 minutes (only with ≥ 100
  requests), pauses 5 s and retries. Trial accounts: 10 webhooks, 60 invocations per minute.
- **Consequence:** a 5xx other than 503 is **not retried** by Zendesk (unlike Forge). Our receive endpoint
  must answer fast (< 12 s) and only fail when it truly cannot store the event; a periodic **reconciliation
  poll** (incremental export, 10 req/min) is needed to recover lost events.

### Z4. Payload and the ticket read
- Ticket event `detail` holds a ticket snapshot (`id`, `status`, `subject`, `description`, `requester_id`,
  `assignee_id`, `group_id`, `tags`, `updated_at`, `via.channel`, …); `comment_added` carries the comment
  body and `is_public`. [docs]
- We still use "notify then fetch" (same as Jira and ServiceNow): read `GET /api/v2/tickets/{id}` and
  `GET /api/v2/tickets/{id}/comments` (cursor pagination, 100 per page; comment fields `id`, `public`,
  `body`, `html_body`, `plain_body`, `author_id`, `created_at`). [docs]
- Identifiers: numeric ticket `id` (unique within the account, shown to users as `#123`); there is no
  separate human key like `ITSUP-1` or `INC0010001`.

### Z5. What "resolved" means
| Concept | Zendesk |
|---|---|
| Statuses | `new`, `open`, `pending`, `hold`, `solved`, `closed`; with custom statuses enabled, `status` holds the category [docs] |
| Done signal | `solved` (can still be reopened by a reply); `closed` is final — a default automation closes solved tickets after 4 days (configurable up to 28, cannot be disabled), and a reply to a closed ticket creates a **follow-up ticket** [docs] |
| Resolution text | no resolution field: the fix is in the comments (public replies and internal notes), like Jira |
| Resolved time | no `solved_at` field on the ticket; take it from the status-change event or the ticket audits |
| Resolver | `assignee_id` |
| Grouping unit | `group_id`, `brand_id`, `form_id`, tags |

### Z6. Writing back
- No comment-create endpoint: add a comment by `PUT /api/v2/tickets/{id}` with
  `{"ticket": {"comment": {"body": …, "public": false}}}`; `public: false` = internal note. [docs]
- Whether the response returns the new comment id was not confirmed; keep our own `comment_posted` flag
  (as for ServiceNow).
- Limit: 30 updates per 10 minutes per user per ticket; 100 ticket updates per minute per account. [docs]

### Z7. Users
- `requester_id`, `assignee_id`, `author_id` are numeric Zendesk user ids within the account; mapping to a
  T-Solve user needs the same per-provider external identity as ServiceNow.

### Z8. Limits
- Account-wide 200–700 requests per minute depending on plan (2,500 with High Volume); 429 with
  `Retry-After` seconds. [docs] The existing retry policy fits, but it should honour `Retry-After`.

### Z9. UI inside the tracker
- Zendesk Apps Framework sidebar apps. With `signed: true` Zendesk sends a **RS256 JWT** (public key at
  `/api/v2/apps/{app_id}/public_key.pem`) on the **initial page request only**; later calls from the app to
  our backend need their own mechanism. [docs; the "initial page only" limit from a community post]
- Like ServiceNow's UI, out of scope; it confirms the panel API must not assume a Forge principal.

## Cross-check: the neutral model against three trackers

| Model element (Recommendations 1–4) | Jira | ServiceNow | Zendesk | Holds? |
|---|---|---|---|---|
| `tracker_connection.provider`, `external_instance_id` | cloudId | instance name | subdomain / `account_id` | Yes |
| `credential_ciphertext` nullable | none (token per event) | client secret / API key | refresh token / API token | Yes — plus `token_expires_at` and refresh for OAuth |
| Inbound auth per connection | FIT (RS256, platform JWKS) | shared secret we define | HMAC-SHA256 with per-webhook secret | **Add** `inbound_secret_ciphertext` (rotatable) to the connection; FIT needs none |
| Event arrives by push | Forge, retried | script we ship | trigger + webhook, best effort | Yes |
| Event arrives by poll | not needed | Table API | incremental export (reconciliation) | **Add** a per-connection poll cursor (`last_polled_at`) so lost pushes are recovered |
| `tracker_event` unique key `(connection_id, external_issue_id, event_type, occurred_at)` | yes | yes | yes — duplicates expected | Yes |
| Short-lived token stored on the event | yes | no | no | Keep the column nullable |
| `TrackerClient.readIssue` / `readComments` | REST | Table API + `sys_journal_field` | Tickets + Ticket Comments API | Yes |
| `TrackerClient.addComment(visibility)` | comment + visibility property | `comments` / `work_notes` | `public: true/false` | Yes — model visibility as INTERNAL / PUBLIC |
| `isResolved` per connection (`done_states`) | status category Done | state 6/7 | `solved` / `closed` | Yes — but **distinguish "resolved" from "final"**: Zendesk `solved` can reopen, `closed` cannot |
| Human key for display | `ITSUP-1` | `INC0010001` | none (`#id`) | Make `external_key` nullable or fill it with the id |
| Resolution text source | comments | `close_notes` | comments | `field_mapping` per tracker project covers it |
| Resolved time | resolution date | `resolved_at` | event time / audits | Take from the event when the issue has no field |
| Rate limit | 429 | 429 | 429 + `Retry-After` | Retry policy honours `Retry-After` |

Result: Recommendations 1–4 hold for all three. Zendesk adds four requirements to the model: a rotatable
inbound secret per connection, a poll cursor for reconciliation, a nullable human key, and a resolved/final
distinction. Its delivery is weaker than Forge's (no retry on most 5xx, possible loss), so the receive path
must be fast and the poller is not optional for best-effort trackers.

## Recommendations
1. **Introduce a provider-neutral connection.** Replace `jira_site` with `tracker_connection`
   (`workspace_id`, `provider` = JIRA | SERVICENOW | …, `external_instance_id` — Jira cloudId / ServiceNow
   instance name, `base_url`, `credential_ciphertext` nullable — Jira needs none, `settings jsonb`,
   `status`). Decide whether a Workspace may have more than one connection (open question 1).
2. **Generalise the event table.** `issue_event` → `tracker_event` keyed on `connection_id`
   (`external_issue_id`, `external_key`, `event_type`, `occurred_at`, `payload jsonb`), unique on
   `(connection_id, external_issue_id, event_type, occurred_at)`. Keep the status machine, retries, claim and
   clean-up exactly as specified — they are already provider-neutral.
3. **Separate "how an event arrives" from "how it is processed".** Inbound adapters (Forge FIT for Jira;
   shared-secret push and/or poller for ServiceNow) write the same `tracker_event`; one worker processes it
   through a `TrackerClient` interface (`readIssue`, `readComments`, `addComment`, `isResolved`) with
   `JiraTrackerClient` and later `ServiceNowTrackerClient` (fits `backend/AGENTS.md` §3, "External systems sit
   behind an interface").
4. **Normalise "resolved" per connection**, not globally: Jira = status category Done; ServiceNow = configured
   state values. The original ERD's Project.`Done status` / `Field mapping` already point that way — make them
   per tracker project (`tracker_project`: `connection_id`, `external_key`, `done_states`, `field_mapping jsonb`).
5. **Snapshot uniqueness**: with one database per tenant (decided 2026-10-29) there is no cross-tenant leak;
   make it `(connection_id, external_id)` when the neutral schema lands, so two trackers of one tenant with
   the same issue id do not collide.
6. **Scope:** keep the MVP *implementation* Jira-only, but write the spec and schema with the neutral names
   above, so ServiceNow is an added adapter later and not a migration. This is a spec change for the tracker
   module (re-approval needed).
7. Before writing the spec, **verify on a ServiceNow PDI** the [community] items: state values, mandatory
   `close_code`/`close_notes`, journal read through REST, client-credentials setup, and a Business Rule push.
8. **From the Zendesk cross-check**, add to the neutral model (still Jira-only implementation):
   `tracker_connection.inbound_secret_ciphertext` (nullable, rotatable) and `token_expires_at`; a poll cursor
   per connection; `tracker_event.external_key` nullable; `done_states` split into "resolved" and "final"
   (Jira and ServiceNow may map both to the same states); comment visibility as INTERNAL / PUBLIC; retries
   honour `Retry-After`.
9. Combine this spec change with the tenancy change (database per tenant, decided 2026-10-29; API gateway), since
   both touch `jira_site` → `tracker_connection` and the `workspace_id` columns — one spec revision, one
   re-approval.

## Open Questions
- Can one Workspace connect more than one tracker (e.g. Jira for IT, ServiceNow for HR)? This decides
  `UNIQUE (workspace_id)` on the connection.
- For ServiceNow, push (customer installs our Business Rule/scoped app) or pull (we poll), or both? Push needs
  a customer-side install; pull needs only credentials but adds latency and API load.
- Which ServiceNow tables are in scope: `incident` only, or also `sc_req_item` and HR `sn_hr_core_case`?
- How is a ServiceNow user linked to a T-Solve user (email match, explicit linking like Jira's principal)?
- Is the Issue Panel (STORY-007/008/010) required inside ServiceNow, or only in the T-Solve web app?
- Does the SRS/customer brief mention trackers other than Jira? If not, the multi-tracker requirement needs to
  be added to the SRS by its author.
- Zendesk: can ticket events be subscribed to directly (`zen:event-type:ticket.*`), or only through a
  trigger-connected webhook? The two official pages disagree.
- Zendesk: when a T-Solve Solution is drafted from a `solved` ticket that is later reopened, is the draft
  kept, re-drafted, or withdrawn? (Applies to any tracker where "resolved" is not final.)
- Zendesk: does the Update Ticket response return the new comment's id?

## Sources
- [ServiceNow API implementation and reference](https://www.servicenow.com/docs/r/api-reference/)
- [ServiceNow: Scripting outbound REST](https://www.servicenow.com/docs/r/api-reference/web-services/c_ScriptingOutboundREST.html?contentId=KsNlviZIvY_JAYNhlYJg0A)
- [ServiceNow: Outbound REST authentication](https://www.servicenow.com/docs/r/api-reference/web-services/c_OutboundRESTAuth.html?contentId=1~McWLNkoyVSHk6t_3zzDg)
- [ServiceNow: Identity and Authentication release notes](https://www.servicenow.com/docs/r/Iru_OmfY8svQHlgM89Nuiw/9EWWai3KP4s0pZaM8MjlYA)
- [ServiceNow: OAuth inbound](https://www.servicenow.com/docs/r/platform-security/authentication/oauth-inbound.html)
- [ServiceNow Developer blog: Inbound REST API keys](https://www.servicenow.com/community/developer-advocate-blog/inbound-rest-api-keys/ba-p/2854924)
- [ServiceNow Developer blog: Inbound client credentials with Washington](https://www.servicenow.com/community/developer-blog/up-your-oauth2-0-game-inbound-client-credentials-with-washington/ba-p/2816891)
- [Truto: How to integrate with the ServiceNow API (2026)](https://truto.one/blog/how-to-integrate-with-the-servicenow-api-2026-engineering-guide/)
- [ServiceNow Community: Incident states](https://www.servicenow.com/community/itsm-forum/incident-states/m-p/849798)
- [ServiceNow Community: Resolve/close an incident via REST](https://www.servicenow.com/community/developer-forum/how-do-i-resolve-close-an-incident-rest-api-fields/m-p/1690000)
- [ServiceNow Community: Comments/work notes added by REST](https://www.servicenow.com/community/developer-forum/incident-comments-or-work-notes-added-by-rest-api-not-visible-on/m-p/1350264)
- [ServiceNow Community: Posting comments via REST and getting their sys_id](https://www.servicenow.com/community/developer-forum/posting-comments-via-rest-api-and-getting-their-sys-id/m-p/2840349)
- [DEV Community: Creating a webhook in ServiceNow](https://dev.to/sebasqui/creating-a-webhook-in-servicenow-a-step-by-step-guide-1j7c)
- [Zendesk: Verifying webhook authenticity](https://developer.zendesk.com/documentation/webhooks/verifying/)
- [Zendesk: Creating and monitoring webhooks](https://developer.zendesk.com/documentation/webhooks/creating-and-monitoring-webhooks)
- [Zendesk: Webhooks API introduction](https://developer.zendesk.com/api-reference/webhooks/introduction/)
- [Zendesk: Ticket events](https://developer.zendesk.com/api-reference/webhooks/event-types/ticket-events)
- [Zendesk: Tickets API](https://developer.zendesk.com/api-reference/ticketing/tickets/tickets/)
- [Zendesk: Ticket Comments API](https://developer.zendesk.com/api-reference/ticketing/tickets/ticket_comments/)
- [Zendesk: Rate limits](https://developer.zendesk.com/api-reference/introduction/rate-limits/)
- [Zendesk: API tokens to OAuth](https://developer.zendesk.com/documentation/authentication/api-tokens-to-oauth/)
- [Zendesk: Creating and using OAuth tokens](https://developer.zendesk.com/documentation/authentication/creating-and-using-oauth-tokens-with-the-api/)
- [Zendesk: Why do solved tickets change to closed](https://support.zendesk.com/hc/en-us/articles/4408883475354-Why-do-solved-tickets-change-to-a-closed-status)
- [Zendesk: Building a server-side app, part 5 — secure the app](https://developer.zendesk.com/documentation/apps/build-an-app/building-a-server-side-app/part-5-secure-the-app/)

## Related Artifacts
- Spec: artifacts/03-modules/tracker/spec.md
- Design: artifacts/04-plans/tracker/design.md
- Module: artifacts/03-modules/jira-app/module.md
- Architecture: artifacts/03-modules/architecture.md
