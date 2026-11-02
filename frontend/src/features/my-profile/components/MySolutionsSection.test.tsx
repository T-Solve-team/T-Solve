import type { BoundFunctions, queries } from '@testing-library/dom';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
import { MY_SOLUTIONS } from '../mocks/data';

const SOLUTIONS_URL = /\/api\/v1\/me\/solutions$/;
const ELLIPSIS = '.ant-typography-ellipsis, .ant-table-cell-ellipsis';
const HEADERS = ['Ticket ID', 'Title', 'Project', 'Status', 'Latest decision', 'Date'];
const ticketId = (i: number) =>
  `00000000-0000-4000-8000-${(0x1000 + i).toString(16).padStart(12, '0')}`;

/** The My Solutions card: a region named "My Solutions" (the developer adds the accessible name). */
async function findMySolutionsCard() {
  return within(await screen.findByRole('region', { name: 'My Solutions' }, { timeout: 5000 }));
}

/** The table named "My Solutions" once it has rows. */
async function findTable() {
  return within(await screen.findByRole('table', { name: 'My Solutions' }, { timeout: 5000 }));
}

/** Records every `GET /me/solutions` query string while the real handler keeps answering. Call before `renderApp`. */
function solutionsRequests() {
  const spy = { calls: 0, queries: [] as string[] };
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (request.method === 'GET' && SOLUTIONS_URL.test(url.pathname)) {
      spy.calls += 1;
      spy.queries.push(url.search);
    }
  });
  return spy;
}

type Scoped = BoundFunctions<typeof queries>;

const COUNTERS = [
  ['PENDING_REVIEW', 'Pending review'],
  ['CHANGES_REQUESTED', 'Changes requested'],
  ['PUBLISHED', 'Published'],
  ['REJECTED', 'Rejected'],
  ['EXPIRED', 'Expired'],
  ['UNPUBLISHED', 'Unpublished'],
] as const;

const trCount = (status: string) =>
  (MY_SOLUTIONS.tr ?? []).filter((row) => row.status === status).length;

/** The number shown with a counter label. */
function counterValue(counters: Scoped, label: string) {
  let node: HTMLElement | null = counters.getByText(label, { exact: true });
  while (node && !/\d/.test(node.textContent ?? '')) node = node.parentElement;
  if (!node) throw new Error(`no value next to ${label}`);
  return (node.textContent ?? '').replace(label, '').trim();
}

/** Opens the select named `label` and clicks the option with that title. */
async function pickFilter(user: ReturnType<typeof userEvent.setup>, label: string, option: string) {
  await user.click(screen.getByRole('combobox', { name: label }));
  const found = await waitFor(() => {
    const el = screen
      .getAllByTitle(option)
      .find((e) => e.classList.contains('ant-select-item-option'));
    if (!el) throw new Error(`no option ${option}`);
    return el;
  });
  await user.click(found);
}

function bodyRows(table: Scoped) {
  return table.getAllByRole('row').slice(1);
}

function cellsOf(row: HTMLElement) {
  return within(row).getAllByRole('cell');
}

function rowOf(table: Scoped, key: string) {
  const row = bodyRows(table).find((r) => within(r).queryByText(key) !== null);
  if (!row) throw new Error(`no row for ${key}`);
  return row;
}

function generatedRows(count: number) {
  return Array.from({ length: count }, (_, i) => ({
    ticketId: ticketId(i),
    ticketKey: `GEN-${100 + i}`,
    title: `Generated ${i}`,
    projectId: '00000000-0000-4000-8000-0000000000c1',
    projectName: 'Helpdesk',
    status: 'PUBLISHED',
    updatedAt: '2026-10-29T12:00:00Z',
    canOpen: true,
    latestDecision: null,
  }));
}

/** Makes `/me/solutions` serve `rows` with offset paging, like the real endpoint (`page` 0-based, `size`). */
function solutionsAre(rows: ReturnType<typeof generatedRows>) {
  server.use(
    http.get('*/api/v1/me/solutions', ({ request }) => {
      const q = new URL(request.url).searchParams;
      const page = Number(q.get('page') ?? 0);
      const size = Number(q.get('size') ?? 20);
      return HttpResponse.json({
        content: rows.slice(page * size, (page + 1) * size),
        page: {
          size,
          number: page,
          totalElements: rows.length,
          totalPages: Math.ceil(rows.length / size),
        },
      });
    }),
  );
}

