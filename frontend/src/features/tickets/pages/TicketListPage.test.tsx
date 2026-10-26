import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { MOCK_USERS } from '@/mocks/session';
import { getGetTicketsMockHandler } from '@/shared/api/generated/tickets/tickets.msw';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

type Options = Parameters<typeof renderApp>[1];

/** The fixture users' Departments (src/mocks/session.ts). */
const IT_ID = '00000000-0000-4000-8000-0000000000d1';
const HR_ID = '00000000-0000-4000-8000-0000000000d2';

/** The Ticket List; resolves once the first page is shown. */
async function openList(path = '/tickets', options: Options = {}) {
  const user = userEvent.setup();
  const view = renderApp(path, options);
  // The first render also loads the lazy page module (BUG-004: 5 s for the first findBy, guideline 10 §6).
  await screen.findByText(/^Total \d+ items$/, {}, { timeout: 5000 });
  return { user, ...view };
}

function dataRows(): HTMLElement[] {
  return within(screen.getByRole('table')).getAllByRole('row').slice(1); // without the header row
}

function rowOf(ticketId: string): HTMLElement {
  const row = screen.getByRole('link', { name: ticketId }).closest('tr');
  if (!row) throw new Error(`no row for ${ticketId}`);
  return row;
}

/** The query string of every GET /api/v1/tickets; the request still reaches the mock handler. */
function recordRequests(): URLSearchParams[] {
  const requests: URLSearchParams[] = [];
  server.use(
    http.get('*/api/v1/tickets', ({ request }) => {
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

describe('TicketListPage', () => {
  beforeAll(async () => {
    await import('./TicketListPage');
  });

  it('AC-001.1 shows the filters and the eight columns to a Project Manager', async () => {
    await openList();
    expect(screen.getByRole('heading', { name: 'Ticket List' })).toBeInTheDocument();
    for (const label of ['Department', 'Source', 'Status']) {
      expect(screen.getByRole('combobox', { name: label })).toBeInTheDocument();
    }
    const headers = within(screen.getByRole('table'))
      .getAllByRole('columnheader')
      .map((h) => h.textContent);
    expect(headers).toEqual([
      'Ticket ID',
      'Title',
      'Project',
      'Department',
      'Source',
      'Status',
      'Review due date',
      'Updated',
    ]);
  });

  it('AC-001.1 shows the Ticket List to a Ticket Resolver', async () => {
    await openList('/tickets', { role: 'TICKET_RESOLVER' });
    expect(screen.getByRole('heading', { name: 'Ticket List' })).toBeInTheDocument();
  });

  it('AC-001.2 loads page 0, size 20, newest Updated first, with no filter', async () => {
    const requests = recordRequests();
    await openList();
    const first = requests[0];
    expect(first?.get('page')).toBe('0');
    expect(first?.get('size')).toBe('20');
    expect(first?.getAll('sort')).toEqual(['updatedAt,desc']);
    for (const name of ['keyword', 'departmentId', 'source', 'status']) {
      expect(first?.has(name)).toBe(false);
    }
    expect(dataRows()).toHaveLength(20);
    expect(dataRows()[0]).toHaveTextContent('ITNET-0412'); // updated 02/10/2026 18:31
  });

  it('AC-001.3 shows each column of a row, and "—" for an empty review due date', async () => {
    await openList();
    expect(screen.getByRole('link', { name: 'ITSUP-1102' })).toHaveAttribute(
      'href',
      '/tickets/7a1c0000-0000-4000-8000-000000000003',
    );
    const cells = within(rowOf('ITSUP-1102'))
      .getAllByRole('cell')
      .map((c) => c.textContent);
    expect(cells).toEqual([
      'ITSUP-1102',
      'VPN keeps dropping during Teams calls',
      'ITSUP',
      'IT',
      'Solution Form',
      'Pending review',
      '05/10/2026 16:02',
      '02/10/2026 16:02',
    ]);
    const published = within(rowOf('ITSUP-1024')).getAllByRole('cell');
    expect(published[5]).toHaveTextContent('Published');
    expect(published[6]).toHaveTextContent('—');
  });

  it('AC-001.4 searches the keyword only on Enter, trimmed, from page 1', async () => {
    const { user, router } = await openList('/tickets?page=2');
    const keyword = screen.getByLabelText('Keyword');
    expect(keyword).toHaveAttribute('placeholder', 'Search by ticket ID, title or solution');
    await user.type(keyword, '  payslip ');
    expect(router.state.location.search).toBe('?page=2'); // typing alone does not reload

    await user.keyboard('{Enter}');
    await waitFor(() => expect(router.state.location.search).toBe('?keyword=payslip'));
    await waitFor(() => expect(dataRows()).toHaveLength(1));
    expect(dataRows()[0]).toHaveTextContent('HRHELP-0318');
  });

  it('AC-001.4 removes the keyword when the box is empty or white space', async () => {
    const { user, router } = await openList('/tickets?keyword=vpn');
    const keyword = screen.getByLabelText('Keyword');
    expect(keyword).toHaveValue('vpn');
    await user.clear(keyword);
    await user.type(keyword, '   {Enter}');
    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(await screen.findByText('Total 28 items')).toBeInTheDocument();
  });

  it('AC-001.5 accepts at most 100 characters in the Keyword box', async () => {
    const { user } = await openList();
    const keyword = screen.getByLabelText('Keyword');
    await user.click(keyword);
    await user.paste('x'.repeat(120));
    expect(keyword).toHaveValue('x'.repeat(100));
  });

  it('AC-001.6 filters by one of my Departments by its id, and All Departments removes it', async () => {
    const requests = recordRequests();
    const { user, router } = await openList('/tickets', { user: 'tr-two-departments' });
    await user.click(screen.getByRole('combobox', { name: 'Department' }));
    expect(await findOption('All Departments')).toBeInTheDocument();
    expect(await findOption('IT')).toBeInTheDocument();
    await user.click(await findOption('HR'));

    await waitFor(() => expect(router.state.location.search).toBe(`?departmentId=${HR_ID}`));
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'ITSUP-1024' })).not.toBeInTheDocument(),
    );
    expect(screen.getByText('Total 10 items')).toBeInTheDocument();
    expect(requests.at(-1)?.get('departmentId')).toBe(HR_ID);

    await pick(user, 'Department', 'All Departments');
    await waitFor(() => expect(router.state.location.search).toBe(''));
  });

  it('AC-001.6 offers only "All Departments" to a user with no Department', async () => {
    server.use(
      http.get('*/api/v1/me', () => HttpResponse.json({ ...MOCK_USERS.tr, departments: [] })),
    );
    const { user } = await openList('/tickets', { role: 'TICKET_RESOLVER' });
    await user.click(screen.getByRole('combobox', { name: 'Department' }));
    expect(await findOption('All Departments')).toBeInTheDocument();
    expect(document.querySelectorAll('.ant-select-item-option')).toHaveLength(1);
  });

  it('AC-001.7 filters by Source and Status from page 1', async () => {
    const { user, router } = await openList('/tickets?page=2');
    await pick(user, 'Source', 'CSV');
    await waitFor(() => expect(router.state.location.search).toBe('?source=CSV'));
    await pick(user, 'Status', 'Rejected');
    await waitFor(() => expect(router.state.location.search).toBe('?source=CSV&status=REJECTED'));
    await waitFor(() => expect(dataRows()).toHaveLength(1));
    expect(dataRows()[0]).toHaveTextContent('ITSUP-1042');
  });

  it('AC-001.7 removes Source and Status when their defaults are picked', async () => {
    const { user, router } = await openList('/tickets?source=CSV&status=REJECTED');
    await pick(user, 'Source', 'All');
    await waitFor(() => expect(router.state.location.search).toBe('?status=REJECTED'));
    await pick(user, 'Status', 'All statuses');
    await waitFor(() => expect(router.state.location.search).toBe(''));
  });

  it('AC-001.7 offers the four sources and the six statuses', async () => {
    const { user } = await openList();
    await user.click(screen.getByRole('combobox', { name: 'Source' }));
    for (const label of ['All', 'Solution Form', 'Jira import', 'CSV', 'Manual']) {
      expect(await findOption(label)).toBeInTheDocument();
    }
    await user.click(screen.getByRole('combobox', { name: 'Status' }));
    for (const label of [
      'All statuses',
      'Pending review',
      'Changes requested',
      'Published',
      'Rejected',
      'Unpublished',
      'Expired',
    ]) {
      expect(await findOption(label)).toBeInTheDocument();
    }
  });

  it('AC-001.8 sorts by a sortable column header', async () => {
    const { user, router } = await openList();
    const table = screen.getByRole('table');
    const sortable = ['Ticket ID', 'Title', 'Project', 'Review due date', 'Updated'];
    for (const header of within(table).getAllByRole('columnheader')) {
      const isSortable = header.classList.contains('ant-table-column-has-sorters');
      expect(isSortable).toBe(sortable.includes(header.textContent));
    }
    await user.click(within(table).getByRole('columnheader', { name: 'Ticket ID' }));
    await waitFor(() => expect(router.state.location.search).toBe('?sort=sourceTicketId%2Casc'));
    await waitFor(() => expect(dataRows()[0]).toHaveTextContent('HRHELP-0228'));
  });

  it('AC-001.9 shows the total and loads another page with the same filters and sort', async () => {
    const requests = recordRequests();
    const { user, router } = await openList('/tickets?sort=title%2Casc');
    expect(screen.getByText('Total 28 items')).toBeInTheDocument();
    await user.click(screen.getByTitle('2'));
    await waitFor(() => expect(router.state.location.search).toBe('?sort=title%2Casc&page=2'));
    await waitFor(() => expect(dataRows()).toHaveLength(8));
    const last = requests.at(-1);
    expect(last?.get('page')).toBe('1');
    expect(last?.getAll('sort')).toEqual(['title,asc']);
  });

  it('AC-001.10 shows the list of a shared link, with its values in the filters', async () => {
    const requests = recordRequests();
    renderApp(
      `/tickets?keyword=vpn&departmentId=${IT_ID}&source=SOLUTION_FORM&status=PENDING_REVIEW`,
    );
    expect(
      await screen.findByRole('link', { name: 'ITSUP-1102' }, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(dataRows()).toHaveLength(1);
    expect(screen.getByLabelText('Keyword')).toHaveValue('vpn');
    expect(selected('Department')).toBe('IT');
    expect(selected('Source')).toBe('Solution Form');
    expect(selected('Status')).toBe('Pending review');
    expect(requests[0]?.get('departmentId')).toBe(IT_ID);
  });

  it('AC-001.10 ignores a Source or Status that is not valid', async () => {
    const requests = recordRequests();
    await openList('/tickets?source=FAX&status=FOO');
    for (const name of ['source', 'status']) {
      expect(requests[0]?.has(name)).toBe(false);
    }
    expect(selected('Source')).toBe('All');
    expect(selected('Status')).toBe('All statuses');
  });

  it('AC-001.10 shows an empty page with the filters when a shared link is past the last page', async () => {
    renderApp('/tickets?status=REJECTED&page=99');
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(selected('Status')).toBe('Rejected');
  });

  it('AC-001.10 resets the Keyword box when going back in history', async () => {
    const { user, router } = await openList();
    await user.type(screen.getByLabelText('Keyword'), 'payslip{Enter}');
    await waitFor(() => expect(router.state.location.search).toBe('?keyword=payslip'));
    await router.navigate(-1);
    await waitFor(() => expect(screen.getByLabelText('Keyword')).toHaveValue(''));
  });

  it('AC-001.11 shows the loading state while the list loads', async () => {
    server.use(
      http.get('*/api/v1/tickets', async () => {
        await delay('infinite');
      }),
    );
    renderApp('/tickets');
    await waitFor(() => expect(document.querySelector('.ant-spin-spinning')).toBeInTheDocument(), {
      timeout: 5000,
    });
  });

  it('AC-001.12 shows MSG04 when no ticket is found', async () => {
    server.use(
      getGetTicketsMockHandler({
        content: [],
        page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
      }),
    );
    renderApp('/tickets');
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('AC-001.13 shows the error with Try again, which loads the list again', async () => {
    let failures = 1;
    server.use(
      http.get('*/api/v1/tickets', () => {
        if (failures-- > 0) return problem(500);
      }),
    );
    const user = userEvent.setup();
    renderApp('/tickets');
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG06'));
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('link', { name: 'ITSUP-1024' })).toBeInTheDocument();
  });

  it('AC-001.14 shows a title with HTML as plain text', async () => {
    const html = '<img src=x onerror=alert(1)>Printer <b>down</b>';
    server.use(
      getGetTicketsMockHandler({
        content: [
          {
            ticketId: '7a1c0000-0000-4000-8000-000000000001',
            sourceTicketId: 'ITSUP-1024',
            title: html,
            project: { projectId: '00000000-0000-4000-8000-000000000101', name: 'ITSUP' },
            department: { departmentId: IT_ID, name: 'IT' },
            source: 'MANUAL',
            status: 'PUBLISHED',
            reviewDueDate: null,
            updatedAt: '2026-10-02T11:24:00Z',
          },
        ],
        page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
      }),
    );
    await openList();
    expect(screen.getByText(html)).toBeInTheDocument();
    expect(screen.getByRole('table').querySelector('img, b')).toBeNull();
  });
});
