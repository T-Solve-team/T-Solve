import type { BoundFunctions, queries } from '@testing-library/dom';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
import { MY_SOLUTIONS } from '../mocks/data';

const SUMMARY_URL = /\/api\/v1\/me\/solutions\/summary$/;
const SOLUTIONS_URL = /\/api\/v1\/me\/solutions$/;

/** The frame's order (decision Q2), not the TicketStatus enum order. */
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

/** Counts requests to the summary and to the table while the real handlers keep answering. Call before `renderApp`. */
function requests() {
  const spy = { summary: 0, solutions: 0 };
  server.events.on('request:start', ({ request }) => {
    if (request.method !== 'GET') return;
    const path = new URL(request.url).pathname;
    if (SUMMARY_URL.test(path)) spy.summary += 1;
    if (SOLUTIONS_URL.test(path)) spy.solutions += 1;
  });
  return spy;
}

/** The counters: a group named "Solution counts" (the developer adds it, decision Q1). */
async function findCounters() {
  return within(await screen.findByRole('group', { name: 'Solution counts' }, { timeout: 5000 }));
}

/** The value shown with a counter label: the text of its nearest ancestor that holds a number. */
function valueOf(counters: BoundFunctions<typeof queries>, label: string) {
  let node: HTMLElement | null = counters.getByText(label, { exact: true });
  while (node && !/\d/.test(node.textContent ?? '')) node = node.parentElement;
  if (!node) throw new Error(`no value next to ${label}`);
  return (node.textContent ?? '').replace(label, '').trim();
}

async function findTable() {
  return within(await screen.findByRole('table', { name: 'My Solutions' }, { timeout: 5000 }));
}

describe('StatusCounters', () => {
  afterEach(() => {
    server.events.removeAllListeners();
  });

  it("AC-003.21 shows six counters with the Status Tag labels and tr's counts, in the frame's order", async () => {
    renderApp('/profile', { user: 'tr' });
    const group = await screen.findByRole('group', { name: 'Solution counts' }, { timeout: 5000 });
    const counters = within(group);
    for (const [status, label] of COUNTERS) {
      await waitFor(() => expect(valueOf(counters, label)).toBe(String(trCount(status))));
      expect(trCount(status)).toBeGreaterThan(0);
    }
    const text = group.textContent ?? '';
    const positions = COUNTERS.map(([, label]) => text.indexOf(label));
    expect(positions.every((p) => p >= 0)).toBe(true);
    expect(positions).toEqual([...positions].sort((a, b) => a - b));
  });

  it('AC-003.21 shows six 0 counters for a resolver with no Solutions', async () => {
    renderApp('/profile', { user: 'tr-two-departments' });
    const counters = await findCounters();
    for (const [, label] of COUNTERS) {
      await waitFor(() => expect(valueOf(counters, label)).toBe('0'));
    }
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('AC-003.21 shows a spinner in the counters at once while they load', async () => {
    server.use(http.get('*/api/v1/me/solutions/summary', () => delay('infinite')));
    renderApp('/profile', { user: 'tr' });
    const counters = await findCounters();
    expect(counters.getByText(/Loading/)).toBeInTheDocument();
    for (const [, label] of COUNTERS) expect(counters.queryByText(label)).not.toBeInTheDocument();
    await findTable();
  });

  it('AC-003.21 the counters are not clickable', async () => {
    const user = userEvent.setup();
    const spy = requests();
    const { router } = renderApp('/profile', { user: 'tr' });
    const group = await screen.findByRole('group', { name: 'Solution counts' }, { timeout: 5000 });
    const counters = within(group);
    await waitFor(() => expect(valueOf(counters, 'Published')).toBe(String(trCount('PUBLISHED'))));
    await findTable();
    expect(counters.queryAllByRole('button')).toHaveLength(0);
    expect(counters.queryAllByRole('link')).toHaveLength(0);
    expect(group.querySelectorAll('[tabindex]')).toHaveLength(0);
    const before = { ...spy };
    const url = router.state.location.search;
    await user.click(counters.getByText('Rejected'));
    expect(router.state.location.search).toBe(url);
    expect(spy).toEqual(before);
  });

  it('AC-003.21 loads the summary once per visit and not again on paging', async () => {
    const user = userEvent.setup();
    const spy = requests();
    const { router } = renderApp('/profile', { user: 'tr' });
    const counters = await findCounters();
    await waitFor(() => expect(valueOf(counters, 'Published')).toBe(String(trCount('PUBLISHED'))));
    await findTable();
    expect(spy.summary).toBe(1);
    const solutionsBefore = spy.solutions;
    const card = within(await screen.findByRole('region', { name: 'My Solutions' }));
    await user.click(card.getByTitle('2'));
    await waitFor(() => expect(router.state.location.search).toBe('?page=2'));
    await waitFor(() => expect(spy.solutions).toBeGreaterThan(solutionsBefore));
    expect(spy.summary).toBe(1);
  });

  it('AC-003.14 counters: a failing summary shows MSG06 in the counters only and the table stays', async () => {
    const spy = requests();
    server.use(http.get('*/api/v1/me/solutions/summary', () => problem(500)));
    renderApp('/profile', { user: 'tr' });
    const counters = await findCounters();
    expect(await counters.findByText(msg('MSG06'), {}, { timeout: 5000 })).toBeInTheDocument();
    for (const [, label] of COUNTERS) expect(counters.queryByText(label)).not.toBeInTheDocument();
    const table = await findTable();
    expect(table.getAllByRole('row').slice(1)).toHaveLength(20);
    await delay(300);
    expect(spy.summary).toBe(1);
  });

  it('AC-003.14 counters: a 403 on the summary shows MSG08 in the counters while the table stays', async () => {
    server.use(http.get('*/api/v1/me/solutions/summary', () => problem(403, 'MSG08')));
    renderApp('/profile', { user: 'tr' });
    const counters = await findCounters();
    expect(await counters.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
    for (const [, label] of COUNTERS) expect(counters.queryByText(label)).not.toBeInTheDocument();
    const table = await findTable();
    expect(table.getAllByRole('row').slice(1)).toHaveLength(20);
  });
});