describe('MySolutionsSection', () => {
  afterEach(() => {
    server.events.removeAllListeners();
  });

  it("AC-003.11 lists tr's newest Solutions with the columns Ticket ID, Title, Project, Status, Latest decision and Date", async () => {
    renderApp('/profile', { user: 'tr' });
    const table = await findTable();
    expect(table.getAllByRole('columnheader').map((h) => h.textContent?.trim())).toEqual(HEADERS);
    const rows = bodyRows(table);
    expect(rows).toHaveLength(20);
    const first = cellsOf(rows[0] as HTMLElement).map((c) => c.textContent?.trim());
    expect(first).toEqual([
      'SUP-100',
      'Khắc phục lỗi đăng nhập VPN số 0',
      'Helpdesk',
      'Pending review',
      '—',
      '31/10/2026',
    ]);
  });

  it("AC-003.11 pages tr's 45 Solutions by 20 with no size changer and keeps the page in the URL", async () => {
    const user = userEvent.setup();
    const spy = solutionsRequests();
    const { router, container } = renderApp('/profile', { user: 'tr' });
    const card = await findMySolutionsCard();
    await findTable();
    expect(card.getByTitle('1')).toBeInTheDocument();
    expect(card.getByTitle('2')).toBeInTheDocument();
    expect(card.getByTitle('3')).toBeInTheDocument();
    expect(container.querySelector('.ant-pagination-options-size-changer')).toBeNull();
    await user.click(card.getByTitle('3'));
    await waitFor(() => expect(router.state.location.search).toBe('?page=3'));
    const table = await findTable();
    await waitFor(() => expect(bodyRows(table)).toHaveLength(5));
    expect(within(bodyRows(table)[0] as HTMLElement).getByText('SUP-140')).toBeInTheDocument();
    const last = new URLSearchParams(spy.queries[spy.queries.length - 1]);
    expect(last.get('page')).toBe('2');
    expect(last.get('size')).toBe('20');
    expect(last.has('sort')).toBe(false);
  });

  it('AC-003.12 clicking the Ticket ID of an openable row opens Ticket Detail', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/profile', { user: 'tr' });
    const table = await findTable();
    await user.click(table.getByRole('link', { name: 'SUP-100' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(`/tickets/${ticketId(0)}`));
  });

  it('AC-003.12 the Ticket ID of a row with canOpen false is plain text (edge 69)', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/profile', { user: 'tr' });
    const table = await findTable();
    const row = rowOf(table, 'SUP-103');
    expect(within(row).queryByRole('link')).toBeNull();
    expect(within(row).queryByRole('button')).toBeNull();
    await user.click(within(row).getByText('SUP-103'));
    expect(router.state.location.pathname).toBe('/profile');
  });

  it('AC-003.13 shows MSG04 in the table area for a resolver with no Solutions', async () => {
    const { container } = renderApp('/profile', { user: 'tr-two-departments' });
    const card = await findMySolutionsCard();
    expect(await card.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(card.getAllByRole('columnheader').map((h) => h.textContent?.trim())).toEqual(HEADERS);
    expect(container.querySelector('.ant-pagination')).toBeNull();
  });

  it('AC-003.23 shows the latest decision comment on one line with an ellipsis and the full text in a tooltip', async () => {
    const user = userEvent.setup();
    renderApp('/profile', { user: 'tr' });
    const table = await findTable();
    const text = within(rowOf(table, 'SUP-101')).getByText('Please add the steps you tried first.');
    expect(text.closest(ELLIPSIS)).not.toBeNull();
    await user.hover(text);
    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      'Please add the steps you tried first.',
    );
  });

  it('AC-003.23 shows "—" when there is no decision or the decision has no comment', async () => {
    renderApp('/profile', { user: 'tr' });
    const table = await findTable();
    for (const key of ['SUP-100', 'SUP-102']) {
      expect(cellsOf(rowOf(table, key))[4]?.textContent?.trim()).toBe('—');
    }
  });

  it('edge 58 cuts the 200-character title to one line with the full title on hover and keeps Vietnamese titles intact', async () => {
    const user = userEvent.setup();
    renderApp('/profile', { user: 'tr' });
    const table = await findTable();
    const long = 'A'.repeat(200);
    const title = within(rowOf(table, 'SUP-105')).getByText(long);
    expect(title.closest(ELLIPSIS)).not.toBeNull();
    await user.hover(title);
    expect(await screen.findByRole('tooltip')).toHaveTextContent(long);
    expect(table.getByText('Khắc phục lỗi đăng nhập VPN số 0')).toBeInTheDocument();
    expect(table.getAllByText('Dự án Hạ tầng').length).toBeGreaterThan(0);
  });

  it.each(['abc', '0', '-3', '1.5'])(
    'edge 59 reads ?page=%s as page 1 and requests page=0',
    async (value) => {
      const spy = solutionsRequests();
      renderApp(`/profile?page=${value}`, { user: 'tr' });
      const table = await findTable();
      expect(within(bodyRows(table)[0] as HTMLElement).getByText('SUP-100')).toBeInTheDocument();
      expect(new URLSearchParams(spy.queries[0]).get('page')).toBe('0');
    },
  );

  it('edge 61 shows one page for 1 and 20 Solutions and a page 2 with one row for 21', async () => {
    const user = userEvent.setup();
    for (const count of [1, 20]) {
      solutionsAre(generatedRows(count));
      const { container, unmount } = renderApp('/profile', { user: 'tr' });
      const table = await findTable();
      expect(bodyRows(table)).toHaveLength(count);
      expect(container.querySelector('.ant-pagination-item-2')).toBeNull();
      unmount();
    }
    solutionsAre(generatedRows(21));
    renderApp('/profile', { user: 'tr' });
    const card = await findMySolutionsCard();
    await findTable();
    await user.click(card.getByTitle('2'));
    const table = await findTable();
    await waitFor(() => expect(bodyRows(table)).toHaveLength(1));
  });

  it('edge 63 moves ?page=4 to the last page with history replace, and an empty list to page 1 with MSG04 (BUG-090)', async () => {
    const first = renderApp('/profile?page=4', { user: 'tr' });
    const table = await findTable();
    await waitFor(() => expect(first.router.state.location.search).toBe('?page=3'));
    expect(first.router.state.historyAction).toBe('REPLACE');
    // BUG-090: wait with a cheap text query, then check the row once. A role query (bodyRows) inside waitFor re-runs on
    // every DOM change and blocks the render it waits for: under load it used up the whole 1 s.
    expect(await table.findByText('SUP-140')).toBeInTheDocument();
    expect(within(bodyRows(table)[0] as HTMLElement).getByText('SUP-140')).toBeInTheDocument();
    first.unmount();

    const second = renderApp('/profile?page=2', { user: 'tr-two-departments' });
    const card = await findMySolutionsCard();
    expect(await card.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    await waitFor(() => expect(second.router.state.location.search).not.toBe('?page=2'));
    expect(second.router.state.location.search).toMatch(/^(\?page=1)?$/);
  });

  it('edge 64 going back from Ticket Detail keeps page 2 (BUG-088)', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/profile?page=2', { user: 'tr' });
    const table = await findTable();
    await user.click(table.getByRole('link', { name: 'SUP-120' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(`/tickets/${ticketId(20)}`));
    // The router state runs ahead of React's commit: wait until the old section has really left the DOM.
    await waitFor(
      () => expect(screen.queryByRole('region', { name: 'My Solutions' })).not.toBeInTheDocument(),
      { timeout: 5000 },
    );
    await router.navigate(-1);
    expect(router.state.location.pathname).toBe('/profile');
    expect(router.state.location.search).toBe('?page=2');
    // Re-query each try: a table found mid-render can be replaced before the assertion runs.
    await waitFor(
      () => {
        const again = within(screen.getByRole('table', { name: 'My Solutions' }));
        expect(within(bodyRows(again)[0] as HTMLElement).getByText('SUP-120')).toBeInTheDocument();
      },
      { timeout: 5000 },
    );
  });

  it('edge 65 shows a spinner in the section at once on the first load', async () => {
    server.use(http.get('*/api/v1/me/solutions', () => delay('infinite')));
    renderApp('/profile', { user: 'tr' });
    const card = await findMySolutionsCard();
    // Two spinners now: the counters' own (AC-003.21) and the table's.
    expect(card.getAllByText(/Loading/)).toHaveLength(2);
    expect(screen.queryByRole('table', { name: 'My Solutions' })).not.toBeInTheDocument();
  });

  it('edge 65 keeps the current rows while the next page loads and the last page clicked wins', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/profile', { user: 'tr' });
    const card = await findMySolutionsCard();
    const table = await findTable();
    await user.click(card.getByTitle('2'));
    expect(within(bodyRows(table)[0] as HTMLElement).getByText('SUP-100')).toBeInTheDocument();
    await user.click(card.getByTitle('3'));
    await waitFor(() => expect(router.state.location.search).toBe('?page=3'));
    const final = await findTable();
    await waitFor(() =>
      expect(within(bodyRows(final)[0] as HTMLElement).getByText('SUP-140')).toBeInTheDocument(),
    );
    expect(router.state.location.search).toBe('?page=3');
  });

  it('edge 72 tabs from the Date range end input to the first Ticket ID link and Enter opens Ticket Detail (review N5)', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/profile', { user: 'tr' });
    const table = await findTable();
    const link = table.getByRole('link', { name: 'SUP-100' });
    const end = within(screen.getByRole('group', { name: 'Date range' })).getByPlaceholderText(
      'End date',
    );
    end.focus();
    expect(end).toHaveFocus();
    for (let i = 0; i < 5 && document.activeElement !== link; i += 1) await user.tab();
    expect(link).toHaveFocus();
    await user.keyboard('{Enter}');
    await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/tickets\//));
  });

  it('edge 73 shows the Date in Vietnam time', async () => {
    renderApp('/profile', { user: 'tr' });
    const table = await findTable();
    expect(cellsOf(rowOf(table, 'SUP-100'))[5]?.textContent?.trim()).toBe('31/10/2026');
    expect(cellsOf(rowOf(table, 'SUP-101'))[5]?.textContent?.trim()).toBe('30/10/2026');
  });

  it('AC-003.13 no match: shows MSG04 in the table area and still shows the counters and the filters', async () => {
    const user = userEvent.setup();
    renderApp('/profile', { user: 'tr' });
    await findTable();
    await pickFilter(user, 'Status', 'Expired');
    await pickFilter(user, 'Project', 'Helpdesk');
    const card = await findMySolutionsCard();
    expect(await card.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(card.getAllByRole('columnheader').map((h) => h.textContent?.trim())).toEqual(HEADERS);
    expect(document.querySelector('.ant-pagination')).toBeNull();
    expect(
      screen.getByRole('combobox', { name: 'Status' }).closest('.ant-select'),
    ).toHaveTextContent('Expired');
    expect(
      screen.getByRole('combobox', { name: 'Project' }).closest('.ant-select'),
    ).toHaveTextContent('Helpdesk');
    const counters = within(screen.getByRole('group', { name: 'Solution counts' }));
    for (const [status, label] of COUNTERS) {
      expect(counterValue(counters, label)).toBe(String(trCount(status)));
    }
    expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
  });

  it('AC-003.22 going back from Ticket Detail keeps the filters and the page (edge 64, BUG-088)', async () => {
    const user = userEvent.setup();
    const search = '?from=2026-10-01&to=2026-10-31&page=2';
    const { router } = renderApp(`/profile${search}`, { user: 'tr' });
    const table = await findTable();
    await user.click(table.getByRole('link', { name: 'SUP-120' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(`/tickets/${ticketId(20)}`));
    await waitFor(
      () => expect(screen.queryByRole('region', { name: 'My Solutions' })).not.toBeInTheDocument(),
      { timeout: 5000 },
    );
    await router.navigate(-1);
    expect(router.state.location.search).toBe(search);
    await waitFor(
      () => {
        const again = within(screen.getByRole('table', { name: 'My Solutions' }));
        expect(within(bodyRows(again)[0] as HTMLElement).getByText('SUP-120')).toBeInTheDocument();
      },
      { timeout: 5000 },
    );
    const group = within(screen.getByRole('group', { name: 'Date range' }));
    expect(group.getByPlaceholderText<HTMLInputElement>('Start date').value).toBe('01/10/2026');
    expect(group.getByPlaceholderText<HTMLInputElement>('End date').value).toBe('31/10/2026');
  });

  it('AC-003.21 the counters are not refetched and do not change when filtering', async () => {
    const user = userEvent.setup();
    let summaryCalls = 0;
    server.events.on('request:start', ({ request }) => {
      if (
        request.method === 'GET' &&
        /\/me\/solutions\/summary$/.test(new URL(request.url).pathname)
      )
        summaryCalls += 1;
    });
    renderApp('/profile', { user: 'tr' });
    await findTable();
    const same = async () => {
      const counters = within(await screen.findByRole('group', { name: 'Solution counts' }));
      for (const [status, label] of COUNTERS) {
        await waitFor(() => expect(counterValue(counters, label)).toBe(String(trCount(status))));
      }
    };
    await same();
    expect(summaryCalls).toBe(1);
    await pickFilter(user, 'Status', 'Expired');
    await pickFilter(user, 'Project', 'Helpdesk');
    await same();
    expect(summaryCalls).toBe(1);
    for (const label of ['Status', 'Project']) {
      const clear = screen
        .getByRole('combobox', { name: label })
        .closest('.ant-select')
        ?.querySelector('.ant-select-clear');
      if (!clear) throw new Error(`no clear icon on ${label}`);
      await user.click(clear);
    }
    await waitFor(() =>
      expect(bodyRows(within(screen.getByRole('table', { name: 'My Solutions' })))).toHaveLength(
        20,
      ),
    );
    await same();
    expect(summaryCalls).toBe(1);
  });

  it('edge 89 paging a filtered list keeps the filters in the URL and the request', async () => {
    const user = userEvent.setup();
    const spy = solutionsRequests();
    const { router } = renderApp('/profile?from=2026-10-01&to=2026-10-31', { user: 'tr' });
    const card = await findMySolutionsCard();
    await findTable();
    await user.click(card.getByTitle('3'));
    await waitFor(() =>
      expect(router.state.location.search).toBe('?from=2026-10-01&to=2026-10-31&page=3'),
    );
    await waitFor(() => {
      const again = within(screen.getByRole('table', { name: 'My Solutions' }));
      expect(bodyRows(again)).toHaveLength(5);
      expect(within(bodyRows(again)[0] as HTMLElement).getByText('SUP-140')).toBeInTheDocument();
    });
    const last = new URLSearchParams(spy.queries[spy.queries.length - 1]);
    expect(last.get('page')).toBe('2');
    expect(last.get('from')).toBe('2026-10-01');
    expect(last.get('to')).toBe('2026-10-31');
  });

  it('edge 65 after a filter or page change the old rows stay and the table shows a busy state while it refetches (review N3)', async () => {
    const user = userEvent.setup();
    server.use(
      http.get('*/api/v1/me/solutions', async ({ request }) => {
        const q = new URL(request.url).searchParams;
        const status = q.get('status');
        await delay(status ? 600 : 0);
        const rows = (MY_SOLUTIONS.tr ?? []).filter((r) => !status || r.status === status);
        return HttpResponse.json({
          content: rows.slice(0, 20),
          page: { size: 20, number: 0, totalElements: rows.length, totalPages: 1 },
        });
      }),
    );
    renderApp('/profile', { user: 'tr' });
    await findMySolutionsCard();
    await findTable();
    const spinning = () => document.querySelector('.ant-spin-spinning');
    expect(spinning()).toBeNull();
    await pickFilter(user, 'Status', 'Expired');
    await waitFor(() => expect(spinning()).not.toBeNull());
    const table = within(screen.getByRole('table', { name: 'My Solutions' }));
    expect(within(bodyRows(table)[0] as HTMLElement).getByText('SUP-100')).toBeInTheDocument();
    await waitFor(() => expect(spinning()).toBeNull(), { timeout: 5000 });
    expect(bodyRows(within(screen.getByRole('table', { name: 'My Solutions' })))).toHaveLength(7);
  });

  it('edge 85 a well-formed projectId that is not one of my Projects is dropped once the summary has loaded', async () => {
    const spy = solutionsRequests();
    const stranger = '00000000-0000-4000-8000-0000000000c9';
    const { router } = renderApp(`/profile?projectId=${stranger}`, { user: 'tr' });
    await findTable();
    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(router.state.historyAction).toBe('REPLACE');
    await waitFor(() => {
      const again = within(screen.getByRole('table', { name: 'My Solutions' }));
      expect(within(bodyRows(again)[0] as HTMLElement).getByText('SUP-100')).toBeInTheDocument();
    });
    expect(new URLSearchParams(spy.queries[spy.queries.length - 1]).has('projectId')).toBe(false);
    expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
  });
});
