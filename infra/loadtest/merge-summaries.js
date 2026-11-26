// KAN-466: merge public-paths.js handleSummary data without inventing pooled percentiles.
const fs = require('node:fs');
const path = require('node:path');
const { isIP } = require('node:net');

const ENDPOINTS = {
  web_page: 200,
  sign_in_discovery: 200,
  session_unauthenticated: 401,
  jira_app_event_unsigned: 401,
  health: 200,
};
const STATS = ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'];
const finite = (value) => typeof value === 'number' && Number.isFinite(value) && value >= 0;
const sum = (rows, pick) => rows.reduce((total, row) => total + pick(row), 0);

function groupChecks(group = {}) {
  return [
    ...Object.values(group.checks || {}),
    ...Object.values(group.groups || {}).flatMap(groupChecks),
  ];
}

function latency(rows) {
  const requests = sum(rows, (row) => row.requests);
  const weighted = (key) =>
    requests ? sum(rows, (row) => row.values[key] * row.requests) / requests : null;
  const worst = (key) => (rows.length ? Math.max(...rows.map((row) => row.values[key])) : null);
  return {
    requests,
    avg: weighted('avg'),
    med: weighted('med'),
    p90: weighted('p(90)'),
    p95Worst: worst('p(95)'),
    p95WeightedMean: weighted('p(95)'),
    p99Worst: worst('p(99)'),
    max: worst('max'),
  };
}

// Only unambiguous status-only counters are used; overlapping tag sets must not be double counted.
function statusCounts(metrics) {
  const counts = {};
  for (const [name, metric] of Object.entries(metrics)) {
    const match = /^http_reqs\{status:(\d{3})\}$/.exec(name);
    if (match && finite(metric.values?.count)) counts[match[1]] = metric.values.count;
  }
  return Object.keys(counts).length ? counts : null;
}

