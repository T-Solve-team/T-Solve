import { act, cleanup, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, beforeEach, vi } from 'vitest';
import { renderApp } from '@/test/render';
import { FakeEventSource } from '../mocks/fake-event-source';

describe('ReviewQueuePage live refresh', () => {
  beforeAll(async () => {
    await import('./ReviewQueuePage');
  });
  beforeEach(() => FakeEventSource.install());
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  async function open() {
    const view = renderApp('/review-queue', { role: 'PROJECT_MANAGER' });
    await screen.findByRole('link', { name: 'ITSUP-1031' }, { timeout: 5000 });
    return view;
  }

  it('AC-012.7 the page opens the event stream and closes it when the user leaves', async () => {
    const view = await open();
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    const source = FakeEventSource.last;
    expect(source.url).toBe('/api/v1/review-queue/events');
    view.unmount();
    expect(source.closed).toBe(true);
  });

  it('AC-012.5 keeps the selection after a queue-changed event refetches the queue', async () => {
    const user = userEvent.setup();
    const view = await open();
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    const firstRow = screen.getAllByRole('checkbox').at(1);
    if (!firstRow) throw new Error('the queue shows no ticket row');
    await user.click(firstRow);
    expect(firstRow).toBeChecked();
    const before = view.queryClient.getQueryCache().findAll({ queryKey: ['/api/v1/review-queue'] });
    const updatedAt = before[0]?.state.dataUpdatedAt ?? 0;
    act(() => FakeEventSource.last.emit('queue-changed'));
    await waitFor(() => {
      const state = view.queryClient
        .getQueryCache()
        .findAll({ queryKey: ['/api/v1/review-queue'] })[0]?.state;
      expect(state?.dataUpdatedAt).toBeGreaterThan(updatedAt);
    });
    expect(screen.getAllByRole('checkbox')[1]).toBeChecked();
  });
});
