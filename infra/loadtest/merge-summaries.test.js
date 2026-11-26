const test = require('node:test');
const assert = require('node:assert/strict');
const { mkdtempSync, writeFileSync, readFileSync, rmSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join } = require('node:path');
const { spawnSync } = require('node:child_process');
const { mergeSummaries, renderMarkdown } = require('./merge-summaries');

const endpoints = [
  'web_page',
  'sign_in_discovery',
  'session_unauthenticated',
  'jira_app_event_unsigned',
  'health',
];

// Same shape as public-paths.js handleSummary, including trends without counts.
function fixture(count, p95 = 200, failed = 0) {
  const trend = {
    avg: 100,
    min: 10,
    med: 90,
    'p(90)': 150,
    'p(95)': p95,
    'p(99)': p95 + 50,
    max: p95 + 100,
  };
  const metrics = {
    http_reqs: { values: { count: count * 5 } },
    http_req_failed: { values: { rate: failed / (count * 5) } },
    unexpected_status: { values: { rate: failed / (count * 5) } },
    checks: {
      values: { rate: 1 - failed / (count * 5), passes: count * 5 - failed, fails: failed },
    },
    iterations: { values: { count } },
    vus_max: { values: { value: 90, min: 90, max: 90 } },
    http_req_duration: { values: { ...trend } },
  };
  for (const endpoint of endpoints) {
    metrics[`http_req_duration{endpoint:${endpoint}}`] = { values: { ...trend } };
  }
  return {
    metrics,
    root_group: {
      groups: [],
      checks: endpoints.map((endpoint) => ({
        name: `${endpoint}: status ${endpoint === 'session_unauthenticated' || endpoint === 'jira_app_event_unsigned' ? 401 : 200}`,
        passes: count - (endpoint === 'health' ? failed : 0),
        fails: endpoint === 'health' ? failed : 0,
      })),
    },
  };
}

test('AC-014.13: merges real summary shapes with request weighting and honest probe coverage', () => {
  const report = mergeSummaries([fixture(100, 200, 1), fixture(300, 600)], {
    expectedRunners: 2,
    metadata: [
      { index: 1, publicIp: '192.0.2.1' },
      { index: 2, publicIp: '192.0.2.2' },
    ],
  });
  assert.equal(report.pass, true);
  assert.equal(report.totals.runners, 2);
  assert.equal(report.totals.totalVus, 180);
  assert.equal(report.totals.requests, 2000);
  assert.equal(report.totals.failedRequests, 1);
  assert.equal(report.totals.failedRequestRate, 1 / 2000);
  assert.equal(report.totals.unexpectedStatusRate, 1 / 2000);
  assert.equal(report.totals.checksPassRate, 1999 / 2000);
  assert.equal(report.totals.iterations, 400);
  assert.equal(report.endpoints.health.requests, 400);
  assert.equal(report.endpoints.health.p95Worst, 600);
  assert.equal(report.endpoints.health.p95WeightedMean, 500);
  assert.equal(report.endpoints.health.unexpectedStatusRate, 1 / 400);
  assert.equal(report.distinctPublicIps, 2);
  const markdown = renderMarkdown(report);
  assert.match(markdown, /rejection probes \(401 expected\)/);
  assert.match(markdown, /percentiles cannot be merged exactly/i);
  assert.match(markdown, /no authenticated session was available/i);
  assert.match(markdown, /no per-status breakdown/i);

  const partial = fixture(100);
  partial.root_group.checks.at(-1).passes = 99; // Interrupted iteration before health's check.
  partial.metrics.http_reqs.values.count = 499;
  partial.metrics.checks.values.passes = 499;
  partial.metrics.iterations.values.count = 99;
  const partialReport = mergeSummaries([partial]);
  assert.equal(partialReport.pass, true);
  assert.equal(partialReport.endpoints.web_page.requests, 100);
  assert.equal(partialReport.endpoints.health.requests, 99);
});

test('AC-014.13: fails strict limits, missing evidence, and runner threshold failures', () => {
  const slow = fixture(100, 1000);
  assert.equal(mergeSummaries([fixture(300), slow]).pass, false);
  assert.equal(mergeSummaries([fixture(100, 200, 5)]).pass, false); // Exactly 1% is a failure.
  assert.equal(mergeSummaries([fixture(100)], { expectedRunners: 2 }).pass, false);
  assert.equal(mergeSummaries([]).pass, false);
  const missing = fixture(100);
  delete missing.metrics['http_req_duration{endpoint:health}'];
  assert.equal(mergeSummaries([missing]).pass, false);
  const thresholdFailure = fixture(100);
  thresholdFailure.metrics.checks.thresholds = { 'rate>0.99': { ok: false } };
  assert.equal(mergeSummaries([thresholdFailure]).pass, false);
  const slowHealth = fixture(100);
  slowHealth.metrics['http_req_duration{endpoint:health}'].values['p(95)'] = 1000;
  slowHealth.metrics['http_req_duration{endpoint:health}'].values['p(99)'] = 1050;
  slowHealth.metrics['http_req_duration{endpoint:health}'].values.max = 1100;
  assert.equal(mergeSummaries([slowHealth]).pass, false);
  assert.equal(
    mergeSummaries([fixture(100)], { metadata: [{ publicIp: null, k6ExitCode: 1 }] }).pass,
    false,
  );
  const wrongStatus = fixture(100);
  wrongStatus.root_group.checks[0].passes -= 5; // Public endpoint answered 401: HTTP accepts it, exact check rejects it.
  wrongStatus.root_group.checks[0].fails += 5;
  wrongStatus.metrics.unexpected_status.values.rate = 0.01;
  wrongStatus.metrics.checks.values = { rate: 0.99, passes: 495, fails: 5 };
  const rejected = mergeSummaries([wrongStatus]);
  assert.equal(rejected.totals.failedRequestRate, 0);
  assert.equal(rejected.endpoints.web_page.unexpectedStatusRate, 0.05);
  assert.equal(rejected.pass, false);
});

test('AC-014.13: CLI writes both reports and exits non-zero for slow, absent, or missing runners', (t) => {
  const dir = mkdtempSync(join(tmpdir(), 'kan466-merge-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const output = join(dir, 'merged-summary.json');
  const run = (expected = '1') =>
    spawnSync(process.execPath, [join(__dirname, 'merge-summaries.js'), dir, output], {
      encoding: 'utf8',
      env: { ...process.env, EXPECTED_RUNNERS: expected },
    });
  assert.equal(run().status, 1);
  writeFileSync(join(dir, 'summary-1.json'), JSON.stringify(fixture(100)));
  writeFileSync(
    join(dir, 'meta-1.json'),
    JSON.stringify({ index: 1, publicIp: '192.0.2.1', k6ExitCode: 0 }),
  );
  assert.equal(run().status, 0);
  assert.equal(JSON.parse(readFileSync(output)).pass, true);
  assert.match(readFileSync(join(dir, 'merged-summary.md'), 'utf8'), /PASS/);
  assert.equal(run('2').status, 1);
  assert.equal(run('invalid').status, 1);
  writeFileSync(join(dir, 'summary-2.json'), '{');
  assert.equal(run('2').status, 1);
  rmSync(join(dir, 'summary-2.json'));
  writeFileSync(join(dir, 'summary-1.json'), JSON.stringify(fixture(100, 1100)));
  assert.equal(run().status, 1);
});