function mergeSummaries(summaries, options = {}) {
  const errors = [...(options.errors || [])];
  const expectedRunners = options.expectedRunners ?? summaries.length;
  const totals = {
    runners: summaries.length,
    totalVus: 0,
    requests: 0,
    failedRequests: 0,
    iterations: 0,
  };
  const rows = Object.fromEntries(Object.keys(ENDPOINTS).map((name) => [name, []]));
  const overall = [];
  const failedThresholds = [];
  const statuses = [];
  let checksPassed = 0;
  let checksFailed = 0;
  let unexpectedRequests = 0;

  summaries.forEach((data, index) => {
    const metrics = data?.metrics || {};
    const values = (name) => metrics[name]?.values || {};
    const requireNumber = (value, label, rate = false) => {
      if (!finite(value) || (rate && value > 1)) {
        errors.push(`Runner ${index + 1}: missing or invalid ${label}`);
        return 0;
      }
      return value;
    };
    const requests = requireNumber(values('http_reqs').count, 'http_reqs.count');
    if (!requests) errors.push(`Runner ${index + 1}: no requests`);
    totals.requests += requests;
    totals.totalVus += requireNumber(values('vus_max').value, 'vus_max.value');
    totals.iterations += requireNumber(values('iterations').count, 'iterations.count');
    totals.failedRequests +=
      requests * requireNumber(values('http_req_failed').rate, 'http_req_failed.rate', true);
    const unexpected =
      requests * requireNumber(values('unexpected_status').rate, 'unexpected_status.rate', true);
    checksPassed += requireNumber(values('checks').passes, 'checks.passes');
    checksFailed += requireNumber(values('checks').fails, 'checks.fails');
    requireNumber(values('checks').rate, 'checks.rate', true);

    const addTrend = (name, count, target, failures = 0) => {
      const trend = values(name);
      if (!count || !STATS.every((stat) => finite(trend[stat]))) {
        errors.push(`Runner ${index + 1}: missing samples or latency statistics for ${name}`);
      } else {
        target.push({ requests: count, values: trend, failures });
      }
    };
    addTrend('http_req_duration', requests, overall);
    const checks = groupChecks(data?.root_group);
    let endpointRequests = 0;
    let endpointFailures = 0;
    for (const [endpoint, expected] of Object.entries(ENDPOINTS)) {
      // One exact-status check per request in public-paths.js, even in partial iterations.
      const matches = checks.filter((check) => check.name === `${endpoint}: status ${expected}`);
      const passes = sum(matches, (check) =>
        requireNumber(check.passes, `${endpoint} check passes`),
      );
      const failures = sum(matches, (check) =>
        requireNumber(check.fails, `${endpoint} check fails`),
      );
      const count = passes + failures;
      endpointRequests += count;
      endpointFailures += failures;
      addTrend(`http_req_duration{endpoint:${endpoint}}`, count, rows[endpoint], failures);
    }
    unexpectedRequests += endpointFailures;
    if (endpointRequests !== requests || Math.abs(endpointFailures - unexpected) > 1e-6) {
      errors.push(
        `Runner ${index + 1}: endpoint checks disagree with request or unexpected-status totals`,
      );
    }
    for (const [name, metric] of Object.entries(metrics)) {
      for (const [threshold, result] of Object.entries(metric.thresholds || {})) {
        if (result.ok !== true) failedThresholds.push(`Runner ${index + 1}: ${name} ${threshold}`);
      }
    }
    statuses.push(statusCounts(metrics));
  });

  totals.failedRequestRate = totals.requests ? totals.failedRequests / totals.requests : null;
  totals.unexpectedStatusRate = totals.requests ? unexpectedRequests / totals.requests : null;
  totals.checksPassRate =
    checksPassed + checksFailed ? checksPassed / (checksPassed + checksFailed) : null;
  const endpoints = Object.fromEntries(
    Object.entries(rows).map(([name, samples]) => {
      const result = latency(samples);
      return [
        name,
        {
          ...result,
          expectedStatus: ENDPOINTS[name],
          unexpectedStatusRate: result.requests
            ? sum(samples, (row) => row.failures) / result.requests
            : null,
        },
      ];
    }),
  );
  const runnerIps = (options.metadata || []).map((meta) => ({
    index: meta.index,
    publicIp: isIP(meta.publicIp || '') ? meta.publicIp : null,
    k6ExitCode: meta.k6ExitCode ?? null,
  }));
  const statusBreakdown =
    statuses.length && statuses.every(Boolean)
      ? {
          429: sum(statuses, (counts) => counts['429'] || 0),
          403: sum(statuses, (counts) => counts['403'] || 0),
          '5xx': sum(statuses, (counts) =>
            sum(Object.entries(counts), ([code, count]) => (code.startsWith('5') ? count : 0)),
          ),
        }
      : null;
  const overallLatency = latency(overall);
  const verdicts = [];
  const verdict = (target, pass, observed) => verdicts.push({ target, pass, observed });
  verdict(
    'All planned runners reported',
    Number.isInteger(expectedRunners) &&
      expectedRunners > 0 &&
      summaries.length === expectedRunners,
    `${summaries.length}/${expectedRunners}`,
  );
  verdict(
    'Complete metric evidence',
    errors.length === 0,
    errors.length ? errors.join('; ') : 'complete',
  );
  verdict(
    'Runner thresholds and execution',
    failedThresholds.length === 0 &&
      runnerIps.every((meta) => meta.k6ExitCode === null || meta.k6ExitCode === 0),
    failedThresholds.length ? failedThresholds.join('; ') : 'see runner exit codes',
  );
  const below = (target, observed, limit) =>
    verdict(target, observed !== null && observed < limit, observed);
  below('Overall p95 < 1000 ms (worst runner)', overallLatency.p95Worst, 1000);
  for (const [name, endpoint] of Object.entries(endpoints)) {
    below(`${name} p95 < 1000 ms (worst runner)`, endpoint.p95Worst, 1000);
  }
  below('Failed-request rate < 1%', totals.failedRequestRate, 0.01);
  below('Unexpected-status rate < 1%', totals.unexpectedStatusRate, 0.01);
  verdict(
    'Checks pass rate > 99%',
    totals.checksPassRate !== null && totals.checksPassRate > 0.99,
    totals.checksPassRate,
  );
  return {
    pass: verdicts.every((item) => item.pass),
    expectedRunners,
    totals,
    overall: overallLatency,
    endpoints,
    runnerIps,
    distinctPublicIps: new Set(runnerIps.map((meta) => meta.publicIp).filter(Boolean)).size,
    statusBreakdown,
    errors,
    failedThresholds,
    verdicts,
    percentileNote:
      'Percentiles cannot be merged exactly. med, p90 and p95WeightedMean are request-count-weighted means of runner statistics; p95Worst and p99Worst are worst runner values, not pooled percentiles.',
    coverageNote:
      'No authenticated session was available (sign-in is Google-only). Authenticated ticket-list/Jira-App work is NOT covered. Rejection probes prove rejection, not authenticated performance.',
  };
}

