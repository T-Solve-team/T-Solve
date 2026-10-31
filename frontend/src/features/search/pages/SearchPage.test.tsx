import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http } from 'msw';
import { server } from '@/mocks/server';
import type { TicketSearchResult } from '@/shared/api';
import { getSearchTicketsMockHandler } from '@/shared/api/generated/search/search.msw';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

type Options = Parameters<typeof renderApp>[1];

/** The fixture users' Departments (src/mocks/session.ts). */
const IT_ID = '00000000-0000-4000-8000-0000000000d1';
const HR_ID = '00000000-0000-4000-8000-0000000000d2';

/** The Search page with no query; resolves once the Search box is shown. */
async function openSearch(path = '/search', options: Options = {}) {
  const user = userEvent.setup();
  const view = renderApp(path, options);
  // The first render also loads the lazy page module (BUG-004: 5 s for the first findBy, guideline 10 §6).
  await screen.findByRole('searchbox', { name: 'Search' }, { timeout: 5000 });
  return { user, ...view };
}

/** The Search page with a query in the URL; resolves once the results are shown. */
async function openResults(path: string, options: Options = {}) {
  const view = await openSearch(path, options);
  await screen.findByText(/^Total \d+ items$/, {}, { timeout: 5000 });
  return view;
}

function results(): HTMLElement {
  return screen.getByRole('region', { name: 'Results' });
}

function cardTitles(): string[] {
  return within(results())
    .queryAllByRole('article')
    .map((card) => within(card).getByRole('link').textContent);
}

function cardOf(title: string): HTMLElement {
  const card = screen.getByRole('link', { name: title }).closest('article');
  if (!card) throw new Error(`no card for ${title}`);
  return card;
}

/** The query string of every GET /api/v1/search/tickets; the request still reaches the mock handler. */
function recordRequests(): URLSearchParams[] {
  const requests: URLSearchParams[] = [];
  server.use(
    http.get('*/api/v1/search/tickets', ({ request }) => {
      requests.push(new URL(request.url).searchParams);
    }),
  );
  return requests;
}

/** An option of the open Select dropdown (the selected value carries the same title). */
function findOption(title: string): Promise<HTMLElement> {
  return waitFor(() => {
    const option = screen
      .getAllByTitle(title)
      .find((el) => el.classList.contains('ant-select-item-option'));
    if (!option) throw new Error(`no option ${title}`);
    return option;
  });
}

async function pick(user: ReturnType<typeof userEvent.setup>, label: string, option: string) {
  await user.click(screen.getByRole('combobox', { name: label }));
  await user.click(await findOption(option));
}

/** The value a Select filter shows (the text of the Select; its search input is empty). */
function selected(label: string): string | null | undefined {
  return screen.getByRole('combobox', { name: label }).closest('.ant-select')?.textContent;
}

function resolvedInputs(): HTMLInputElement[] {
  const start = screen.getByLabelText('Resolved date');
  const picker = start.closest('.ant-picker');
  if (!picker) throw new Error('no Resolved date picker');
  return Array.from(picker.querySelectorAll('input'));
}

function onePage(content: TicketSearchResult[], totalElements = content.length) {
  return getSearchTicketsMockHandler({
    content,
    page: { size: 20, number: 0, totalElements, totalPages: Math.ceil(totalElements / 20) },
  });
}

const RESULT: TicketSearchResult = {
  ticketId: '5ea70000-0000-4000-8000-000000000001',
  title: 'VPN disconnects every 10 minutes on home Wi-Fi',
  project: { projectId: '00000000-0000-4000-8000-000000000101', name: 'ITSUP' },
  department: { departmentId: IT_ID, name: 'IT' },
  excerpt: 'Renew the VPN client certificate.',
  helpfulRate: 92,
  resolvedAt: '2026-10-02T04:24:00Z',
};

