import { act, cleanup, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, vi, type MockInstance } from 'vitest';
import type { QueryClient } from '@tanstack/react-query';
import { getGetReviewQueueQueryKey, getGetReviewQueueTicketIdsQueryKey } from '@/shared/api';
import { renderWithProviders } from '@/test/render';
import { FakeEventSource } from '../mocks/fake-event-source';
import {
  REVIEW_QUEUE_POLL_MS,
  REVIEW_QUEUE_SAFETY_REFETCH_MS,
  useReviewQueue,
} from './useReviewQueue';
import { useReviewQueueEvents } from './useReviewQueueEvents';

function Probe() {
  const { connected } = useReviewQueueEvents();
  useReviewQueue({ page: 0, size: 20 }, { live: connected });
  return <p>{connected ? 'live' : 'polling'}</p>;
}

function refetchInterval(client: ReturnType<typeof renderWithProviders>['queryClient']) {
  const query = client
    .getQueryCache()
    .find({ queryKey: getGetReviewQueueQueryKey(), exact: false });
  return query?.observers[0]?.options.refetchInterval;
}

async function mount() {
  const view = renderWithProviders(<Probe />);
  await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
  const source = FakeEventSource.last;
  const spy = vi.spyOn(view.queryClient, 'invalidateQueries');
  return { ...view, source, spy };
}

const invalidatedKeys = (spy: MockInstance<QueryClient['invalidateQueries']>) =>
  spy.mock.calls.map(([filters]) => JSON.stringify(filters?.queryKey));

describe('useReviewQueueEvents', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_MOCKING', 'false');
    FakeEventSource.install();
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it('AC-012.5 opens the credentialed stream and invalidates queue and ticket-ids on queue-changed', async () => {
    const { source, spy } = await mount();
    expect(source.url).toBe('/api/v1/review-queue/events');
    expect(source.init).toEqual({ withCredentials: true });
    act(() => source.emit('queue-changed'));
    const keys = invalidatedKeys(spy);
    expect(keys).toContain(JSON.stringify(getGetReviewQueueQueryKey()));
    expect(keys).toContain(JSON.stringify(getGetReviewQueueTicketIdsQueryKey()));
  });

  it('AC-012.5 edge 1 ignores an unrelated event name', async () => {
    const { source, spy } = await mount();
    act(() => source.emit('something-else'));
    expect(spy).not.toHaveBeenCalled();
  });

  it('AC-012.6 does not poll while the stream is open', async () => {
    const { source, queryClient } = await mount();
    expect(refetchInterval(queryClient)).toBe(REVIEW_QUEUE_POLL_MS);
    act(() => source.emit('open'));
    await screen.findByText('live');
    expect(refetchInterval(queryClient)).toBe(REVIEW_QUEUE_SAFETY_REFETCH_MS);
  });

  it('AC-012.6 edge BUG-175 stays on polling without a stream in mock mode', async () => {
    vi.stubEnv('DEV', true);
    vi.stubEnv('VITE_API_MOCKING', 'true');
    const { queryClient } = renderWithProviders(<Probe />);
    await screen.findByText('polling');
    expect(FakeEventSource.instances).toHaveLength(0);
    expect(refetchInterval(queryClient)).toBe(REVIEW_QUEUE_POLL_MS);
  });

  it('AC-012.6 edge BUG-176 keeps a 60 s safety refetch while connected', async () => {
    const { source, queryClient } = await mount();
    act(() => source.emit('open'));
    await screen.findByText('live');
    expect(refetchInterval(queryClient)).toBe(60_000);
  });

  it('AC-012.6 polls every 5 s again after the stream fails', async () => {
    const { source, queryClient } = await mount();
    act(() => source.emit('open'));
    await screen.findByText('live');
    act(() => source.emit('error'));
    await screen.findByText('polling');
    expect(refetchInterval(queryClient)).toBe(REVIEW_QUEUE_POLL_MS);
    expect(source.closed).toBe(false); // EventSource retries by itself
  });

  it('AC-012.6 refetches the queue once on every (re)open', async () => {
    const { source, spy } = await mount();
    act(() => source.emit('open'));
    const queueKey = JSON.stringify(getGetReviewQueueQueryKey());
    expect(invalidatedKeys(spy).filter((key) => key === queueKey)).toHaveLength(1);
    act(() => source.emit('error'));
    act(() => source.emit('open'));
    expect(invalidatedKeys(spy).filter((key) => key === queueKey)).toHaveLength(2);
  });

  it('AC-012.7 closes the stream when the screen unmounts', async () => {
    const { source, unmount } = await mount();
    expect(source.closed).toBe(false);
    unmount();
    expect(source.closed).toBe(true);
  });

  it('AC-012.7 edge 2 a remounted screen opens a fresh stream and the old one stays closed', async () => {
    const first = await mount();
    first.unmount();
    renderWithProviders(<Probe />);
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(2));
    expect(first.source.closed).toBe(true);
    expect(FakeEventSource.last.closed).toBe(false);
  });
});
