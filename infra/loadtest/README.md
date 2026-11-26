# Public staging load test (KAN-378)

`public-paths.js` is a first cut of platform AC-014.12, run with k6 v2.4.0.
Each iteration sends five sequential requests, then waits one second:

| Endpoint tag              | Request                                                        | Expected | Purpose                                                                |
| ------------------------- | -------------------------------------------------------------- | -------- | ---------------------------------------------------------------------- |
| `web_page`                | GET `BASE_URL/`                                                | 200      | SPA index through the public web edge; no browser assets or rendering. |
| `sign_in_discovery`       | GET `LOGIN_URL/realms/REALM/.well-known/openid-configuration`  | 200      | Public sign-in provider discovery.                                     |
| `session_unauthenticated` | GET `BASE_URL/api/v1/me`                                       | 401      | Unauthenticated session rejection through Caddy and Spring Security.   |
| `jira_app_event_unsigned` | POST `API_URL/api/v1/jira-app/events/issue-updated`, body `{}` | 401      | Jira App path rejection before any event work.                         |
| `health`                  | GET `API_URL/actuator/health`                                  | 200      | Public API health response.                                            |

Requests carry `User-Agent: T-Solve-loadtest/1.0 (KAN-378)`, have a 10-second
timeout, send no cookies or tokens, and do not follow redirects. Each request uses
a fresh cookie jar, following the [k6 request parameters API](https://grafana.com/docs/k6/latest/javascript-api/k6-http/params/).
No secrets are needed.

AC-014.12 supplies the p95 response time below 1 second and error rate below 1%.
The script enforces p95 globally and for each endpoint, `http_req_failed < 1%`,
`unexpected_status < 1%`, and checks above 99%. Expected 401s count as successful
HTTP responses; exact-status checks catch a 200 where a 401 was required and vice
versa. Both error-rate thresholds abort on failure after a 10-second evaluation
delay. A missed threshold makes k6 exit non-zero (AC-014.13).

## Limits

This does not yet cover the authenticated Ticket List or the full sign-in round
trip: sign-in is Google-only and there is no test token. The unsigned Jira App
probe measures rejection, not authenticated event processing.

The 500-concurrent-user target of AC-014.12 is not covered. Staging is one small
VPS; never run high loads against it without the DevOps owner. A passing public
path run does not establish full compliance with AC-014.12. A measured staging
run and its recorded result for AC-014.13 are still required.

## Run

With k6 installed, run from the repository root. Start at 10 VUs, review the
results, then step to 50 and 100 only if the preceding run is healthy. Stop at the
first errors; do not increase the load after a failure.

```sh
k6 run -e VUS=10 -e HOLD=60s infra/loadtest/public-paths.js
k6 run -e VUS=50 -e HOLD=60s infra/loadtest/public-paths.js
k6 run -e VUS=100 -e HOLD=60s infra/loadtest/public-paths.js
```

Defaults: `BASE_URL=https://bluefin.tsolve.io.vn`,
`LOGIN_URL=https://login.tsolve.io.vn`, `API_URL=https://api.tsolve.io.vn`,
`REALM=tsolve`, `VUS=10`, `HOLD=60s`, `RAMP=15s`. Override with `-e NAME=value`.
The scenario ramps from zero to VUS over RAMP, holds for HOLD, then ramps down
over 10 seconds with a 10-second graceful ramp-down allowance.

Optional: add `-e SUMMARY_JSON=<path outside the repo>` to save the summary JSON
(use an existing parent directory). The text summary always prints to stdout,
including p95 per path.

## Multi-IP run on GitHub Actions

Dispatch only from `main` (the default branch), after the workflow is available there:

```sh
gh workflow run loadtest.yml --ref main -f total_vus=500 -f duration=60s
```

Staging limits about 100 concurrent requests per source IP. This workflow uses
`ceil(total_vus / 90)` GitHub-hosted runners, each capped at 90 VUs. Equal runner
allocations can round the total up: 500 requested VUs becomes 6 × 84 = 504 VUs.
Inputs accept 1–2000 total VUs; `duration` is the hold time, plus the script's ramps.
Runner jobs time out after 30 minutes, so leave room for setup and ramps.
Override `base_url`, `login_url`, and `api_url` to repoint the whole stack; all
must use HTTPS. Runs are serialized to prevent overlapping load tests.

GitHub IP ranges are public, and a small number of runners may share a NAT.
The merged report shows IPs seen (or unknown when IP lookup fails), so distinct
runner jobs alone do not prove distinct source IPs. The `k6-merged` artifact
contains JSON and Markdown, also published in the run summary. A missed target,
runner threshold/execution failure, or missing runner summary fails the merge.
Per-endpoint counts come from exact-status checks; percentiles cannot be merged
exactly, so p95 includes both the worst runner and a request-weighted mean.

The session and unsigned Jira App endpoints are **rejection probes (401 expected)**.
No authenticated session was available because sign-in is Google-only; these
probes prove rejection, not fast authenticated ticket-list or Jira-App work.
The 500-user authenticated workload remains outside this test's coverage.

## Results

| Date             | Commit on staging                     | VUs | Requests          | Error rate | p95 all | p95 per path                                                                                                                     | Result                                               |
| ---------------- | ------------------------------------- | --- | ----------------- | ---------- | ------- | -------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| 22/11/2026 06:39 | not exposed (web build of 18/11/2026) | 10  | 920               | 0.00 %     | 3.02 s  | web_page 21.9 ms · sign_in_discovery 17.8 ms · session_unauthenticated 21.4 ms · jira_app_event_unsigned 14.7 ms · health 3.10 s | Threshold missed: health (BUG-368); other paths pass |
| 22/11/2026 06:41 | same                                  | 50  | 4,610             | 0.00 %     | 3.03 s  | web_page 11.4 ms · sign_in_discovery 13.5 ms · session_unauthenticated 13.1 ms · jira_app_event_unsigned 12.1 ms · health 3.13 s | Threshold missed: health (BUG-368); other paths pass |
| 22/11/2026 06:42 | same                                  | 100 | 9,200 (105 req/s) | 0.00 %     | 3.03 s  | web_page 11.5 ms · sign_in_discovery 13.5 ms · session_unauthenticated 14.0 ms · jira_app_event_unsigned 12.8 ms · health 3.15 s | Threshold missed: health (BUG-368); other paths pass |

Rows are added after each run (AC-014.13). Run from one client in Vietnam, 60 s hold per level; the steps stopped at
100 users (staging is one small VPS). "p95 all" includes the slow health path; without it every path stays under 25 ms.
