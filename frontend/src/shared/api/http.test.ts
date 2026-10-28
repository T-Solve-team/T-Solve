import { http as mock, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { markBackground } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { env } from '@/shared/config/env';
import { ApiError } from './errors';
import { http } from './http';

const URL = '/api/v1/test-endpoint';
const BACKGROUND = 'X-TSolve-Background';

/** Answers `URL` (any method) with an empty JSON body and keeps every request it received. */
function captureRequests(): Request[] {
  const requests: Request[] = [];
  server.use(
    mock.all(`*${URL}`, ({ request }) => {
      requests.push(request.clone());
      return HttpResponse.json({});
    }),
  );
  return requests;
}

async function failureOf(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ApiError) return error;
    throw error;
  }
  throw new Error('Expected the request to fail');
}

describe('http (fetch wrapper)', () => {
  it('returns the JSON body of a 2xx response', async () => {
    server.use(mock.get(`*${URL}`, () => HttpResponse.json({ count: 3 })));
    await expect(http<{ count: number }>(URL)).resolves.toEqual({ count: 3 });
  });

  it('returns undefined for 204 No Content', async () => {
    server.use(mock.post(`*${URL}`, () => new HttpResponse(null, { status: 204 })));
    await expect(http<void>(URL, { method: 'POST' })).resolves.toBeUndefined();
  });

  it('turns a Problem Details response into an ApiError with the MSG code', async () => {
    server.use(
      mock.post(`*${URL}`, () =>
        problem(409, 'MSG49', {
          params: { department_name: 'IT' },
          fieldErrors: [{ field: 'jiraProjectKey', code: 'MSG49' }],
        }),
      ),
    );
    const error = await failureOf(http(URL, { method: 'POST' }));
    expect(error.status).toBe(409);
    expect(error.code).toBe('MSG49');
    expect(error.params).toEqual({ department_name: 'IT' });
    expect(error.fieldErrors).toEqual([{ field: 'jiraProjectKey', code: 'MSG49', params: {} }]);
  });

  it.each([
    [401, 'MSG07'],
    [403, 'MSG08'],
    [500, 'MSG06'],
  ] as const)('uses %s → %s when the server sends no known code', async (status, code) => {
    server.use(mock.get(`*${URL}`, () => HttpResponse.text('Internal error', { status })));
    expect((await failureOf(http(URL))).code).toBe(code);
  });

  it('ignores an unknown code (MSG06)', async () => {
    server.use(
      mock.get(`*${URL}`, () =>
        HttpResponse.json({ status: 400, code: 'MSG999' }, { status: 400 }),
      ),
    );
    expect((await failureOf(http(URL))).code).toBe('MSG06');
  });

  it('reports a network failure as status 0, MSG06', async () => {
    server.use(mock.get(`*${URL}`, () => HttpResponse.error()));
    const error = await failureOf(http(URL));
    expect(error.status).toBe(0);
    expect(error.code).toBe('MSG06');
  });
});

describe('http and the Workspace site', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it('AC-011.1 sends every request to its own origin as a relative /api/v1 path', async () => {
    // Even a build that still sets the removed variable must not send requests elsewhere.
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.other.example');
    vi.resetModules();
    const { http: freshHttp } = await import('./http');
    const requests = captureRequests();

    await freshHttp(URL);

    expect(requests).toHaveLength(1);
    const sent = new window.URL(requests[0]?.url ?? '');
    expect(sent.origin).toBe(window.location.origin);
    expect(sent.pathname).toBe(URL);
  });

  it('AC-011.2 has no setting that sends API requests to another host', () => {
    expect(Object.keys(env)).not.toContain('apiBaseUrl');
  });

  it('AC-011.2 adds no Workspace, site or domain header', async () => {
    const requests = captureRequests();

    await http(URL);
    await http(URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ok: true }),
    });

    expect(requests).toHaveLength(2);
    for (const request of requests) {
      // `host` is the page's own host, set by the browser: it is what decides the Workspace (AC-011.1).
      const names = [...request.headers.keys()];
      expect(names.filter((name) => /workspace|site|domain|tenant/i.test(name))).toEqual([]);
    }
  });

  it('AC-012.6 adds X-TSolve-Background: 1 when the request’s signal is marked background', async () => {
    const requests = captureRequests();
    const controller = new AbortController();

    markBackground(controller.signal);
    await http(URL, { signal: controller.signal });

    expect(requests[0]?.headers.get(BACKGROUND)).toBe('1');
  });

  it('AC-012.7 sends no X-TSolve-Background header for an unmarked signal or no signal', async () => {
    const requests = captureRequests();

    await http(URL, { signal: new AbortController().signal });
    await http(URL);

    expect(requests).toHaveLength(2);
    for (const request of requests) expect(request.headers.has(BACKGROUND)).toBe(false);
  });
});
