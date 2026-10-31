---
module: platform
date: 2026-10-25
total_tasks: 4
owner: Dev
iteration: 2
---

# Tasks: platform — iteration 2 (31/10–13/11)

Spec: `artifacts/03-modules/platform/spec.md` · Design: `artifacts/04-plans/platform/iteration-2/design.md`
(master: `artifacts/04-plans/platform/design.md`).

Task numbers continue those of iteration 1. "Edge #n" refers to the Edge Cases table of the design.

---

## Task 6: Verify the Forge Invocation Token

**Story:** STORY-005
**Feature:** forge-token
**Assignee:** Trần Trung Hiếu (KAN-105)
**Description:** Add `spring-security-oauth2-jose` to `backend/pom.xml` (approved, P3). `NimbusForgeTokenVerifier`
(`NimbusJwtDecoder.withJwkSource` over a caching Nimbus `JWKSource` from `FORGE_JWKS_URL` with no rate limit, so an
unknown key id refetches once; validators: `JwtTimestampValidator(Duration.ZERO)`, issuer `forge/invocation-token`,
audience `ari:cloud:ecosystem::app/` + `FORGE_APP_ID`); `ForgeTokenFilter`; `ForgeCallContext` (request-scoped);
`ForgeCallAuthentication`; `ForgeSecurityConfig` (`@Order(1)`, host `api.<base>`, stateless, no CSRF, no cookie).
First step, before any code or fixture: confirm from Atlassian's Forge Remote documentation the claim paths of the
`cloudId`, the principal's account id and any site URL, and the lifecycle body's `eventType` values; record them in
the master design. Covers AC-005.1 – AC-005.8. Tests: forged and expired tokens (Edge #15); key set
unreachable and unknown key id (Edge #16); a Forge token on a Workspace site authenticates nothing (Edge #17); no
`Set-Cookie` on `api.`; no Workspace lookup on a refused token (registry mock never called); logs carry no token
(Edge #41).
**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/vn/tsolve/platform/forge/ForgeTokenVerifier.java`, `NimbusForgeTokenVerifier.java`,
  `ForgeTokenFilter.java`, `ForgeCallContext.java`, `RequestForgeCallContext.java`, `VerifiedForgeCall.java`,
  `ForgeCallAuthentication.java`
- Create: `backend/src/main/java/vn/tsolve/platform/config/ForgeSecurityConfig.java`
- Modify: `backend/src/main/resources/application.yml`, `.env.example` (`FORGE_APP_ID`, `FORGE_JWKS_URL`)
- Test: `backend/src/test/java/vn/tsolve/platform/NimbusForgeTokenVerifierTest.java`
- Test: `backend/src/test/java/vn/tsolve/platform/ForgeSecurityIT.java`
**Dependencies:** iteration 1 Task 5
**Estimated size:** medium

---

## Task 7: Tenant Resolver of the Jira App and Jira site registration

**Story:** STORY-006
**Feature:** jira-tenant-resolver
**Assignee:** Trần Trung Hiếu (KAN-101; contract agreed with Đặng Tấn Bình through the spec)
**Description:** In `ForgeTokenFilter`: verified `cloudId` → `findByCloudId` → bind; MSG124 (`tracker_name` = `Jira`)
and MSG121 as 403. `ForgeLifecycleController` (`POST /api/v1/jira-app/lifecycle`, no Workspace bound);
`JiraSiteMatcher`: signed site URL first, else `TenantInfoClient` on every unbound site in parallel (public-address
guard, `https`, no redirects, 5 s timeouts, 64 KB cap, overall deadline 10 s); any failed probe → store nothing, 503
(AC-006.11); conditional update `cloud_id IS NULL`; uninstalled → no change. Check Atlassian's retry rules for
Forge Remote events: if a 4xx is retried, tell Đặng Tấn Bình and raise a spec change before closing the task (design
builder check #12). Tests use a test-only event handler to prove nothing runs for a Suspended Workspace. Covers
AC-006.1 – AC-006.14. Tests: body site URL ignored (Edge #18); private or IP-literal site URL never called (Edge #19);
two installs at once (Edge #20); replay (Edge #21); one lookup fails (Edge #22) → nothing stored, 503;
Suspended Workspace's issue event → MSG121, nothing recorded (Edge #23); conflicts logged; Suspended Workspace still
bound on install; isolation case AC-006.14.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/platform/forge/ForgeTokenFilter.java`
- Create: `backend/src/main/java/vn/tsolve/platform/controller/ForgeLifecycleController.java`
- Create: `backend/src/main/java/vn/tsolve/platform/service/JiraSiteMatcher.java`
- Create: `backend/src/main/java/vn/tsolve/platform/service/impl/JiraSiteMatcherImpl.java`
- Create: `backend/src/main/java/vn/tsolve/platform/client/TenantInfoClient.java`, `RestTenantInfoClient.java`
- Modify: `frontend/openapi/tsolve-api.yaml` (the lifecycle endpoint, for the contract)
- Test: `backend/src/test/java/vn/tsolve/platform/JiraSiteMatcherImplTest.java`
- Test: `backend/src/test/java/vn/tsolve/platform/ForgeLifecycleIT.java`
- Test: `backend/src/test/java/vn/tsolve/common/tenant/TenantIsolationIT.java` (Jira App case)
**Dependencies:** Task 6; iteration 1 Task 4
**Estimated size:** medium

---

## Task 14: AI VPS with Ollama behind WireGuard

**Story:** STORY-011
**Feature:** ai-vps
**Assignee:** Trần Trung Hiếu (KAN-203)
**Description:** Manual first (Hiếu): buy the AI VPS sized for the KAN-140 models. `bootstrap.sh --role ai` (ufw:
22, UDP 51820 only from the application VPS). `infra/scripts/wireguard-setup.sh app|ai` (keys generated on the host in
`/etc/wireguard`, mode 600, never committed; `wg0` 10.66.0.1 / 10.66.0.2; `PersistentKeepalive`; `systemctl enable
wg-quick@wg0`). `infra/scripts/ollama-setup.sh` (pinned installer; systemd override `OLLAMA_HOST=10.66.0.2:11434`
written **before** the first start; `After=`/`Requires=wg-quick@wg0.service`; ufw allows 11434 only on `wg0` from
10.66.0.1; pulls `bge-m3` and `OLLAMA_CHAT_MODEL`). Staging `.env`: `OLLAMA_BASE_URL=http://10.66.0.2:11434`. Covers
AC-011.1 – AC-011.5. Checks: `infra/tests/ai-vps.test.sh` from the application VPS (embedding of 1024 dimensions and a
completion), again after a reboot of the AI VPS (Edge #35); `ports.test.sh` from a hosted runner finds 11434 closed
(Edge #36); `server.test.sh` on the AI VPS (AC-011.5). The chat-model pull and the completion check wait for KAN-140;
the `bge-m3` parts do not.
**Files:**
- Modify: `infra/scripts/bootstrap.sh`
- Create: `infra/scripts/wireguard-setup.sh`, `infra/scripts/ollama-setup.sh`
- Test: `infra/tests/ai-vps.test.sh`
- Modify: `infra/tests/ports.test.sh`, `infra/tests/server.test.sh`, `infra/README.md`
**Dependencies:** iteration 1 Task 11; KAN-140 model choice
**Estimated size:** medium

---

## Task 16: Demo seed — module data through contributors

**Story:** STORY-012
**Feature:** demo-seed-contributors
**Assignee:** Trần Trung Hiếu (KAN-97, S3)
**Description:** `SeedRunner` calls every `DemoSeedContributor` in `order()`, each inside `TenantContext.runIn` of the
new Workspace, with the `modules.<module>` part of the seed file (module = package segment after `vn.tsolve.`).
Stops with a non-zero exit naming the Workspace and the contributor on a failure. Extend
`infra/seed/demo-workspaces.json` with the TS-005 sections for each module that has a contributor; the contributors
themselves are each module's work (identity, workspace, project-integration, ticket-repository, wiki).
Add `--reset` to `infra/seed/run-seed.sh` (design, "Seed after S3"): environment check first (`dev` or `staging`
only, else exit non-zero with nothing touched), then stop backend and worker, drop and recreate the database and
re-run `infra/postgres/init` for it, empty the file storage, `migrate`, start, seed. Last step, once, by hand: run
`infra/seed/run-seed.sh --reset` on the staging server, where `TSOLVE_ENVIRONMENT=staging` (no argument), before the S3 seed so staging's Workspaces are re-created with every contributor.
Covers AC-012.2, AC-012.5, AC-012.6 – AC-012.9. Tests: order and binding with two test contributors; a failing contributor
(Edge #39); `--reset` refused for `production`, `prod` and unset (Edge #43), checked by `infra/tests/seed-reset.test.sh`
against the local stack, which also shows a reset local stack re-seeded, with the contributors run on freshly
created Workspaces (no "already exists, skipped" output, AC-012.5).
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/platform/seed/SeedRunner.java`, `SeedData.java`
- Modify: `infra/seed/demo-workspaces.json`, `infra/seed/run-seed.sh`
- Test: `backend/src/test/java/vn/tsolve/platform/SeedRunnerTest.java`
- Test: `infra/tests/seed-reset.test.sh`
**Dependencies:** iteration 1 Task 15
**Estimated size:** medium