function renderMarkdown(report) {
  const number = (value) => (value === null ? 'unavailable' : value.toFixed(2));
  const percent = (value) => (value === null ? 'unavailable' : `${(value * 100).toFixed(3)}%`);
  const { totals } = report;
  const lines = [
    `# Multi-IP k6 result: ${report.pass ? 'PASS' : 'FAIL'}`,
    '',
    `Runners: ${totals.runners}/${report.expectedRunners}; total VUs (sum of vus_max): ${totals.totalVus}; requests: ${totals.requests}; iterations: ${totals.iterations}.`,
    `Failed requests: ${number(totals.failedRequests)} (${percent(totals.failedRequestRate)}); unexpected-status rate: ${percent(totals.unexpectedStatusRate)}; checks pass rate: ${percent(totals.checksPassRate)}.`,
    '',
    'Failed requests use http_req_failed: transport errors or responses outside 200/401. Exact-status checks also count a 401 on a public endpoint, or any non-401 on a rejection probe, as an error.',
    '',
    report.percentileNote,
    'All latency values below are in milliseconds. Overall worst runner p95: ' +
      number(report.overall.p95Worst) +
      '.',
    '',
    report.coverageNote,
  ];
  for (const expected of [200, 401]) {
    lines.push(
      '',
      `## ${expected === 401 ? 'rejection probes (401 expected)' : 'Public endpoints (200 expected)'}`,
      '',
      '| Endpoint | Requests | Avg | Med (weighted) | p90 (weighted) | p95 (worst) | p95 (weighted mean) | p99 (worst) | Max | Unexpected status |',
      '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |',
    );
    for (const [name, endpoint] of Object.entries(report.endpoints)) {
      if (endpoint.expectedStatus !== expected) continue;
      lines.push(
        `| ${name} | ${endpoint.requests} | ${number(endpoint.avg)} | ${number(endpoint.med)} | ${number(endpoint.p90)} | ${number(endpoint.p95Worst)} | ${number(endpoint.p95WeightedMean)} | ${number(endpoint.p99Worst)} | ${number(endpoint.max)} | ${percent(endpoint.unexpectedStatusRate)} |`,
      );
    }
  }
  lines.push(
    '',
    report.statusBreakdown
      ? `Status counts: 429: ${report.statusBreakdown['429']}; 403: ${report.statusBreakdown['403']}; 5xx: ${report.statusBreakdown['5xx']}.`
      : 'The summary has no per-status breakdown (in one or more runners); separate 429/403/5xx counts are unavailable.',
  );
  lines.push(
    '',
    '## Runner IPs',
    '',
    `Distinct public IPs observed: ${report.distinctPublicIps}. GitHub IP ranges are public; runners may share a NAT. Unknown IPs do not prove distinct sources.`,
    '',
    '| Runner | Public IP | k6 exit code |',
    '| --- | --- | --- |',
  );
  for (const meta of report.runnerIps)
    lines.push(
      `| ${meta.index} | ${meta.publicIp || 'unknown'} | ${meta.k6ExitCode ?? 'unknown'} |`,
    );
  lines.push(
    '',
    '## Verdict',
    '',
    '| Target | Result | Observed (rates are fractions) |',
    '| --- | --- | --- |',
  );
  for (const item of report.verdicts)
    lines.push(
      `| ${item.target} | ${item.pass ? 'PASS' : 'FAIL'} | ${item.observed ?? 'unavailable'} |`,
    );
  return lines.join('\n') + '\n';
}

function main(args = process.argv.slice(2)) {
  const [directory, output] = args;
  if (!directory || !output)
    throw new Error('Usage: node merge-summaries.js <summary-directory> <output.json>');
  const summaries = [];
  const metadata = [];
  const errors = [];
  let files = [];
  try {
    files = fs
      .readdirSync(directory)
      .filter((name) => /^summary-.+\.json$/.test(name))
      .sort();
  } catch (error) {
    errors.push(`Cannot read summary directory: ${error.message}`);
  }
  for (const file of files) {
    try {
      summaries.push(JSON.parse(fs.readFileSync(path.join(directory, file), 'utf8')));
    } catch (error) {
      errors.push(`${file}: ${error.message}`);
    }
    const metaPath = path.join(directory, file.replace(/^summary-/, 'meta-'));
    let meta = {};
    if (fs.existsSync(metaPath)) {
      try {
        meta = JSON.parse(fs.readFileSync(metaPath, 'utf8'));
      } catch (error) {
        errors.push(`${path.basename(metaPath)}: ${error.message}`);
      }
    }
    metadata.push({ ...meta, index: file.slice(8, -5) });
  }
  const expectedRunners =
    process.env.EXPECTED_RUNNERS === undefined
      ? summaries.length
      : Number(process.env.EXPECTED_RUNNERS);
  const report = mergeSummaries(summaries, { expectedRunners, metadata, errors });
  const markdown = renderMarkdown(report);
  const markdownPath = path.join(
    path.dirname(output),
    `${path.basename(output, path.extname(output))}.md`,
  );
  fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
  fs.writeFileSync(markdownPath, markdown);
  process.stdout.write(markdown);
  return report.pass ? 0 : 1;
}

module.exports = { mergeSummaries, renderMarkdown, main };
if (require.main === module) {
  try {
    process.exitCode = main();
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
