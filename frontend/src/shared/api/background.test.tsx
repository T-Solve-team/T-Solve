import { focusManager, onlineManager, useMutation } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { AppProviders } from '@/app/providers/AppProviders';
import { REVIEW_QUEUE_POLL_MS, useReviewQueue } from '@/features/review/hooks/useReviewQueue';
import { server } from '@/mocks/server';
import { approveTickets, useGetReviewQueue } from '@/shared/api';
import { useSignOut } from '@/shared/auth';
import { createTestQueryClient } from '@/test/render';

/**
 * AC-012.6 / AC-012.7: a refetch of a query that already holds data carries `X-TSolve-Background: 1`; a first
 * load, a mutation, sign-in and logout never do. The requests are recorded as MSW receives them.
 */
const BACKGROUND = 'X-TSolve-Background';
const QUEUE_PATH = '/api/v1/review-queue';

type Sent = { method: string; path: string; page: string | null; background: string | null };
let sent: Sent[] = [];

function record({ request }: { request: Request }) {
  const url = new URL(request.url);
  sent.push({
    method: request.method,
    path: url.pathname,
    page: url.searchParams.get('page'),
    background: request.headers.get(BACKGROUND),
  });
}

const queueRequests = () => sent.filter((r) => r.method === 'GET' && r.path === QUEUE_PATH);
const backgroundOf = (requests: Sent[]) => requests.map((r) => r.background);

function setup() {
  const queryClient = createTestQueryClient();
  const wrapper = ({ children }: { children: ReactNode }) => (
    <AppProviders queryClient={queryClient}>{children}</AppProviders>
  );
  return { queryClient, wrapper };
}

beforeEach(() => {
  sent = [];
  server.events.on('request:start', record);
});

afterEach(() => {
  server.events.removeListener('request:start', record);
  focusManager.setFocused(undefined);
  onlineManager.setOnline(true);
  vi.useRealTimers();
});

describe('the background-request header', () => {
  it('AC-012.7 the first load of a query has no X-TSolve-Background header', async () => {
    const { wrapper } = setup();
    const { result } = renderHook(() => useGetReviewQueue(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(backgroundOf(queueRequests())).toEqual([null]);
  });

  it('AC-012.7 the first load of a new query key has no header while another key holds data', async () => {
    const { wrapper } = setup();
    const { result, rerender } = renderHook(({ page }) => useGetReviewQueue({ page }), {
      wrapper,
      initialProps: { page: 0 },
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    rerender({ page: 1 });
    await waitFor(() => expect(queueRequests()).toHaveLength(2));

    expect(queueRequests().map((r) => [r.page, r.background])).toEqual([
      ['0', null],
      ['1', null],
    ]);
  });

  it('AC-012.6 a refetchInterval refetch of a query that holds data carries the header', async () => {
    const { wrapper } = setup();
    renderHook(() => useGetReviewQueue(undefined, { query: { refetchInterval: 50 } }), {
      wrapper,
    });

    await waitFor(() => expect(queueRequests().length).toBeGreaterThanOrEqual(3));
    expect(backgroundOf(queueRequests()).slice(0, 3)).toEqual([null, '1', '1']);
  });

  it('AC-012.6 a refetch on window focus carries the header', async () => {
    const { wrapper } = setup();
    const { result } = renderHook(() => useGetReviewQueue(undefined, { query: { staleTime: 0 } }), {
      wrapper,
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    act(() => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });

    await waitFor(() => expect(queueRequests()).toHaveLength(2));
    expect(backgroundOf(queueRequests())).toEqual([null, '1']);
  });

  it('AC-012.6 a refetch on reconnect carries the header', async () => {
    const { wrapper } = setup();
    const { result } = renderHook(() => useGetReviewQueue(undefined, { query: { staleTime: 0 } }), {
      wrapper,
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    act(() => {
      onlineManager.setOnline(false);
      onlineManager.setOnline(true);
    });

    await waitFor(() => expect(queueRequests()).toHaveLength(2));
    expect(backgroundOf(queueRequests())).toEqual([null, '1']);
  });

  it('AC-012.6 a refetch after invalidation carries the header', async () => {
    const { queryClient, wrapper } = setup();
    const { result } = renderHook(() => useGetReviewQueue(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    await act(() => queryClient.invalidateQueries({ queryKey: [QUEUE_PATH] }));

    await waitFor(() => expect(queueRequests()).toHaveLength(2));
    expect(backgroundOf(queueRequests())).toEqual([null, '1']);
  });

  it('AC-012.7 a mutation sends no header while queries hold data', async () => {
    const { wrapper } = setup();
    const { result } = renderHook(
      () => ({
        queue: useGetReviewQueue(),
        approve: useMutation({
          mutationFn: (ticketIds: number[]) => approveTickets({ ticketIds }),
          meta: { handlesOwnErrors: true },
        }),
      }),
      { wrapper },
    );
    await waitFor(() => expect(result.current.queue.isSuccess).toBe(true));
    const ticketId = result.current.queue.data?.content[0]?.ticketId ?? 1;

    await act(() => result.current.approve.mutateAsync([ticketId]).catch(() => undefined));

    const posts = sent.filter((r) => r.method === 'POST' && r.path === '/api/v1/reviews/approve');
    expect(backgroundOf(posts)).toEqual([null]);
  });

  it('AC-012.7 logout sends no header', async () => {
    const { wrapper } = setup();
    const { result } = renderHook(() => ({ queue: useGetReviewQueue(), signOut: useSignOut() }), {
      wrapper,
    });
    await waitFor(() => expect(result.current.queue.isSuccess).toBe(true));

    await act(() => result.current.signOut());

    const logouts = sent.filter((r) => r.method === 'POST' && r.path === '/api/v1/auth/logout');
    expect(backgroundOf(logouts)).toEqual([null]);
  });

  it('edge 27 every Review Queue poll carries the header', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { wrapper } = setup();
    renderHook(() => useReviewQueue({}), { wrapper });
    await waitFor(() => expect(queueRequests()).toHaveLength(1));

    for (const polls of [2, 3, 4]) {
      await act(() => vi.advanceTimersByTimeAsync(REVIEW_QUEUE_POLL_MS));
      await waitFor(() => expect(queueRequests()).toHaveLength(polls));
    }

    expect(backgroundOf(queueRequests())).toEqual([null, '1', '1', '1']);
  });

  it('edge 28 returning to a page whose query holds data refetches with the header; a new page loads without it', async () => {
    const { wrapper } = setup();
    const { result, rerender } = renderHook(
      ({ page }) => useGetReviewQueue({ page }, { query: { staleTime: 0 } }),
      { wrapper, initialProps: { page: 0 } },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    rerender({ page: 1 }); // a new page: first load
    await waitFor(() => expect(queueRequests()).toHaveLength(2));
    rerender({ page: 0 }); // back to a page whose data is still cached
    await waitFor(() => expect(queueRequests()).toHaveLength(3));

    expect(queueRequests().map((r) => [r.page, r.background])).toEqual([
      ['0', null],
      ['1', null],
      ['0', '1'],
    ]);
  });
});
