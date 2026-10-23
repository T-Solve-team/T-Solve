import { http as mock, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { ApiError } from './errors';
import { http } from './http';

const URL = '/api/v1/test-endpoint';

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
