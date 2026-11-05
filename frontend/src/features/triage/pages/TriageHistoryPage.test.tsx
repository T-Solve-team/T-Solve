import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { SuggestionResultPage } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

const ITSUP_ID = '00000000-0000-4000-8000-000000000101';
const VPN_SUGGESTION = 'Check the VPN configuration, then retry the connection.';
const SCRIPT_SUGGESTION = '<script>alert(1)</script> Follow the documented VPN reset steps.';

const emptyPage: SuggestionResultPage = {
  content: [],
  page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
};

async function issueRow(issueKey: string): Promise<HTMLTableRowElement> {
  const row = (await screen.findByText(issueKey, {}, { timeout: 5000 })).closest('tr');
  if (!row) throw new Error(`Missing table row for ${issueKey}`);
  return row;
}

describe('TriageHistoryPage', () => {
  beforeAll(async () => {
    await import('./TriageHistoryPage');
  });

  afterEach(() => vi.unstubAllEnvs());

  it('AC-001.1 shows the history columns in newest-first order', async () => {
    renderApp('/triage-history');
    const table = await screen.findByRole(
      'table',
      { name: 'Suggestion history' },
      { timeout: 5000 },
    );
    for (const column of ['Issue', 'Sources', 'Suggested solution', "Reporter's answer"]) {
      expect(within(table).getByRole('columnheader', { name: column })).toBeInTheDocument();
    }
    expect(within(table).getAllByRole('row')[1]).toHaveTextContent('ITNET-2002');
  });

  it('AC-001.1 renders 10.1 instead of the not-built placeholder (BUG-121)', async () => {
    renderApp('/triage-history');
    await screen.findByRole('table', { name: 'Suggestion history' }, { timeout: 5000 });
    expect(screen.queryByText(/not built yet/)).not.toBeInTheDocument();
  });

  it('AC-001.2 requests the default page and sort without filters', async () => {
    let requested: URL | undefined;
    server.use(
      http.get('*/api/v1/suggestion-results', ({ request }) => {
        requested = new URL(request.url);
        return HttpResponse.json(emptyPage);
      }),
    );
    renderApp('/triage-history');
    await screen.findByText(msg('MSG04'), {}, { timeout: 5000 });
    if (!requested) throw new Error('History request was not made');
    expect([...requested.searchParams.entries()].sort()).toEqual([
      ['page', '0'],
      ['size', '20'],
      ['sort', 'createdAt,desc'],
    ]);
  });

  it('AC-001.3 offers managed Projects and all answer choices', async () => {
    const user = userEvent.setup();
    renderApp('/triage-history', { user: 'pm-km' });
    await screen.findByRole('table', { name: 'Suggestion history' }, { timeout: 5000 });
    await user.click(screen.getByRole('combobox', { name: 'Project' }));
    expect(await screen.findByRole('option', { name: 'HRHELP' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'ITSUP' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('combobox', { name: 'Answer' }));
    for (const choice of ['All answers', 'Solved', 'Not solved', 'No answer']) {
      expect(await screen.findByRole('option', { name: choice })).toBeInTheDocument();
    }
  });

  it('AC-001.4 combines URL filters, resets the page, and restores browser Back', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/triage-history?page=2&answer=YES');
    await screen.findByRole('table', { name: 'Suggestion history' }, { timeout: 5000 });
    await user.click(screen.getByRole('combobox', { name: 'Project' }));
    await user.click(await screen.findByRole('option', { name: 'ITSUP' }));
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).has('page')).toBe(false),
    );
    expect(router.state.location.search).toContain('answer=YES');
    expect(router.state.location.search).toContain('projectId=');
    await act(() => router.navigate(-1));
    expect(new URLSearchParams(router.state.location.search).get('page')).toBe('2');
  });

  it('AC-001.3 sends the picked calendar days and clears both dates from the URL and request', async () => {
    // Midnight in UTC+7 is the previous UTC day: ISO conversion would shift these dates.
    vi.stubEnv('TZ', 'Asia/Ho_Chi_Minh');
    expect(new Date(2026, 10, 1).getTimezoneOffset()).toBe(-420);
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get('*/api/v1/suggestion-results', ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(emptyPage);
      }),
    );
    const { router } = renderApp('/triage-history?page=2&from=2026-11-05&to=2026-11-06');
    await screen.findByText(msg('MSG04'), {}, { timeout: 5000 });
    await user.click(screen.getByLabelText('Date range'));
    await user.click(await screen.findByTitle('2026-11-01'));
    await user.click(await screen.findByTitle('2026-11-03'));

    await waitFor(() => expect(requests.at(-1)?.searchParams.get('to')).toBe('2026-11-03'));
    const picked = new URLSearchParams(router.state.location.search);
    expect(picked.get('from')).toBe('2026-11-01');
    expect(picked.get('to')).toBe('2026-11-03');
    expect(picked.has('page')).toBe(false);
    expect(requests.at(-1)?.searchParams.get('from')).toBe('2026-11-01');
    expect(screen.getByLabelText('Date range')).toHaveValue('01/11/2026');
    expect(screen.getByLabelText('To date')).toHaveValue('03/11/2026');

    const beforeClear = requests.length;
    await user.click(screen.getByRole('button', { name: 'Clear date range' }));
    await waitFor(() => expect(requests).toHaveLength(beforeClear + 1));
    const cleared = new URLSearchParams(router.state.location.search);
    expect(cleared.has('from')).toBe(false);
    expect(cleared.has('to')).toBe(false);
    expect(requests.at(-1)?.searchParams.has('from')).toBe(false);
    expect(requests.at(-1)?.searchParams.has('to')).toBe(false);
    expect(screen.getByLabelText('Date range')).toHaveValue('');
    expect(screen.getByLabelText('To date')).toHaveValue('');
  });

  it('AC-001.4 sends Project and answer selections together in the list request', async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get('*/api/v1/suggestion-results', ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(emptyPage);
      }),
    );
    const { router } = renderApp('/triage-history?page=2');
    await screen.findByText(msg('MSG04'), {}, { timeout: 5000 });
    await user.click(screen.getByRole('combobox', { name: 'Project' }));
    await user.click(await screen.findByRole('option', { name: 'ITSUP' }));
    await user.click(screen.getByRole('combobox', { name: 'Answer' }));
    await user.click(await screen.findByRole('option', { name: 'Not solved' }));
    await waitFor(() => expect(requests.at(-1)?.searchParams.get('answer')).toBe('NO'));
    expect(requests.at(-1)?.searchParams.get('projectId')).toBe(ITSUP_ID);
    expect(requests.at(-1)?.searchParams.get('page')).toBe('0');
    const params = new URLSearchParams(router.state.location.search);
    expect(params.get('projectId')).toBe(ITSUP_ID);
    expect(params.get('answer')).toBe('NO');
    expect(params.has('page')).toBe(false);
  });

  it.each([
    ['answer=STALE', 'STALE', 'All managed Projects', 'All answers'],
    [
      'projectId=00000000-0000-4000-8000-000000000201',
      '00000000-0000-4000-8000-000000000201',
      'All managed Projects',
      'All answers',
    ],
    [`projectId=${ITSUP_ID}&answer=STALE`, 'STALE', 'ITSUP', 'All answers'],
    ['projectId=stale-project&answer=NO', 'stale-project', 'All managed Projects', 'Not solved'],
    [
      'projectId=stale-project&answer=STALE',
      'stale-project',
      'All managed Projects',
      'All answers',
    ],
  ])(
    'AC-001.4 replaces stale filters before requesting history: %s',
    async (filters, stale, projectLabel, answerLabel) => {
      const requests: URL[] = [];
      server.use(
        http.get('*/api/v1/suggestion-results', ({ request }) => {
          requests.push(new URL(request.url));
          return HttpResponse.json(emptyPage);
        }),
      );
      const { router } = renderApp(`/triage-history?page=3&${filters}`);
      await screen.findByText(msg('MSG04'), {}, { timeout: 5000 });
      const params = new URLSearchParams(router.state.location.search);
      expect(params.has('page')).toBe(false);
      expect(params.get('projectId')).toBe(projectLabel === 'ITSUP' ? ITSUP_ID : null);
      expect(params.get('answer')).toBe(answerLabel === 'Not solved' ? 'NO' : null);
      expect(router.state.historyAction).toBe('REPLACE');
      expect(screen.queryByText(stale)).not.toBeInTheDocument();
      expect(screen.getByText(projectLabel)).toBeInTheDocument();
      expect(screen.getByText(answerLabel)).toBeInTheDocument();
      expect(requests).toHaveLength(1);
      expect(requests[0]?.searchParams.get('page')).toBe('0');
      expect(requests[0]?.searchParams.get('projectId')).toBe(params.get('projectId'));
      expect(requests[0]?.searchParams.get('answer')).toBe(params.get('answer'));
    },
  );

  it('AC-001.5 rejects an invalid URL date range without requesting it', async () => {
    let requests = 0;
    server.use(
      http.get('*/api/v1/suggestion-results', () => {
        requests++;
        return HttpResponse.json(emptyPage);
      }),
    );
    renderApp('/triage-history?from=2026-11-02&to=2026-11-01');
    const message = await screen.findByText(msg('MSG39'), {}, { timeout: 5000 });
    expect(message).toHaveAttribute('id', 'triage-date-error');
    expect(screen.getByLabelText('Date range')).toHaveAttribute('aria-describedby', message.id);
    expect(screen.getByLabelText('To date')).toHaveAttribute('aria-describedby', message.id);
    expect(requests).toBe(0);
  });

  it('AC-001.6 paginates twenty rows while retaining filters', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/triage-history?projectId=00000000-0000-4000-8000-000000000101');
    expect(await screen.findByText('Total 25 items', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getAllByRole('row').slice(1)).toHaveLength(20);
    await user.click(screen.getByTitle('Next Page'));
    await waitFor(() =>
      expect(within(screen.getByRole('table')).getAllByRole('row').slice(1)).toHaveLength(5),
    );
    expect(router.state.location.search).toContain('page=2');
    expect(router.state.location.search).toContain('projectId=');
  });

  it('AC-001.7 shows a separate HTTPS Jira link and detail action', async () => {
    renderApp('/triage-history');
    const row = await issueRow('ITSUP-1001');
    expect(within(row).getByText('ITSUP')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: 'ITSUP-1001' })).toHaveAttribute(
      'href',
      'https://jira.example/browse/ITSUP-1001',
    );
    expect(within(row).getByRole('button', { name: 'View details' })).toBeInTheDocument();
    // A Workspace without a Jira site URL gives a null URL: the key stays, unlinked.
    const noUrlRow = await issueRow('ITNET-2002');
    expect(within(noUrlRow).queryByRole('link', { name: 'ITNET-2002' })).not.toBeInTheDocument();
    expect(within(noUrlRow).getByText('ITNET-2002')).toBeInTheDocument();
  });

  it('AC-001.8 shows source counts, plain text excerpt, and answer labels', async () => {
    renderApp('/triage-history?page=2&projectId=00000000-0000-4000-8000-000000000101');
    const row = await issueRow('ITSUP-1003');
    expect(row).toHaveTextContent('2 Tickets');
    expect(row).toHaveTextContent('1 Wiki');
    expect(row).toHaveTextContent('No answer');
    expect(row).toHaveTextContent('—');
    expect(await issueRow('ITSUP-1004')).toHaveTextContent('Solved');
    expect(await issueRow('ITSUP-1002')).toHaveTextContent('Not solved');
  });

  it('AC-001.9 keeps the existing table during a filter refetch', async () => {
    const user = userEvent.setup();
    renderApp('/triage-history');
    const row = await issueRow('ITSUP-1001');
    let finish = () => {};
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    let started = false;
    server.use(
      http.get('*/api/v1/suggestion-results', async () => {
        started = true;
        await pending;
        return HttpResponse.json(emptyPage);
      }),
    );
    try {
      await user.click(screen.getByRole('combobox', { name: 'Answer' }));
      await user.click(await screen.findByRole('option', { name: 'Not solved' }));
      await waitFor(() => expect(started).toBe(true));
      expect(screen.getByRole('table', { name: 'Suggestion history' })).toContainElement(row);
      expect(screen.queryByText('Loading ...')).not.toBeInTheDocument();
    } finally {
      finish();
    }
    await screen.findByText(msg('MSG04'));
  });

  it('AC-001.9 shows loading, empty, and retry states with filters retained', async () => {
    const user = userEvent.setup();
    let fail = true;
    const requests: string[] = [];
    server.use(
      http.get('*/api/v1/suggestion-results', ({ request }) => {
        requests.push(request.url);
        return fail ? problem(500, 'MSG06') : HttpResponse.json(emptyPage);
      }),
    );
    renderApp('/triage-history?answer=NONE');
    expect(await screen.findByText('Loading ...', {}, { timeout: 5000 })).toBeInTheDocument();
    await screen.findByText(msg('MSG06'));
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    await screen.findByText(msg('MSG04'));
    expect(requests).toHaveLength(2);
    expect(requests[0]).toBe(requests[1]);
  });

  it('AC-001.10 denies a non-Project Manager before making a list request', async () => {
    let requests = 0;
    server.use(
      http.get('*/api/v1/suggestion-results', () => {
        requests++;
        return HttpResponse.json(emptyPage);
      }),
    );
    renderApp('/triage-history', { role: 'EMPLOYEE' });
    expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(requests).toBe(0);
  });

  it('AC-001.12 never makes an unsafe Jira URL clickable or renders HTML', async () => {
    renderApp('/triage-history?page=2&projectId=00000000-0000-4000-8000-000000000101');
    const row = await issueRow('ITSUP-1004');
    expect(within(row).queryByRole('link', { name: 'ITSUP-1004' })).not.toBeInTheDocument();
    const htmlRow = await issueRow('ITSUP-1002');
    expect(htmlRow).toHaveTextContent(SCRIPT_SUGGESTION);
    const excerpt = within(htmlRow).getByText(SCRIPT_SUGGESTION);
    expect(excerpt.childNodes).toHaveLength(1);
    expect(excerpt.firstChild?.nodeType).toBe(Node.TEXT_NODE);
    expect(excerpt.firstChild?.textContent).toBe(SCRIPT_SUGGESTION);
    expect(htmlRow.querySelector('script')).toBeNull();
  });

  it.each([
    'https://user@jira.example/browse/ITSUP-1001',
    'https://:password@jira.example/browse/ITSUP-1001',
  ])('AC-001.12 rejects Jira URLs containing credentials: %s', async (jiraIssueUrl) => {
    server.use(
      http.get('*/api/v1/suggestion-results', () =>
        HttpResponse.json<SuggestionResultPage>({
          ...emptyPage,
          content: [
            {
              issueKey: 'ITSUP-1001',
              jiraIssueUrl,
              project: { projectId: ITSUP_ID, key: 'ITSUP' },
              createdAt: '2026-11-01T10:00:00Z',
              ticketSourceCount: 0,
              wikiSourceCount: 0,
              suggestedSolutionExcerpt: null,
              reporterAnswer: null,
            },
          ],
        }),
      ),
    );
    renderApp('/triage-history');
    const row = await issueRow('ITSUP-1001');
    expect(within(row).queryByRole('link', { name: 'ITSUP-1001' })).not.toBeInTheDocument();
  });

  it('AC-002.1 opens detail with full result fields', async () => {
    const user = userEvent.setup();
    renderApp('/triage-history');
    const row = await issueRow('ITSUP-1001');
    await user.click(within(row).getByRole('button', { name: 'View details' }));
    const dialog = await screen.findByRole('dialog', { name: 'Triage Result Detail' });
    expect(await within(dialog).findByText(VPN_SUGGESTION)).toBeInTheDocument();
    expect(dialog).toHaveTextContent('ITSUP-1001');
    expect(within(dialog).getByText('ITSUP')).toBeInTheDocument();
    expect(within(dialog).getByText('Jira project key').closest('dt')).toBeInTheDocument();
    expect(within(dialog).getByText('ITSUP').closest('dd')).toBeInTheDocument();
    expect(within(dialog).getByText('01/11/2026 17:00').closest('dd')).toBeInTheDocument();
    expect(within(dialog).getAllByRole('term')).toHaveLength(6);
    expect(within(dialog).getAllByRole('definition')).toHaveLength(6);
    expect(within(dialog).getByText(VPN_SUGGESTION).closest('dl')).toBeInTheDocument();
    expect(dialog).toHaveTextContent('Check the VPN configuration, then retry the connection.');
    expect(dialog).toHaveTextContent('Solved');
  });

  it('AC-002.2 shows no suggestion and no answer as read-only values', async () => {
    const user = userEvent.setup();
    renderApp('/triage-history?page=2&projectId=00000000-0000-4000-8000-000000000101');
    const row = await issueRow('ITSUP-1003');
    await user.click(within(row).getByRole('button', { name: 'View details' }));
    const dialog = await screen.findByRole('dialog', { name: 'Triage Result Detail' });
    expect(await within(dialog).findByText('No answer')).toBeInTheDocument();
    expect(dialog).toHaveTextContent('—');
  });

  it('AC-002.3 shows Ticket count only and available Wiki links', async () => {
    const user = userEvent.setup();
    renderApp('/triage-history');
    const row = await issueRow('ITSUP-1001');
    await user.click(within(row).getByRole('button', { name: 'View details' }));
    const dialog = await screen.findByRole('dialog', { name: 'Triage Result Detail' });
    const link = await within(dialog).findByRole('link', { name: 'VPN troubleshooting' });
    expect(link).toHaveAttribute('href', '/wiki/documents/00000000-0000-4000-8000-000000000a01');
    expect(dialog).toHaveTextContent('2 Tickets');
    expect(within(dialog).queryByRole('link', { name: /Ticket/ })).not.toBeInTheDocument();
  });

  it('AC-002.4 shows MSG110 for unavailable Wiki without a link', async () => {
    const user = userEvent.setup();
    renderApp('/triage-history');
    const row = await issueRow('ITSUP-1001');
    await user.click(within(row).getByRole('button', { name: 'View details' }));
    const dialog = await screen.findByRole('dialog', { name: 'Triage Result Detail' });
    expect(await within(dialog).findByText(new RegExp(msg('MSG110')))).toBeInTheDocument();
    expect(
      within(dialog).queryByRole('link', { name: 'Archived network guide' }),
    ).not.toBeInTheDocument();
  });

  it('AC-002.5 shows detail loading, 404 and retryable errors', async () => {
    const user = userEvent.setup();
    let status = 500;
    server.use(
      http.get('*/api/v1/suggestion-results/:issueKey', async () => {
        await new Promise((resolve) => setTimeout(resolve, 80));
        return status === 500 ? problem(500, 'MSG06') : problem(404, 'MSG04');
      }),
    );
    renderApp('/triage-history');
    const row = await issueRow('ITSUP-1001');
    await user.click(within(row).getByRole('button', { name: 'View details' }));
    const dialog = await screen.findByRole('dialog', { name: 'Triage Result Detail' });
    expect(within(dialog).getByText('Loading ...')).toBeInTheDocument();
    await within(dialog).findByText(msg('MSG06'));
    status = 404;
    await user.click(within(dialog).getByRole('button', { name: 'Try again' }));
    expect(await within(dialog).findByText(msg('MSG04'))).toBeInTheDocument();
  });

  it('AC-002.6 clears old detail before another issue opens', async () => {
    const user = userEvent.setup();
    renderApp('/triage-history');
    const first = await issueRow('ITSUP-1001');
    await user.click(within(first).getByRole('button', { name: 'View details' }));
    const dialog = await screen.findByRole('dialog', { name: 'Triage Result Detail' });
    await within(dialog).findByText(VPN_SUGGESTION);
    const closeButton = within(dialog).getAllByRole('button', { name: 'Close' }).at(-1);
    if (!closeButton) throw new Error('Close button was not found');
    await user.click(closeButton);
    await waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: 'Triage Result Detail' }),
      ).not.toBeInTheDocument(),
    );
    await user.click(screen.getByTitle('Next Page'));
    const second = await issueRow('ITSUP-1002');
    await user.click(within(second).getByRole('button', { name: 'View details' }));
    expect(
      within(await screen.findByRole('dialog', { name: 'Triage Result Detail' })).queryByText(
        VPN_SUGGESTION,
      ),
    ).not.toBeInTheDocument();
    expect(
      await within(screen.getByRole('dialog')).findByText(SCRIPT_SUGGESTION),
    ).toBeInTheDocument();
  });

  it('AC-002.8 offers no class, routing, or mutation controls', async () => {
    const user = userEvent.setup();
    renderApp('/triage-history');
    const row = await issueRow('ITSUP-1001');
    await user.click(within(row).getByRole('button', { name: 'View details' }));
    const dialog = await screen.findByRole('dialog', { name: 'Triage Result Detail' });
    for (const name of ['Correct class', 'Route', 'Save', 'Submit', 'Delete']) {
      expect(within(dialog).queryByRole('button', { name })).not.toBeInTheDocument();
    }
  });
});
