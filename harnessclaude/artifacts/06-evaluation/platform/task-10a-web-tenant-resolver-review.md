---
module: platform
feature: web-tenant-resolver
task: 10a
verdict: pass
attempt: 1
date: 2026-11-26
reviewer: tsolve-code-reviewer
iteration: 1
currentIteration: 1
---

# Review: platform — Task 10a: web Tenant Resolver (PR #354, KAN-258)

## Summary
Reviewed `git diff origin/main...HEAD` at `c381a7f8` (16 files, +1310/−5) against platform `spec.md` (STORY-002,
AC-005.7, AC-005.8, AC-013.7, AC-013.11), iteration-1 `tasks.md` Task 10a and master `design.md` ("The binding",
"The web resolver", Key Decisions T10, edge rows #1, #2, #17 – #20, #22, #86, #90, #91).

The filter follows the planned order of checks exactly (AC-002.19 paths → `Host` classified → one `findBySiteName` →
`MSG151` / `MSG150` → Jira App / `api.` path rule → bind for the request, unbind in `finally`). Every criterion Task
10a covers is implemented and named by a test that checks it; the 95 targeted tests pass and all 14 CI checks on the
PR head are green. The two card decisions hold: `acme.` + `/api/v1/jira-appx` is bound (test `PASSES_IN_A`), and an
unknown or Suspended site on a Jira App path answers `MSG151` / `MSG150` first.

No Critical or High finding. One Medium: the design's claim that a differing `X-Forwarded-Host` "fails closed" does
not hold when `Host` is `login.` or `admin.`, which lets a direct caller of the backend port use a Suspended Workspace
(finding 1). It is not reachable through Caddy, and the spec (AC-002.3, AC-002.7: "the host" is the `Host` header) is
met, so it does not fail the task; it needs a decision.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1 table below: every covered AC is PASS |
| Completeness | pass | No MISSING. No EXTRA behaviour beyond the BUG-404 hardening of `SecurityProblemWriter` (logged, tested) |
| Correctness | pass | 95/95 targeted tests; CI green on `c381a7f8`; no CRITICAL code finding |
| Integration | pass | `bindRequest` / `runIn` contract as designed; contract text + generated model in sync (CI `frontend-checks`); full-context ITs without a catalog green on CI. Finding 1 is the open integration edge with identity's `WebSessionFilter` |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-002.2 bind the site's Workspace for the whole request; base domain from the template | PASS | `TenantResolverFilter.java:94-128`, `HostClassifier.fromTemplate`; `bindsFromTheHostHeaderOnly…`, `readsTheBaseDomainFromTheTemplate_AC_002_2`, `RequestBindingSourceScanTest` |
| 2 | AC-002.3 `Host` only, no case, no port; forwarded headers and ids ignored | PASS | `TenantResolverFilter.java:89`, `HostClassifier.classify`; spoofed request with XFH, `Forwarded`, header/param/cookie/body ids stays in A |
| 3 | AC-002.4 bad label → 404 MSG151, no lookup | PASS | `HostClassifier` `LABEL`; `-acme`, `a_b`, 64 chars, no lookup counted |
| 4 | AC-002.5 unknown label → 404 MSG151 | PASS | `TenantResolverFilter.java:110-113`; `nosuch` on 3 paths, one lookup each |
| 5 | AC-002.6 bare, `www.`, `staging.`, deep, foreign → MSG151, no lookup (except AC-002.19) | PASS | `HostClassifier.java:56-64`; `classifiesNonSiteHosts…`; `BARE` + Jira path → MSG151 |
| 6 | AC-002.7 Suspended → 403 MSG150, no handler, whatever session | PASS | `TenantResolverFilter.java:114-117`; with `SESSION` cookie and bearer, chain never reached |
| 7 | AC-002.8 before every Spring Security check | PASS | order `DEFAULT_FILTER_ORDER - 10`; `TenantFilterOrderTest` POST without CSRF: MSG151 / MSG150, Active → MSG07 |
| 8 | AC-002.9 problem+json, `status` + `code`, nothing naming a Workspace | PASS | `assertRefused` checks content type, status, code, SRS text, absence of labels and ids |
| 9 | AC-002.10 `login.` / `api.` / `admin.` bind nothing | PASS | `TenantResolverFilter.java:124-127`; three hosts pass unbound, no lookup |
| 10 | AC-002.11 unbind at the end, also on exception | PASS | `runBound` `finally`; unchecked and checked throws, ASYNC re-dispatch, ERROR dispatch |
| 11 | AC-002.13 (the resolver part: differing XFH fails closed) | PASS for a site `Host` | `runIn(B)` throws inside A. Not true for a `login.` / `admin.` `Host`: finding 1 |
| 12 | AC-002.17 `<site>.localhost` any port | PASS | local template rows in `HostClassifierTest` |
| 13 | AC-002.19 health / API docs pass on any host, unbound, no lookup | PASS | `ANY_HOST`; 6 paths × 6 hosts incl. null; `/actuator/healthx` and `/actuator/health/%2e%2e/env` refused |
| 14 | AC-005.7 (resolver part) `api.` any other path → 404 MSG06 | PASS | `TenantResolverFilter.java:120`; `/api/v1/me`, `/`, callback, `/api/v1/jira-appx` on `api.` |
| 15 | AC-005.8 (host part) Jira App paths on site / `login.` / `admin.` → 404 MSG06 | PASS | same line; plain, bare `/api/v1/jira-app`, and three percent-encoded forms |
| 16 | AC-013.11 a new Workspace resolves on the next request | PASS | `gamma` refused, row added, next request bound (no cache) |
| 17 | AC-013.7 (read-first; not in Covers) suspend applies on the next request | consistent | no cache; Active → Suspended toggle answers MSG150 on the next request |
| 18 | Edge #17 / #22 Registry down → 500 MSG06; logs host and code only | PASS | JDBC URL and markers absent from body and captured log |
| 19 | Edge #86 / #91 template check after start-up, only with a catalog; one log line without one | PASS | `TenantFilterConfigTest` |

## Traceability
```
  ✓ AC-002.2  HostClassifierTest, RequestBindingSourceScanTest, TenantResolverFilterTest, TenantFilterConfigTest
  ✓ AC-002.3  HostClassifierTest, TenantResolverFilterTest
  ✓ AC-002.4  HostClassifierTest, TenantResolverFilterTest
  ✓ AC-002.5  TenantResolverFilterTest
  ✓ AC-002.6  HostClassifierTest
  ✓ AC-002.7  TenantResolverFilterTest
  ✓ AC-002.8  TenantFilterOrderTest
  ✓ AC-002.9  TenantResolverFilterTest
  ✓ AC-002.10 HostClassifierTest, TenantResolverFilterTest
  ✓ AC-002.11 TenantResolverFilterTest, ThreadLocalTenantContextTest, WebSessionFilterTenantContextTest
  ✓ AC-002.13 NoWorkspaceFallbacksTest, TenantResolverFilterTest, ThreadLocalTenantContextTest
  ✓ AC-002.17 HostClassifierTest
  ✓ AC-002.19 TenantResolverFilterTest
  ✓ AC-005.7  TenantResolverFilterTest
  ✓ AC-005.8  TenantResolverFilterTest
  ✓ AC-013.11 TenantResolverFilterTest
trace platform (STORY-002, STORY-005): 19/33 acceptance criteria have a test
```
✗ rows outside Task 10a's Covers (AC-002.1, .18, .20 – .25, AC-005.1 – .6, AC-013.7) belong to Tasks 6, 10b, 20 and
STORY-013. Full output: test report.

## Issues

No CRITICAL finding.

1. **[MEDIUM][STRIDE: elevation of privilege]** `backend/src/main/java/vn/tsolve/platform/web/TenantResolverFilter.java:124`
   together with `identity/signin/port/PlatformWorkspaceLookup.java:42-55` and `identity/session/WebSessionFilter.java:175`.
   A request with `Host: login.<base>` or `Host: admin.<base>` (a non-Jira path), `X-Forwarded-Host:
   <suspended-site>.<base>` and a valid session cookie of that site passes the resolver unbound (no lookup, so no
   `MSG150`). `server.forward-headers-strategy: framework` makes `getServerName()` follow `X-Forwarded-Host`, so
   `WebSessionFilter` looks up the Suspended site; `findBySiteHost` does not filter on status, the session matches,
   and `runIn` binds the Suspended Workspace: the handler runs (GBR-38 bypassed). On `api.` every non-Jira path is
   refused first, so `api.` is not affected. `design.md:83-84` says a differing XFH "fails closed: `runIn` of the
   other Workspace throws or the session check answers `MSG123`" — that holds only when `Host` is a site (something is
   already bound). Reach: only a caller of `backend:8080` directly — Caddy overwrites client `X-Forwarded-*`
   (`infra/caddy/Caddyfile:16-17`) and `compose.staging.yml` publishes no backend port. Spec-wise AC-002.3 / AC-002.7
   are met.
   Fix (decision for Hiếu; Q5 rejected refusing in the resolver on the premise above): either (a) in the resolver,
   when `X-Forwarded-Host` is present and its first value, without case and port, differs from `Host`, answer 404
   `MSG151` (the forward-headers strategy stays on, so https redirect URIs keep working), with a test `Host: login.` +
   XFH `acme.` (Suspended) → refused; or (b) keep the code, correct `design.md:83-84` to name the network boundary as
   the control, and add an edge row.
