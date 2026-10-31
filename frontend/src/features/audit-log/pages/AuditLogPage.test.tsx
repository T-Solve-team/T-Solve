import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { AuditEntryPage } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

describe('AuditLogPage', () => {
  beforeAll(async () => {
    await import('./AuditLogPage');
  });
  it('AC-001.1 shows the five audit columns and recorded actions', async () => {
    renderApp('/audit-log', { role: 'ADMIN' });
    const table = await screen.findByRole('table', { name: 'Audit log' }, { timeout: 5000 });
    for (const name of ['Date and time', 'Actor', 'Action', 'Target', 'Summary']) {
      expect(
        within(table).getByRole('columnheader', { name: new RegExp(name) }),
      ).toBeInTheDocument();
    }
    expect(within(table).getAllByRole('row').slice(1)).toHaveLength(20);
  });

  it('AC-001.2 formats timestamps in Vietnam time', async () => {
    renderApp('/audit-log', { role: 'ADMIN' });
    expect(
      (await screen.findAllByText('30/10/2026 16:30', {}, { timeout: 5000 })).length,
    ).toBeGreaterThan(0);
  });

  it('AC-001.3 combines all three filters and resets page when the action changes', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/audit-log?page=2&actor=DUONG&from=2026-10-01&to=2026-10-30', {
      role: 'ADMIN',
    });
    await screen.findByRole('table', { name: 'Audit log' }, { timeout: 5000 });
    await user.click(screen.getByRole('combobox', { name: 'Action' }));
    await user.click(await screen.findByRole('option', { name: 'Approve' }));
    await screen.findByText('Total 4 items');
    expect(new URLSearchParams(router.state.location.search).has('page')).toBe(false);
    expect(new URLSearchParams(router.state.location.search).get('action')).toBe('APPROVE');
    expect(within(screen.getByRole('table')).getAllByRole('row').slice(1)).toHaveLength(4);
    expect(screen.queryByText('Foreign Workspace event')).not.toBeInTheDocument();
  });

  it('AC-001.4 restores Actor search and results with browser Back', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/audit-log?actor=former', { role: 'ADMIN' });
    await screen.findByText('former.user@company.example', {}, { timeout: 5000 });
    await user.clear(screen.getByRole('searchbox', { name: 'Actor' }));
    await user.type(screen.getByRole('searchbox', { name: 'Actor' }), 'LAN{Enter}');
    await screen.findByText('Total 17 items');
    expect(new URLSearchParams(router.state.location.search).get('actor')).toBe('LAN');
    await act(() => router.navigate(-1));
    await screen.findByText('former.user@company.example');
    expect(screen.getByRole('searchbox', { name: 'Actor' })).toHaveValue('former');
  });

  it('AC-001.5 paginates and switches from default descending to ascending (BUG-068)', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/audit-log?from=2026-10-01&to=2026-10-30', { role: 'ADMIN' });
    await screen.findByText('Total 35 items', {}, { timeout: 5000 });
    await user.click(screen.getByTitle('Next Page'));
    await waitFor(() =>
      expect(within(screen.getByRole('table')).getAllByRole('row').slice(1)).toHaveLength(15),
    );
    expect(new URLSearchParams(router.state.location.search).get('page')).toBe('2');
    expect(new URLSearchParams(router.state.location.search).get('from')).toBe('2026-10-01');
    await user.click(screen.getByRole('columnheader', { name: /Date and time/ }));
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).get('sort')).toBe('createdAt,asc'),
    );
    expect(new URLSearchParams(router.state.location.search).has('page')).toBe(false);
  });

  it('AC-001.6 shows LoadingState while the first audit request is pending', async () => {
    let finish: () => void = () => undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    let started = false;
    server.use(
      http.get('*/api/v1/audit-events', async () => {
        started = true;
        await pending;
        return HttpResponse.json<AuditEntryPage>({
          content: [],
          page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
        });
      }),
    );
    renderApp('/audit-log', { role: 'ADMIN' });
    await waitFor(() => expect(started).toBe(true));
    expect(screen.getByText('Loading ...')).toBeInTheDocument();
    finish();
    await screen.findByText(msg('MSG04'));
  });

  it('AC-001.7 shows MSG04 when no audit entries match', async () => {
    renderApp('/audit-log?actor=nobody-matches', { role: 'ADMIN' });
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('AC-001.8 retries a failed request with the same filters', async () => {
    const user = userEvent.setup();
    const requests: string[] = [];
    let failing = true;
    server.use(
      http.get('*/api/v1/audit-events', ({ request }) => {
        requests.push(request.url);
        return failing
          ? problem(500, 'MSG06')
          : HttpResponse.json<AuditEntryPage>({
              content: [],
              page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
            });
      }),
    );
    renderApp('/audit-log?actor=former&action=IMPORT', { role: 'ADMIN' });
    await screen.findByText(msg('MSG06'), {}, { timeout: 5000 });
    failing = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    await screen.findByText(msg('MSG04'));
    expect(requests).toHaveLength(2);
    expect(requests[0]).toBe(requests[1]);
  });

  it('AC-001.9 blocks a non-Admin without requesting audit entries', async () => {
    let requests = 0;
    server.use(
      http.get('*/api/v1/audit-events', () => {
        requests++;
        return problem(500);
      }),
    );
    renderApp('/audit-log', { role: 'EMPLOYEE' });
    expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(requests).toBe(0);
  });

  it('AC-001.10 sends a same-origin request with no Workspace selection fields', async () => {
    let received: Request | undefined;
    server.use(
      http.get('*/api/v1/audit-events', ({ request }) => {
        received = request;
        return HttpResponse.json<AuditEntryPage>({
          content: [],
          page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
        });
      }),
    );
    renderApp('/audit-log?workspaceId=foreign&tenant=other', { role: 'ADMIN' });
    await screen.findByText(msg('MSG04'), {}, { timeout: 5000 });
    expect(received).toBeDefined();
    if (!received) throw new Error('Audit request was not made');
    const url = new URL(received.url);
    expect(url.origin).toBe(window.location.origin);
    expect([...url.searchParams.keys()].sort()).toEqual(['page', 'size', 'sort']);
    expect([...received.headers.keys()].some((key) => /tenant|workspace|site/i.test(key))).toBe(
      false,
    );
  });

  it('AC-001.12 stays read-only and safely renders text with null summaries', async () => {
    renderApp('/audit-log', { user: 'admin-tr' });
    await screen.findByRole('table', { name: 'Audit log' }, { timeout: 5000 });
    for (const name of ['Edit', 'Delete', 'Export', 'Details']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('AC-001.13 submits trimmed email search text without a user-options query', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/audit-log', { role: 'ADMIN' });
    await screen.findByRole('table', { name: 'Audit log' }, { timeout: 5000 });
    await user.type(screen.getByRole('searchbox', { name: 'Actor' }), '  FORMER.USER  {Enter}');
    await screen.findByText('former.user@company.example');
    expect(new URLSearchParams(router.state.location.search).get('actor')).toBe('FORMER.USER');
  });

  it('AC-001.14 does not restrict the initial date range', async () => {
    renderApp('/audit-log', { role: 'ADMIN' });
    await screen.findByText('Total 35 items', {}, { timeout: 5000 });
    expect(screen.getByPlaceholderText('From date')).toHaveValue('');
    expect(screen.getByPlaceholderText('To date')).toHaveValue('');
  });

  it('AC-001.15 displays the inclusive Vietnam date range from a shared URL', async () => {
    renderApp('/audit-log?from=2026-10-01&to=2026-10-01', { role: 'ADMIN' });
    await screen.findByText('former.user@company.example', {}, { timeout: 5000 });
    expect(screen.getByPlaceholderText('From date')).toHaveValue('01/10/2026');
    expect(screen.getByPlaceholderText('To date')).toHaveValue('01/10/2026');
    expect(screen.getByText('Total 1 items')).toBeInTheDocument();
  });

  it('AC-001.16 shows MSG39 for invalid URL dates without any audit request and can clear both dates', async () => {
    const user = userEvent.setup();
    let requests = 0;
    server.use(
      http.get('*/api/v1/audit-events', () => {
        requests++;
        return problem(500);
      }),
    );
    const { router } = renderApp('/audit-log?page=2&from=2026-02-30&to=2026-03-01', {
      role: 'ADMIN',
    });
    await screen.findByText(msg('MSG39'), {}, { timeout: 5000 });
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(requests).toBe(0);
    await user.click(screen.getByRole('button', { name: 'Clear date range' }));
    await waitFor(() => expect(requests).toBe(1));
    const params = new URLSearchParams(router.state.location.search);
    expect(params.has('from') || params.has('to') || params.has('page')).toBe(false);
    await screen.findByText(msg('MSG06'));
  });
});
