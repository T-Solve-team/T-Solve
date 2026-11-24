/**
 * Public staging path load test — KAN-378, first cut of AC-014.12/13.
 * Safe starting point: VUS=10; step 10 → 50 → 100, stopping at the first errors.
 * k6 run -e VUS=10 -e HOLD=60s infra/loadtest/public-paths.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'https://bluefin.tsolve.io.vn';
const LOGIN_URL = __ENV.LOGIN_URL || 'https://login.tsolve.io.vn';
const API_URL = __ENV.API_URL || 'https://api.tsolve.io.vn';
const REALM = __ENV.REALM || 'tsolve';
const VUS = Number(__ENV.VUS || '10');
const HOLD = __ENV.HOLD || '60s';
const RAMP = __ENV.RAMP || '15s';

const unexpectedStatus = new Rate('unexpected_status');
http.setResponseCallback(http.expectedStatuses(200, 401));

export const options = {
  scenarios: {
    public_paths: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP, target: VUS },
        { duration: HOLD, target: VUS },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    'http_req_duration{endpoint:web_page}': ['p(95)<1000'],
    'http_req_duration{endpoint:sign_in_discovery}': ['p(95)<1000'],
    'http_req_duration{endpoint:session_unauthenticated}': ['p(95)<1000'],
    'http_req_duration{endpoint:jira_app_event_unsigned}': ['p(95)<1000'],
    'http_req_duration{endpoint:health}': ['p(95)<1000'],
    http_req_failed: [
      { threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '10s' },
    ],
    unexpected_status: [
      { threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '10s' },
    ],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function probe(endpoint, method, url, expectedStatus, body = null) {
  const tags = { name: endpoint, endpoint };
  const headers = { 'User-Agent': 'T-Solve-loadtest/1.0 (KAN-378)' };
  if (method === 'POST') {
    headers['Content-Type'] = 'application/json';
  }
  const response = http.request(method, url, body, {
    tags,
    headers,
    timeout: '10s',
    // Fresh jars and no redirects prevent server-set cookies reaching any probe.
    jar: new http.CookieJar(),
    redirects: 0,
  });
  const passed = check(response, {
    [`${endpoint}: status ${expectedStatus}`]: (r) => r.status === expectedStatus,
  }, tags);
  unexpectedStatus.add(!passed, tags);
}

export default function () {
  probe('web_page', 'GET', `${BASE_URL}/`, 200);
  probe(
    'sign_in_discovery',
    'GET',
    `${LOGIN_URL}/realms/${REALM}/.well-known/openid-configuration`,
    200,
  );
  probe('session_unauthenticated', 'GET', `${BASE_URL}/api/v1/me`, 401);
  probe(
    'jira_app_event_unsigned',
    'POST',
    `${API_URL}/api/v1/jira-app/events/issue-updated`,
    401,
    '{}',
  );
  probe('health', 'GET', `${API_URL}/actuator/health`, 200);
  sleep(1);
}

export function handleSummary(data) {
  const output = { stdout: textSummary(data, { indent: ' ', enableColors: false }) };
  if (__ENV.SUMMARY_JSON) {
    output[__ENV.SUMMARY_JSON] = JSON.stringify(data, null, 2);
  }
  return output;
}
