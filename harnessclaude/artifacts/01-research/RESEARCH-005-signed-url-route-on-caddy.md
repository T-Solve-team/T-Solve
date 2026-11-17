---
id: RESEARCH-005
title: Signed-URL route on a Workspace site (KAN-265)
question: What must the Caddy route of platform Task 17 do so that a Garage signed URL works through it, and can it be built now?
status: complete
date: 2026-11-16
related: [platform design R11, R12, platform tasks.md Task 17, kan-96-decisions D2, D16]
---

# Signed-URL route on a Workspace site (KAN-265)

## Research Question
KAN-265 is platform Task 17: Caddy routes `GET`/`HEAD` of `/<bucket>/*` on a Workspace site to Garage, so a signed URL
from `FileStorage.signedUrl` opens in a browser. Two things to find out:
1. What the route must do (and must not do) so that the signature stays valid, the file is downloaded and not rendered,
   and the signature never reaches the access log (Edge #39, #40, #41).
2. Whether the task's prerequisites are on `main`, so the task can be built and verified now.

Scope chosen without asking the user (decision D23 in `04-plans/platform/iteration-1/kan-96-decisions.md`): the Jira
sub-task names no question, so this spike covers what the task needs before its code is written.

## Context
Task 16 (`file-storage`, KAN-264) is `evaluating`. It signs URLs for the Workspace's own site, path-style:
`http://acme.localhost/<bucket>/<workspaceId>/<uuid>?X-Amz-…` (local) or `https://acme.<domain>/…` (staging), region
`garage`, `S3Presigner` with `pathStyleAccessEnabled(true)`
(`backend/src/main/java/vn/tsolve/platform/service/impl/ObjectFileStorage.java:138`). Decision D2 of KAN-96 deferred
Task 17 until the Caddyfiles of Tasks 11 and 14 exist.

## Findings

### 1. Prerequisites on `main` (16/11)

| Needed by Task 17 | State on `main` | Owner task (feature, status) |
|---|---|---|
| `infra/caddy/Caddyfile.local` (local Caddy on `*.localhost`) | missing | Task 11 `local-compose`, pending |
| Garage in `docker-compose.yml` + `garage-init.sh` | missing (no Garage, no Caddy in the local compose) | Task 1 `local-data-plane`, pending |
| Garage in `infra/compose.staging.yml` (AC-010.11) | missing | Task 13 `staging-servers`, pending |
| `infra/caddy/Caddyfile` (staging) | present: built for KAN-14, stock `caddy:2.11.7-alpine`, explicit site hosts from `TSOLVE_SITE_HOSTS`, no wildcard, no rate-limit module | Task 14 `edge-caddy`, pending (the design's custom image is not built) |
| `infra/tests/caddy-routing.test.sh` | missing (Task 17 only adds checks to it) | Task 14, pending |

So the route can be written into the staging `Caddyfile` today, but nowhere can it be run against a Garage: neither
stack has one. Task 17's "Done when" (a browser download on a local Workspace site, `caddy-routing.test.sh` green)
cannot be met before Tasks 1, 11 and 13 land. D2 still holds.

### 2. The signature survives Caddy with the defaults
- Caddy's `reverse_proxy` to an HTTP upstream passes the incoming `Host` unchanged and sends the same method and URI
  (path and query) unless a rewrite runs first (Caddy docs, `reverse_proxy`). Since 2.11 Caddy rewrites `Host` only for
  HTTPS upstreams; `garage:3900` is HTTP, so `Host` stays `acme.localhost` / `acme.<domain>`.
- Caddy adds `X-Forwarded-For`, `-Proto` and `-Host`. A presigned URL signs only `host` (`X-Amz-SignedHeaders=host`), so
  extra headers do not break it.
- Garage with no `root_domain` in `[s3_api]` reads every request as path-style, whatever the host.
  `ObjectFileStorageIT` already proves it: it sends the signed request straight to Garage with `Host: acme.localhost:<port>`
  and gets the object (`GarageTestBase` config: `s3_region = "garage"`, no `root_domain`).
- The scheme is not in the signature, so TLS ending at Caddy on staging is fine.

Conditions that would break it:
- `handle_path`, `uri strip_prefix` or any rewrite of the path (R11 already rejects a rewritten path).
- A port mismatch: the host in the signature is the authority of `FILE_STORAGE_PUBLIC_URL_PATTERN`. Locally Caddy must
  serve on port 80 and the pattern must be `http://{site}.localhost` with no port; any other port must appear in both.
- Automatic HTTPS on `*.localhost`: Caddy treats `*.localhost` as eligible for automatic HTTPS (internal CA) and
  redirects :80 to :443. A bare `*.localhost { … }` site makes the signed `GET` on `http://acme.localhost/…` a 308, and
  `caddy-routing.test.sh` (curl without `-L`) sees 308, not 200. The local site address must be `http://*.localhost`
  (scheme written out), or the global option `auto_https off`.
- A `root_domain` in Garage's `garage.toml` that is a suffix of the site hosts (Garage would then take `acme` as a bucket
  name). Task 1's `garage.toml` should leave `root_domain` out.

### 3. `HEAD` on a URL signed for `GET` is refused
SigV4 puts the HTTP method in the canonical request, and `signedUrl` presigns `GetObject` only. A `HEAD` with that URL
reaches Garage (Caddy allows it) and Garage refuses it (400 or 403, as for an expired URL, D16). Task 17's test line
"HEAD allowed" can only mean "Caddy does not answer 405"; it cannot expect a 200. This is the same behaviour Edge #39
asks for ("URL with a changed … method: refused"). Design R11 therefore states that `HEAD` is only passed through, not
served; AC-009.2 asks for no `HEAD` download. Presigning `HeadObject` as well waits until a criterion asks for it.

### 4. Downloaded, not rendered
- `store` writes every object as `application/octet-stream` (`ObjectFileStorage.java:99`, asserted in
  `ObjectFileStorageIT`), and Garage returns the stored type.
- With `X-Content-Type-Options: nosniff` the browser does not sniff an HTML or SVG body, so it downloads it (R12).
  The header is best set with `header_down` inside `reverse_proxy`, so it applies to the proxied response only.
- Caddy's `encode` (in the `common` snippet) compresses only its default text types by default, not
  `application/octet-stream`, so downloads stay byte-identical. The test should still compare the bytes.
- Optional hardening, not asked by R12: `Content-Disposition: attachment` or `Content-Security-Policy: sandbox` on the
  route. Neither is needed once the type is `application/octet-stream` with `nosniff`.

### 5. Methods other than `GET`/`HEAD`
A second matcher on the same path answers `405` with `Allow: GET, HEAD`, so `PUT`/`POST`/`DELETE` never reach Garage,
even signed ones. Garage has no published port, so this route is the only way from outside to Garage.

### 6. Signature out of the access log (Edge #40)
The log is declared once per site in the `(common)` snippet (`format json`). Caddy's `filter` log format has a `query`
filter on `request>uri` that deletes named query keys; `wrap` and `fields` are optional (Caddy docs, `log`). Setting
`wrap json` keeps the current JSON output. Deleting `X-Amz-Signature` (the part that grants access) and
`X-Amz-Credential` (the access key id) for every site is harmless, because other routes do not use these keys.
Caddy also has `log_skip`, but a filter keeps the request in the log and removes only the secret.

### 7. Where the route goes and what it needs
- Inside the Workspace-site block, before the final bare `handle` (the web app). It does not overlap `/api/*`.
- In the staging `Caddyfile` that block also serves `admin.<domain>`. The route there is harmless: the backend never
  signs for `admin.`, and Garage refuses unsigned reads (AC-007.15). Excluding it would need a `not host` matcher.
- The bucket name comes from `{$FILE_STORAGE_BUCKET}`, so the `caddy` service in both compose files needs that variable
  (required, `${FILE_STORAGE_BUCKET:?…}`). Otherwise the matcher becomes `//*`. The name must not collide with a web-app
  route (e.g. `tsolve-files`).

Draft for both Caddyfiles (not applied; Task 17 is not started):

```caddyfile
(files_route) {
	@files_read {
		path /{$FILE_STORAGE_BUCKET}/*
		method GET HEAD
	}
	handle @files_read {
		reverse_proxy garage:3900 {
			import proxy_headers
			header_down X-Content-Type-Options nosniff
		}
	}
	@files_other path /{$FILE_STORAGE_BUCKET}/*
	handle @files_other {
		header Allow "GET, HEAD"
		respond 405
	}
}

# in (common):
log {
	output stdout
	format filter {
		wrap json
		request>uri query {
			delete X-Amz-Signature
			delete X-Amz-Credential
		}
	}
}
```

### 8. How `caddy-routing.test.sh` can get a signed URL
The script has no backend endpoint that returns a signed URL (none is in the platform spec). The simplest source is the
`amazon/aws-cli` image on the compose network:
- `s3api put-object` (internal endpoint `http://garage:3900`, `--content-type application/octet-stream`) for an HTML file;
- `s3 presign` with `--endpoint-url http://acme.localhost` and `--region garage` for the URL. `aws s3 presign` has no
  path-style flag, so the test mounts an AWS config file (`AWS_CONFIG_FILE`) with `[default]`, `s3 =`,
  `  addressing_style = path`. Without it the URL may be virtual-hosted (`<bucket>.acme.localhost`) and the GET fails
  for a reason unrelated to the route.

Then `curl` through Caddy checks: `GET` 200 with `nosniff` and `application/octet-stream`; `HEAD` not 405 (Garage
refuses it, see 3); `PUT`/`POST`/`DELETE` 405; an altered signature and an unsigned request refused (400 or 403, D16);
neither `docker logs caddy` nor `docker logs garage` holds `X-Amz-Signature=` (Edge #40). The image is used by the test only.

## Recommendations
1. Keep KAN-265 deferred (D2) until Garage runs in the local stack (Task 1), the local Caddy exists (Task 11) and
   Garage is in the staging stack (Task 13). Writing the route into the staging Caddyfile alone would leave it untested.
2. When those land, apply the draft of Finding 7 to `Caddyfile.local` and `Caddyfile`, and add `FILE_STORAGE_BUCKET` to
   the `caddy` service of both compose files.
3. Correct Task 17's test expectations: `HEAD` with a `GET`-signed URL is refused by Garage (not a 200), and refusals
   are 400 or 403 (D16, as in Edge #39).
4. Give Tasks 1 and 11 three constraints: no `root_domain` in `garage.toml`; a local public URL pattern
   `http://{site}.localhost` with Caddy on port 80; the local site address `http://*.localhost` (or `auto_https off`).

## Open Questions
- Does Garage log the request query string (and so the signature) at its default log level? Task 17's test now checks
  `docker logs garage` for `X-Amz-Signature=`. If it fails, set Garage's log level or filter so Edge #40 also holds
  outside Caddy.
- Should the route exclude `admin.<domain>`? This spike says it is harmless. The owner of Task 14 decides when the
  site blocks are split.
- The design still describes a custom Caddy image with wildcard certificates (Task 14), but `main` runs the stock image
  with explicit hosts (KAN-14 deviation). The route works with either. Task 14's owner should update the design.

## Related Artifacts
- `artifacts/04-plans/platform/design.md`: Signed URL host and routing (R11), Key Decisions R11, R12, Edge #39 – #41
- `artifacts/04-plans/platform/iteration-1/tasks.md`: Tasks 1, 11, 13, 14, 16, 17
- `artifacts/04-plans/platform/iteration-1/kan-96-decisions.md`: D2, D16, D23
- `artifacts/03-modules/platform/spec.md`: AC-007.12, AC-007.15, AC-009.2