describe('SearchPage', () => {
  beforeAll(async () => {
    await import('./SearchPage');
  });

  it('AC-002.1 shows the Search box and the filters above the results area to a Ticket Resolver', async () => {
    await openSearch('/search', { role: 'TICKET_RESOLVER' });
    expect(screen.getByRole('heading', { name: 'Search' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Department' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Source' })).toBeInTheDocument();
    expect(screen.getByLabelText('Resolved date')).toBeInTheDocument();
    expect(results()).toBeInTheDocument();
  });

  it('AC-002.1 shows the Search to a Project Manager', async () => {
    await openSearch();
    expect(screen.getByRole('heading', { name: 'Search' })).toBeInTheDocument();
  });

  it('AC-002.2 calls nothing and shows an empty results area with no query, or one of white space', async () => {
    const requests = recordRequests();
    const { user } = await openSearch('/search?q=%20%20');
    expect(results()).toBeEmptyDOMElement();
    // A search afterwards is the first request: the white-space query sent none.
    await user.type(screen.getByRole('searchbox', { name: 'Search' }), 'vpn{Enter}');
    await waitFor(() => expect(cardTitles()).toHaveLength(3));
    expect(requests.map((r) => r.toString())).toEqual(['q=vpn&page=0&size=20']);
  });

  it('AC-002.2 empties the results area when the query is cleared with the clear icon', async () => {
    const { user, router } = await openResults('/search?q=vpn');
    const clear = screen
      .getByRole('searchbox', { name: 'Search' })
      .closest('.ant-input-affix-wrapper')
      ?.querySelector('.ant-input-clear-icon');
    if (!clear) throw new Error('no clear icon');
    await user.click(clear);
    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(results()).toBeEmptyDOMElement();
    expect(screen.getByRole('searchbox', { name: 'Search' })).toHaveValue('');
  });

  it('AC-002.2 calls nothing for a link with filters but no query, and the filters show their values', async () => {
    const requests = recordRequests();
    const { user } = await openSearch(`/search?departmentId=${IT_ID}&source=CSV`);
    expect(results()).toBeEmptyDOMElement();
    expect(selected('Department')).toBe('IT');
    expect(selected('Source')).toBe('CSV');
    // A search afterwards is the first request, with the filters of the link.
    await user.type(screen.getByRole('searchbox', { name: 'Search' }), 'onedrive{Enter}');
    await waitFor(() =>
      expect(cardTitles()).toEqual(['OneDrive sync stuck at processing changes']),
    );
    expect(requests.map((r) => r.toString())).toEqual([
      `q=onedrive&departmentId=${IT_ID}&source=CSV&page=0&size=20`,
    ]);
  });

  it('AC-002.3 searches the trimmed text on Enter with page 0, size 20 and no filter or sort', async () => {
    const requests = recordRequests();
    const { user, router } = await openSearch();
    const box = screen.getByRole('searchbox', { name: 'Search' });
    await user.type(box, '  vpn ');
    expect(router.state.location.search).toBe(''); // typing alone does not search

    await user.keyboard('{Enter}');
    await waitFor(() => expect(router.state.location.search).toBe('?q=vpn'));
    await waitFor(() => expect(cardTitles()).toHaveLength(3));
    // One request, from Enter, with no filter and no sort.
    expect(requests.map((r) => r.toString())).toEqual(['q=vpn&page=0&size=20']);
  });

  it('AC-002.3 does nothing on Enter with an empty or white-space text', async () => {
    const requests = recordRequests();
    const { user, router } = await openResults('/search?q=vpn');
    const box = screen.getByRole('searchbox', { name: 'Search' });
    await user.clear(box);
    await user.type(box, '   {Enter}');
    // A filter change afterwards shows the query still applied and no request in between.
    await pick(user, 'Source', 'Jira import');
    await waitFor(() => expect(cardTitles()).toHaveLength(2));
    expect(router.state.location.search).toBe('?q=vpn&source=JIRA_IMPORT');
    expect(requests.map((r) => r.toString())).toEqual([
      'q=vpn&page=0&size=20',
      'q=vpn&source=JIRA_IMPORT&page=0&size=20',
    ]);
  });

  it('AC-002.3 keeps the focus in the Search box after a search (BUG-059)', async () => {
    const { user } = await openSearch();
    await user.type(screen.getByRole('searchbox', { name: 'Search' }), 'vpn{Enter}');
    await waitFor(() => expect(cardTitles()).toHaveLength(3));
    expect(screen.getByRole('searchbox', { name: 'Search' })).toHaveFocus();
  });

  it('AC-002.4 accepts at most 200 characters in the Search box', async () => {
    const { user } = await openSearch();
    const box = screen.getByRole('searchbox', { name: 'Search' });
    await user.click(box);
    await user.paste('x'.repeat(220));
    expect(box).toHaveValue('x'.repeat(200));
  });

  it('AC-002.5 offers my Departments and sends the picked one by its id; All Departments removes it', async () => {
    const requests = recordRequests();
    const { user, router } = await openResults('/search?q=e', { user: 'tr-two-departments' });
    await user.click(screen.getByRole('combobox', { name: 'Department' }));
    expect(await findOption('All Departments')).toBeInTheDocument();
    expect(await findOption('IT')).toBeInTheDocument();
    await user.click(await findOption('HR'));
    await waitFor(() => expect(router.state.location.search).toBe(`?q=e&departmentId=${HR_ID}`));
    await waitFor(() => expect(screen.getByText('Total 5 items')).toBeInTheDocument());
    expect(requests.at(-1)?.get('departmentId')).toBe(HR_ID);

    await pick(user, 'Department', 'All Departments');
    await waitFor(() => expect(router.state.location.search).toBe('?q=e'));
    await waitFor(() => expect(screen.getByText('Total 14 items')).toBeInTheDocument());
  });

  it('AC-002.5 offers the four sources, sends the picked one, and All removes it', async () => {
    const requests = recordRequests();
    const { user, router } = await openResults('/search?q=vpn');
    await user.click(screen.getByRole('combobox', { name: 'Source' }));
    for (const label of ['All', 'Solution Form', 'Jira import', 'CSV', 'Manual']) {
      expect(await findOption(label)).toBeInTheDocument();
    }
    await user.click(await findOption('Jira import'));
    await waitFor(() => expect(router.state.location.search).toBe('?q=vpn&source=JIRA_IMPORT'));
    await waitFor(() => expect(cardTitles()).toHaveLength(2));
    expect(requests.at(-1)?.get('source')).toBe('JIRA_IMPORT');

    await pick(user, 'Source', 'All');
    await waitFor(() => expect(router.state.location.search).toBe('?q=vpn'));
    await waitFor(() => expect(cardTitles()).toHaveLength(3));
  });

  it('AC-002.5 sends a Resolved date range as two days, and clearing it removes both', async () => {
    const requests = recordRequests();
    const { user, router } = await openResults('/search?q=vpn');
    const [start, end] = resolvedInputs();
    if (!start || !end) throw new Error('no Resolved date inputs');
    await user.click(start);
    await user.keyboard('15/09/2026{Enter}');
    await user.click(end);
    await user.keyboard('29/09/2026{Enter}');
    await waitFor(() =>
      expect(router.state.location.search).toBe(
        '?q=vpn&resolvedFrom=2026-09-15&resolvedTo=2026-09-29',
      ),
    );
    await waitFor(() => expect(cardTitles()).toHaveLength(2));
    expect(requests.at(-1)?.get('resolvedFrom')).toBe('2026-09-15');
    expect(requests.at(-1)?.get('resolvedTo')).toBe('2026-09-29');

    const clear = start.closest('.ant-picker')?.querySelector('.ant-picker-clear');
    if (!clear) throw new Error('no clear icon');
    await user.click(clear);
    await waitFor(() => expect(router.state.location.search).toBe('?q=vpn'));
    await waitFor(() => expect(cardTitles()).toHaveLength(3));
  });

  it('AC-002.5 sends no Resolved date when only the first day is picked', async () => {
    const requests = recordRequests();
    const { user, router } = await openResults('/search?q=vpn');
    const [start] = resolvedInputs();
    if (!start) throw new Error('no Resolved date input');
    await user.click(start);
    await user.keyboard('15/09/2026{Enter}');
    await user.keyboard('{Escape}');
    // A filter change afterwards carries no Resolved date: the half range was never applied.
    await pick(user, 'Source', 'Jira import');
    await waitFor(() => expect(cardTitles()).toHaveLength(2));
    expect(router.state.location.search).toBe('?q=vpn&source=JIRA_IMPORT');
    expect(requests.map((r) => r.toString())).toEqual([
      'q=vpn&page=0&size=20',
      'q=vpn&source=JIRA_IMPORT&page=0&size=20',
    ]);
  });

  it('AC-002.6 searches again from page 1 when a filter changes', async () => {
    server.use(onePage([RESULT], 25));
    const requests = recordRequests();
    const { user, router } = await openResults('/search?q=vpn&page=2');
    await pick(user, 'Source', 'CSV');
    await waitFor(() => expect(router.state.location.search).toBe('?q=vpn&source=CSV'));
    await waitFor(() => expect(requests.at(-1)?.get('page')).toBe('0'));
    await waitFor(() => expect(cardTitles()).toHaveLength(1));
  });

  it('AC-002.6 only keeps a filter in the URL while there is no query', async () => {
    const requests = recordRequests();
    const { user, router } = await openSearch();
    await pick(user, 'Source', 'CSV');
    await waitFor(() => expect(router.state.location.search).toBe('?source=CSV'));
    expect(results()).toBeEmptyDOMElement();
    // A search afterwards is the first request, already with the kept filter.
    await user.type(screen.getByRole('searchbox', { name: 'Search' }), 'onedrive{Enter}');
    await waitFor(() =>
      expect(cardTitles()).toEqual(['OneDrive sync stuck at processing changes']),
    );
    expect(requests.map((r) => r.toString())).toEqual(['q=onedrive&source=CSV&page=0&size=20']);
  });

  it('AC-002.7 shows each result as a card, in the order of the API', async () => {
    await openResults('/search?q=vpn');
    expect(cardTitles()).toEqual([
      'VPN disconnects every 10 minutes on home Wi-Fi',
      'VPN cannot connect after the Windows update',
      'Remote desktop over VPN is very slow',
    ]);
    const card = cardOf('VPN disconnects every 10 minutes on home Wi-Fi');
    expect(within(card).getByRole('link')).toHaveAttribute(
      'href',
      '/tickets/5ea70000-0000-4000-8000-000000000001',
    );
    expect(card).toHaveTextContent('ITSUP · IT · Resolved 02/10/2026 11:24');
    expect(within(card).getByText('92% helpful')).toBeInTheDocument();
    expect(card.querySelector('.anticon-like')).toBeInTheDocument();
    expect(card).toHaveTextContent(
      'The VPN client drops the tunnel every 10 minutes on home Wi-Fi. Renew the VPN client certificate',
    );
  });

  it('AC-002.7 shows "0% helpful" for a rate of 0', async () => {
    await openResults('/search?q=vpn');
    expect(
      within(cardOf('Remote desktop over VPN is very slow')).getByText('0% helpful'),
    ).toBeInTheDocument();
  });

  it('AC-002.8 shows no helpful rate on a result with no Feedback yet', async () => {
    await openResults('/search?q=vpn');
    const card = cardOf('VPN cannot connect after the Windows update');
    expect(card).not.toHaveTextContent('helpful');
    expect(card.querySelector('.anticon-like')).toBeNull();
  });

  it('AC-002.9 shows the total and loads another page with the same query and filters', async () => {
    server.use(onePage([RESULT], 25));
    const requests = recordRequests();
    const { user, router } = await openResults(`/search?q=vpn&departmentId=${IT_ID}`);
    expect(screen.getByText('Total 25 items')).toBeInTheDocument();
    await user.click(screen.getByTitle('2'));
    await waitFor(() =>
      expect(router.state.location.search).toBe(`?q=vpn&departmentId=${IT_ID}&page=2`),
    );
    await waitFor(() => expect(requests.at(-1)?.get('page')).toBe('1'));
    expect(requests.at(-1)?.get('q')).toBe('vpn');
    expect(requests.at(-1)?.get('departmentId')).toBe(IT_ID);
    expect(requests.at(-1)?.get('size')).toBe('20');
    await waitFor(() => expect(cardTitles()).toHaveLength(1));
  });

  it('AC-002.10 runs the search of a shared link, with its values in the Search box and the filters', async () => {
    const requests = recordRequests();
    await openResults(
      `/search?q=vpn&departmentId=${IT_ID}&source=JIRA_IMPORT&resolvedFrom=2026-09-15&resolvedTo=2026-09-29`,
    );
    expect(cardTitles()).toHaveLength(2);
    expect(screen.getByRole('searchbox', { name: 'Search' })).toHaveValue('vpn');
    expect(selected('Department')).toBe('IT');
    expect(selected('Source')).toBe('Jira import');
    expect(resolvedInputs().map((input) => input.value)).toEqual(['15/09/2026', '29/09/2026']);
    const first = requests[0];
    expect(first?.get('departmentId')).toBe(IT_ID);
    expect(first?.get('resolvedFrom')).toBe('2026-09-15');
    expect(first?.get('resolvedTo')).toBe('2026-09-29');
  });

  it('AC-002.10 ignores a Source or a Resolved date that is not valid', async () => {
    const requests = recordRequests();
    await openResults('/search?q=vpn&source=FAX&resolvedFrom=2026-02-31&resolvedTo=soon');
    for (const name of ['source', 'resolvedFrom', 'resolvedTo']) {
      expect(requests[0]?.has(name)).toBe(false);
    }
    expect(selected('Source')).toBe('All');
  });

  it('AC-002.10 caps a query over 200 characters and ignores an empty Department or a reversed range', async () => {
    const requests = recordRequests();
    const long = 'vpn'.padEnd(250, 'x');
    await openSearch(
      `/search?q=${long}&departmentId=&resolvedFrom=2026-09-29&resolvedTo=2026-09-15`,
    );
    expect(
      await within(results()).findByText(msg('MSG04'), {}, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(requests.map((r) => r.toString())).toEqual([`q=${long.slice(0, 200)}&page=0&size=20`]);
    expect(screen.getByRole('searchbox', { name: 'Search' })).toHaveValue(long.slice(0, 200));
  });

  it('AC-002.10 resets the Search box when going back in history', async () => {
    const { user, router } = await openSearch();
    await user.type(screen.getByRole('searchbox', { name: 'Search' }), 'vpn{Enter}');
    await waitFor(() => expect(cardTitles()).toHaveLength(3));
    await act(() => router.navigate(-1));
    await waitFor(() => expect(screen.getByRole('searchbox', { name: 'Search' })).toHaveValue(''));
    expect(results()).toBeEmptyDOMElement();
  });

  it('AC-002.11 shows the loading state while a search loads', async () => {
    server.use(
      http.get('*/api/v1/search/tickets', async () => {
        await delay('infinite');
      }),
    );
    await openSearch('/search?q=vpn');
    expect(await within(results()).findByText('Loading ...')).toBeInTheDocument();
  });

  it('AC-002.12 shows MSG04 when nothing is found', async () => {
    const { user } = await openSearch();
    await user.type(screen.getByRole('searchbox', { name: 'Search' }), 'zzz{Enter}');
    expect(await within(results()).findByText(msg('MSG04'))).toBeInTheDocument();
  });

  it('AC-002.13 shows the error with Try again, which runs the same search again', async () => {
    server.use(onePage([RESULT], 25));
    let failures = 1;
    server.use(
      http.get('*/api/v1/search/tickets', () => {
        if (failures-- > 0) return problem(500);
      }),
    );
    const requests = recordRequests(); // added last, so it sees the failed request too
    const { user } = await openSearch(`/search?q=vpn&source=JIRA_IMPORT&page=2`);
    expect(await within(results()).findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(
      msg('MSG06'),
    );
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    await waitFor(() => expect(cardTitles()).toHaveLength(1));
    expect(requests.map((r) => r.toString())).toEqual([
      'q=vpn&source=JIRA_IMPORT&page=1&size=20',
      'q=vpn&source=JIRA_IMPORT&page=1&size=20',
    ]);
  });

  it('AC-002.14 shows a title and an excerpt with HTML as plain text', async () => {
    const title = '<img src=x onerror=alert(1)>Printer <b>down</b>';
    const excerpt = '<script>alert(1)</script>Restart the <i>spooler</i>.';
    server.use(onePage([{ ...RESULT, title, excerpt }]));
    await openResults('/search?q=printer');
    expect(screen.getByText(title)).toBeInTheDocument();
    expect(screen.getByText(excerpt)).toBeInTheDocument();
    expect(results().querySelector('img, b, i, script')).toBeNull();
  });
});