2. **[LOW][path matching]** `TenantResolverFilter.java:39` uses `AntPathRequestMatcher` on the servlet path; the Jira
   chain's `http.securityMatcher(PATHS)` (`common/security/JiraAppSecurityConfig.java:54`) builds an `MvcRequestMatcher`
   in Spring Security 6.3 (Spring MVC is on the class path), which reads the request URI. Judged acceptable: the two
   agree on every path the `StrictHttpFirewall` lets through (encoded `/`, `.`, `%`, `;`, `//` and `..` are rejected
   inside `FilterChainProxy` before any chain matcher), and case and `/**` semantics are the same. `design.md:87-88`
   says "the same matcher"; it is the same pattern, not the same class.
   Fix: in Task 20 or 10b, one `RANDOM_PORT` test on a real Tomcat for `/api/v1/jira%2Dapp/x` and `/api/v1/jira-app;x/y`
   on a site host (expect 404 `MSG06` or 400, never the Jira chain), and reword the design line.
3. **[LOW][test gap]** No test runs the resolver behind Spring's `ForwardedHeaderFilter` (the `framework` strategy
   wrapper), so "the resolver reads the raw `Host` while `getServerName()` follows XFH" is shown only by code reading;
   `TenantFilterOrderTest` is a `@WebMvcTest` slice without it.
   Fix: in 10b's `TenantIsolationIT` (full context, real filters), send `Host: a.` + `X-Forwarded-Host: b.` and assert
   A's row only.
