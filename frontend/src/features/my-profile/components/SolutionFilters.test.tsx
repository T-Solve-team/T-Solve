import type { BoundFunctions, queries } from '@testing-library/dom';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { msg } from '@/shared/messages';
import { STATUS_TAGS } from '@/shared/ui';
import { renderApp } from '@/test/render';
import { MY_SOLUTIONS } from '../mocks/data';

type Scoped = BoundFunctions<typeof queries>;
type User = ReturnType<typeof userEvent.setup>;

const SOLUTIONS_URL = /\/api\/v1\/me\/solutions$/;
const NETWORK = '00000000-0000-4000-8000-0000000000c2';
const TR = MY_SOLUTIONS.tr ?? [];

/** The Status options in the counters' order (decision Q7), not the enum order. */
const STATUS_ORDER = [
  'PENDING_REVIEW',
  'CHANGES_REQUESTED',
  'PUBLISHED',
  'REJECTED',
  'EXPIRED',
  'UNPUBLISHED',
] as const;

/** Records every `GET /me/solutions` query string while the real handler keeps answering. Call before `renderApp`. */
function solutionsRequests() {
  const spy = { queries: [] as string[] };
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (request.method === 'GET' && SOLUTIONS_URL.test(url.pathname)) spy.queries.push(url.search);
  });
  return spy;
}

function lastParams(spy: { queries: string[] }) {
  return new URLSearchParams(spy.queries[spy.queries.length - 1]);
}

async function findTable() {
  return within(await screen.findByRole('table', { name: 'My Solutions' }, { timeout: 5000 }));
}

function bodyRows(table: Scoped) {
  return table.getAllByRole('row').slice(1);
}

/** The ticket keys of the rows now in the table, re-queried each time. */
function keys() {
  const table = within(screen.getByRole('table', { name: 'My Solutions' }));
  return bodyRows(table).map((r) => within(r).getAllByRole('cell')[0]?.textContent?.trim());
}

function combo(label: string) {
  return screen.getByRole('combobox', { name: label });
}

/** What a select shows now (its selection or placeholder text). */
function shown(label: string) {
  return combo(label).closest('.ant-select')?.textContent ?? '';
}

function findOption(title: string): Promise<HTMLElement> {
  return waitFor(() => {
    const option = screen
      .getAllByTitle(title)
      .find((el) => el.classList.contains('ant-select-item-option'));
    if (!option) throw new Error(`no option ${title}`);
    return option;
  });
}

async function pick(user: User, label: string, option: string) {
  await user.click(combo(label));
  await user.click(await findOption(option));
}

async function optionsOf(user: User, label: string) {
  await user.click(combo(label));
  await findOption(label === 'Status' ? 'All statuses' : 'All Projects');
  const titles = Array.from(document.querySelectorAll('.ant-select-item-option')).map(
    (el) => el.getAttribute('title') ?? '',
  );
  await user.keyboard('{Escape}');
  return titles;
}

function clearOf(label: string) {
  const clear = combo(label).closest('.ant-select')?.querySelector('.ant-select-clear');
  if (!clear) throw new Error(`no clear icon on ${label}`);
  return clear;
}

function rangeInputs() {
  const group = within(screen.getByRole('group', { name: 'Date range' }));
  return {
    start: group.getByPlaceholderText<HTMLInputElement>('Start date'),
    end: group.getByPlaceholderText<HTMLInputElement>('End date'),
  };
}

async function typeRange(user: User, from: string, to: string) {
  const { start, end } = rangeInputs();
  await user.click(start);
  await user.keyboard(`${from}{Enter}`);
  await user.click(end);
  await user.keyboard(`${to}{Enter}`);
}

