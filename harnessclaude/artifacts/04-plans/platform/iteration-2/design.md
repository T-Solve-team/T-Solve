---
module: platform
date: 2026-11-04
iteration: 2
---

# Design: platform — iteration 2 (31/10–13/11)

Master: `artifacts/04-plans/platform/design.md` (full Data Model, Key Decisions, File Structure, Edge Cases, Risk
Review, Builder Check). This file keeps only what Tasks 6, 7, 14 and 16 need.

## Scope

The Forge Invocation Token check (STORY-005), the Jira App resolver and Jira site registration (STORY-006), the AI VPS
(STORY-011) and the seed's module data through contributors (STORY-012, AC-012.2, AC-012.6, AC-012.7).

## Decisions used in this iteration

| Decision | Choice |
|----------|--------|
| P3 | `spring-security-oauth2-jose` (Nimbus), Spring Boot BOM version — approved by the user on 25/10 |
| Security chain | `ForgeSecurityConfig` `@Order(1)`, matches the `api.` host; stateless; no CSRF; no cookie; be-foundation's and identity's chains unchanged |
| Forge claims | `cloudId` and principal read from the token's claims; the builder confirms the claim paths against Atlassian's Forge Remote documentation before Task 6 and records them in the master |
| Lifecycle events | Installed and uninstalled events change nothing and answer 204; an installed event from an unlinked `cloudId` is logged (AC-006.15, AC-006.16). The `cloudId` is linked only by `WorkspaceCatalog.linkJiraSite` (AC-004.19), whose `TenantInfoClient` call uses public addresses only, `https`, no redirects, 5 s, 64 KB |
| P11, Q13 | WireGuard `wg0` (10.66.0.1 / 10.66.0.2), keys on the hosts, `wg-quick@wg0` enabled; Ollama native systemd on `10.66.0.2:11434`, override before first start; ufw 11434 only from the peer on `wg0` |
| Seed contributors | Called in `order()`, each in `runIn` of the new Workspace, with `modules.<module>` of the seed file |

## Edge cases covered here

Master rows 28 – 34 (STORY-005, STORY-006), 52 – 53 (STORY-011) and 56 (seed contributors). The slicing of 25/10 is
to be redone with the tasks; the 4/11 sync is in the master.