4. **[LOW][maintainability]** `TenantResolverFilter.java:41-49` copies the AC-002.19 list from
   `common/security/SecurityConfig.java:40-45`; a path added to one is not added to the other.
   Fix: one `public static final String[]` in `SecurityConfig` (as `JiraAppSecurityConfig.PATHS`) used by both.
5. **[INFO][security, accepted by design]** Each request on a well-formed unknown label costs one Registry query
   (no cache, no rate limit, Q9), and refusals log the caller's raw `Host` at INFO (`TenantResolverFilter.java:144`;
   Tomcat rejects CR/LF in header values, so no log forging). Both listed by the developer; no change asked now.

### Open risks of the card, judged
- Path matcher not compared with the Jira chain's: acceptable, finding 2.
- Not run on a real servlet container: decoding is mimicked correctly in the tests (raw URI plus decoded servlet path);
  the remaining gap is findings 2 and 3, for 10b / Task 20.
- `X-Forwarded-Host` differing from `Host` must fail closed: holds for a site `Host`; does not for a `login.` or
  `admin.` `Host` (finding 1; on `api.` every non-Jira path is refused first).

## Security Review — platform Task 10a — 2026-11-26

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 1 (finding 4) |
| STRIDE | 0 | 0 | 1 (finding 1) | 1 + 1 info (findings 2, 5) |

- Secrets: grep of the added lines finds only the fake `password=s3cr3t` inside a test that asserts it is not logged;
  gitleaks green on CI. No env file added.
- Dependencies: none added (no `pom.xml` / `package.json` change); no audit run.
- Spoofing: raw `Host` only; forwarded headers and ids ignored for the binding (finding 1 is the forwarded-host gap
  outside the resolver).
- Tampering: decoded-path matching, encoded variants tested; `//` no longer a 500 (BUG-404).
- Repudiation / information disclosure: refusals log host and code; lookup failure logs the exception class only;
  bodies hold code, SRS text and the request path.
- Denial of service: finding 5.
- Elevation of privilege: Jira App paths closed off `api.`; only the resolver calls `bindRequest` (source scan);
  `runIn` skips the existence check only for the already bound id.

## Test Report
`task-10a-web-tenant-resolver-test-report.md`: targeted run 95 passed, 0 failed (HostClassifierTest 50,
TenantResolverFilterTest 32, ThreadLocalTenantContextTest 10, TenantFilterOrderTest 1, TenantFilterConfigTest 1,
RequestBindingSourceScanTest 1); `gh pr checks 354` on `c381a7f8`: 14/14 pass. Full verify not re-run (developer's
result and CI cover it). Coverage not measured.

## Assumptions made
- "The host" in AC-002.7 is the `Host` header (AC-002.3), so finding 1 is a design gap, not a spec failure.
- Caddy 2.11's default (client `X-Forwarded-*` overwritten without `trusted_proxies`) is taken from the Caddyfile
  comment and config; not checked on the VPS.