describe('SolutionFilters', () => {
  afterEach(() => {
    server.events.removeAllListeners();
  });

  it('AC-003.22 shows the Status, Project and Date range filters with their defaults and no "Clear filters" button', async () => {
    const spy = solutionsRequests();
    const { router } = renderApp('/profile', { user: 'tr' });
    await findTable();
    expect(shown('Status')).toContain('All statuses');
    expect(shown('Project')).toContain('All Projects');
    const { start, end } = rangeInputs();
    expect(start.value).toBe('');
    expect(end.value).toBe('');
    expect(screen.queryByRole('button', { name: /clear filters/i })).not.toBeInTheDocument();
    expect(router.state.location.search).toBe('');
    const first = new URLSearchParams(spy.queries[0]);
    for (const key of ['status', 'projectId', 'from', 'to']) expect(first.has(key)).toBe(false);
  });

  it('AC-003.22 offers "All statuses" and the six statuses with their Status Tag labels', async () => {
    const user = userEvent.setup();
    renderApp('/profile', { user: 'tr' });
    await findTable();
    expect(await optionsOf(user, 'Status')).toEqual([
      'All statuses',
      ...STATUS_ORDER.map((s) => STATUS_TAGS[s].label),
    ]);
  });

  it('AC-003.22 offers "All Projects" and the Projects of the summary, sorted by name', async () => {
    const user = userEvent.setup();
    renderApp('/profile', { user: 'tr' });
    await findTable();
    const names = [...new Set(TR.map((r) => r.projectName))].sort((a, b) => a.localeCompare(b));
    await waitFor(async () => {
      expect(await optionsOf(user, 'Project')).toEqual(['All Projects', ...names]);
    });
    expect(names).toContain('Dự án Hạ tầng');
  });

  it('AC-003.22 filtering by Status puts status in the URL, sends it and narrows the table', async () => {
    const user = userEvent.setup();
    const spy = solutionsRequests();
    const { router } = renderApp('/profile', { user: 'tr' });
    await findTable();
    await pick(user, 'Status', 'Expired');
    await waitFor(() => expect(router.state.location.search).toBe('?status=EXPIRED'));
    await waitFor(() => expect(keys()).toHaveLength(7));
    expect(lastParams(spy).toString()).toBe('status=EXPIRED&page=0&size=20');
    expect(keys()[0]).toBe('SUP-105');
    const table = await findTable();
    for (const row of bodyRows(table)) expect(within(row).getByText('Expired')).toBeInTheDocument();
  });

  it('AC-003.22 filtering by Project sends projectId and combines with Status', async () => {
    const user = userEvent.setup();
    const spy = solutionsRequests();
    const { router } = renderApp('/profile?status=EXPIRED', { user: 'tr' });
    await findTable();
    await pick(user, 'Project', 'Network operations');
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).get('projectId')).toBe(NETWORK),
    );
    expect(new URLSearchParams(router.state.location.search).get('status')).toBe('EXPIRED');
    await waitFor(() => expect(keys()).toEqual(['SUP-105', 'SUP-117', 'SUP-129', 'SUP-141']));
    expect(lastParams(spy).get('status')).toBe('EXPIRED');
    expect(lastParams(spy).get('projectId')).toBe(NETWORK);
    const table = await findTable();
    for (const row of bodyRows(table))
      expect(within(row).getByText('Network operations')).toBeInTheDocument();
  });

  it('AC-003.22 a Date range typed as dd/MM/yyyy sends from and to as days, inclusive in Vietnam time (edge 86)', async () => {
    const user = userEvent.setup();
    const spy = solutionsRequests();
    const { router } = renderApp('/profile', { user: 'tr' });
    await findTable();
    await typeRange(user, '30/10/2026', '30/10/2026');
    await waitFor(() =>
      expect(router.state.location.search).toBe('?from=2026-10-30&to=2026-10-30'),
    );
    await waitFor(() => expect(keys()).toEqual(['SUP-101', 'SUP-102', 'SUP-103']));
    expect(lastParams(spy).get('from')).toBe('2026-10-30');
    expect(lastParams(spy).get('to')).toBe('2026-10-30');
    expect(keys()).not.toContain('SUP-100');
    const { start, end } = rangeInputs();
    expect(start.value).toBe('30/10/2026');
    expect(end.value).toBe('30/10/2026');
  });

  it('AC-003.22 each filter is clearable and clearing removes only its parameter', async () => {
    const user = userEvent.setup();
    const { router } = renderApp(
      `/profile?status=EXPIRED&projectId=${NETWORK}&from=2026-10-01&to=2026-10-31`,
      { user: 'tr' },
    );
    await findTable();
    const params = () => new URLSearchParams(router.state.location.search);

    await user.click(clearOf('Status'));
    await waitFor(() => expect(params().has('status')).toBe(false));
    expect(params().get('projectId')).toBe(NETWORK);
    expect(params().get('from')).toBe('2026-10-01');
    expect(shown('Status')).toContain('All statuses');

    await user.click(clearOf('Project'));
    await waitFor(() => expect(params().has('projectId')).toBe(false));
    expect(params().get('from')).toBe('2026-10-01');
    expect(params().get('to')).toBe('2026-10-31');

    const picker = rangeInputs().start.closest('.ant-picker')?.querySelector('.ant-picker-clear');
    if (!picker) throw new Error('no clear icon on the Date range');
    // antd keeps the clear icon at pointer-events: none until the picker is hovered or focused.
    const pickerRoot = picker.closest('.ant-picker') as HTMLElement;
    await user.hover(pickerRoot);
    await user.click(rangeInputs().start);
    await waitFor(() => expect(pickerRoot).toHaveClass('ant-picker-focused'));
    await user.click(picker);
    await waitFor(() => expect(router.state.location.search).toBe(''));
    await waitFor(() => expect(keys()).toHaveLength(20));
    expect(keys()[0]).toBe('SUP-100');
  });

  it('AC-003.22 a filter change resets to page 1 with history replace', async () => {
    const user = userEvent.setup();
    const spy = solutionsRequests();
    const { router } = renderApp('/profile?page=2', { user: 'tr' });
    await findTable();
    await waitFor(() => expect(keys()[0]).toBe('SUP-120'));
    await pick(user, 'Status', 'Published');
    await waitFor(() => expect(router.state.location.search).toBe('?status=PUBLISHED'));
    expect(router.state.historyAction).toBe('REPLACE');
    await waitFor(() => expect(lastParams(spy).get('page')).toBe('0'));
    await waitFor(() => expect(keys()[0]).toBe('SUP-102'));
  });

  it('AC-003.22 a shared URL fills the filters and the table', async () => {
    const spy = solutionsRequests();
    renderApp(`/profile?status=EXPIRED&projectId=${NETWORK}&from=2026-10-01&to=2026-10-31`, {
      user: 'tr',
    });
    await findTable();
    await waitFor(() => expect(shown('Status')).toContain('Expired'));
    await waitFor(() => expect(shown('Project')).toContain('Network operations'));
    const { start, end } = rangeInputs();
    expect(start.value).toBe('01/10/2026');
    expect(end.value).toBe('31/10/2026');
    const first = new URLSearchParams(spy.queries[0]);
    expect(first.get('status')).toBe('EXPIRED');
    expect(first.get('projectId')).toBe(NETWORK);
    expect(first.get('from')).toBe('2026-10-01');
    expect(first.get('to')).toBe('2026-10-31');
    await waitFor(() => expect(keys()).toHaveLength(4));
  });

  it.each([
    ['status=FOO', 'status'],
    ['status=expired', 'status'],
    ['projectId=not-a-uuid', 'projectId'],
    ['from=2026-02-30', 'from'],
    ['to=31/10/2026', 'to'],
    ['from=2026-10-31&to=2026-10-01', 'from'],
  ])('edge 85 drops an invalid %s in the URL silently', async (search, param) => {
    const spy = solutionsRequests();
    const { router } = renderApp(`/profile?${search}`, { user: 'tr' });
    await findTable();
    await waitFor(() => expect(keys()[0]).toBe('SUP-100'));
    expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
    const names = search.split('&').map((p) => p.split('=')[0] ?? '');
    for (const query of spy.queries) {
      const sent = new URLSearchParams(query);
      for (const name of names) expect(sent.has(name)).toBe(false);
    }
    expect(shown('Status')).toContain('All statuses');
    expect(shown('Project')).toContain('All Projects');
    const { start, end } = rangeInputs();
    expect(start.value).toBe('');
    expect(end.value).toBe('');
    // The dropped values are also removed from the URL with history replace (Q6).
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).has(param)).toBe(false),
    );
    expect(router.state.location.search).toBe('');
    expect(router.state.historyAction).toBe('REPLACE');
  });

  it('edge 87 the last filter picked wins while the table loads', async () => {
    const user = userEvent.setup();
    server.use(
      http.get('*/api/v1/me/solutions', async ({ request }) => {
        const q = new URL(request.url).searchParams;
        const status = q.get('status');
        await delay(status === 'EXPIRED' ? 600 : 150);
        const rows = TR.filter((r) => !status || r.status === status);
        return HttpResponse.json({
          content: rows.slice(0, 20),
          page: { size: 20, number: 0, totalElements: rows.length, totalPages: 1 },
        });
      }),
    );
    const { router } = renderApp('/profile', { user: 'tr' });
    await findTable();
    await pick(user, 'Status', 'Expired');
    expect(screen.getByRole('table', { name: 'My Solutions' })).toBeInTheDocument();
    expect(screen.queryByText(/Loading/)).not.toBeInTheDocument();
    await pick(user, 'Status', 'Published');
    await waitFor(() => expect(keys()[0]).toBe('SUP-102'), { timeout: 5000 });
    await delay(800);
    expect(keys()[0]).toBe('SUP-102');
    expect(keys()).toHaveLength(8);
    expect(router.state.location.search).toBe('?status=PUBLISHED');
  });

  it('edge 88 while the summary fails the Project filter shows only "All Projects" and the other filters still work', async () => {
    const user = userEvent.setup();
    server.use(
      http.get('*/api/v1/me/solutions/summary', () => HttpResponse.json({}, { status: 500 })),
    );
    renderApp('/profile', { user: 'tr' });
    await findTable();
    expect(
      (await screen.findAllByText(msg('MSG06'), {}, { timeout: 5000 })).length,
    ).toBeGreaterThan(0);
    expect(await optionsOf(user, 'Project')).toEqual(['All Projects']);
    await pick(user, 'Status', 'Expired');
    await waitFor(() => expect(keys()).toHaveLength(7));
  });
});
