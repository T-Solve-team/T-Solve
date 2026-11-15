import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { ImportHistoryItem, ImportHistoryPage } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { formatDateTime } from '@/shared/lib';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

const PROJECT = '00000000-0000-4000-8000-000000000101';
const JIRA: ImportHistoryItem = {
  batchId: 3,
  createdAt: '2026-11-14T16:30:00Z',
  performedBy: 'Nguyễn Văn An',
  project: { projectId: PROJECT, projectKey: 'ITSUP' },
  department: { id: '00000000-0000-4000-8000-0000000000d1', name: 'IT' },
  source: 'JIRA_IMPORT',
  dateFrom: '2026-10-01',
  dateTo: '2026-10-31',
  fileName: null,
  publishedCount: 7,
  removedCount: 2,
  errorCount: 3,
  status: 'COMPLETED',
  errorCode: null,
};
const CSV: ImportHistoryItem = {
  ...JIRA,
  batchId: 2,
  source: 'CSV',
  dateFrom: null,
  dateTo: null,
  fileName: 'resolved-tickets.csv',
  status: 'FAILED',
  publishedCount: 0,
  errorCode: 'MSG33',
};
const MANUAL: ImportHistoryItem = { ...CSV, batchId: 1, source: 'MANUAL', fileName: null };
const EMPTY: ImportHistoryPage = {
  content: [],
  page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
};

function history(content: ImportHistoryItem[], total = content.length): ImportHistoryPage {
  return {
    content,
    page: { size: 20, number: 0, totalElements: total, totalPages: Math.ceil(total / 20) },
  };
}

describe('ImportHistoryPage', () => {
  it('AC-007.9 shows every column, Jira date range, CSV file name and Manual placeholder', async () => {
    server.use(
      http.get('*/api/v1/imports/history', () => HttpResponse.json(history([JIRA, CSV, MANUAL]))),
    );
    renderApp('/import/history');
    const table = await screen.findByRole('table', { name: 'Import History' }, { timeout: 5000 });
    for (const name of [
      'Date',
      'Project Manager',
      'Project',
      'Department',
      'Source',
      'Date range / file',
      'Published',
      'Removed / errors',
      'Status',
      'Error',
    ]) {
      expect(within(table).getByRole('columnheader', { name })).toBeInTheDocument();
    }
    const jira = within(table).getByText('Jira import').closest('tr');
    const csv = within(table).getByText('resolved-tickets.csv').closest('tr');
    const manual = within(table).getByText('Manual').closest('tr');
    if (!jira || !csv || !manual) throw new Error('Missing history row');
    expect(jira).toHaveTextContent(formatDateTime(JIRA.createdAt));
    expect(jira).toHaveTextContent('Nguyễn Văn An');
    expect(jira).toHaveTextContent('01/10/2026 – 31/10/2026');
    expect(jira).toHaveTextContent('2 / 3');
    expect(jira).toHaveTextContent('Completed');
    expect(csv).toHaveTextContent('Failed');
    expect(csv).toHaveTextContent('MSG33');
    expect(manual).toHaveTextContent('—');
  });

  it('AC-007.9 sends Project and source selections with dates and resets paging', async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get('*/api/v1/imports/history', ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(EMPTY);
      }),
    );
    const { router } = renderApp('/import/history?page=2&dateFrom=2026-11-05&dateTo=2026-11-06');
    await screen.findByText(msg('MSG04'), {}, { timeout: 5000 });
    expect(requests[0]?.searchParams.get('page')).toBe('1');
    expect(requests[0]?.searchParams.get('size')).toBe('20');
    await user.click(screen.getByRole('combobox', { name: 'Project' }));
    await user.click(await screen.findByRole('option', { name: 'ITSUP · IT' }));
    await user.click(screen.getByRole('combobox', { name: 'Source' }));
    await user.click(await screen.findByRole('option', { name: 'CSV' }));
    await waitFor(() => expect(requests.at(-1)?.searchParams.get('source')).toBe('CSV'));
    const query = requests.at(-1)?.searchParams;
    expect(query?.get('projectId')).toBe(PROJECT);
    expect(query?.get('source')).toBe('CSV');
    expect(query?.get('dateFrom')).toBe('2026-11-05');
    expect(query?.get('dateTo')).toBe('2026-11-06');
    expect(query?.get('page')).toBe('0');
    expect(new URLSearchParams(router.state.location.search).has('page')).toBe(false);
  });

  it('AC-007.9 sends picked calendar days with the other filters and clears both dates', async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get('*/api/v1/imports/history', ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(EMPTY);
      }),
    );
    renderApp(
      `/import/history?page=2&projectId=${PROJECT}&source=CSV&dateFrom=2026-11-05&dateTo=2026-11-06`,
    );
    await screen.findByText(msg('MSG04'), {}, { timeout: 5000 });
    await user.click(screen.getByLabelText('Date range'));
    await user.click(await screen.findByTitle('2026-11-01'));
    await user.click(await screen.findByTitle('2026-11-03'));
    await waitFor(() => expect(requests.at(-1)?.searchParams.get('dateTo')).toBe('2026-11-03'));
    expect(requests.at(-1)?.searchParams.get('dateFrom')).toBe('2026-11-01');
    expect(requests.at(-1)?.searchParams.get('projectId')).toBe(PROJECT);
    expect(requests.at(-1)?.searchParams.get('source')).toBe('CSV');
    expect(requests.at(-1)?.searchParams.get('page')).toBe('0');
    expect(screen.getByLabelText('Date range')).toHaveValue('01/11/2026');
    expect(screen.getByLabelText('To date')).toHaveValue('03/11/2026');
    await user.click(screen.getByRole('button', { name: 'Clear date range' }));
    await waitFor(() => expect(requests.at(-1)?.searchParams.has('dateFrom')).toBe(false));
    expect(requests.at(-1)?.searchParams.has('dateTo')).toBe(false);
  });

  it('AC-007.9 offers only managed Projects and the three import sources', async () => {
    const user = userEvent.setup();
    renderApp('/import/history', { user: 'pm-km' });
    await screen.findByText(msg('MSG04'), {}, { timeout: 5000 });
    await user.click(screen.getByRole('combobox', { name: 'Project' }));
    expect(await screen.findByRole('option', { name: 'HRHELP · HR' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'ITSUP · IT' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('combobox', { name: 'Source' }));
    for (const source of ['Jira import', 'CSV', 'Manual']) {
      expect(await screen.findByRole('option', { name: source })).toBeInTheDocument();
    }
    expect(screen.queryByRole('option', { name: 'Solution Form' })).not.toBeInTheDocument();
  });

  it('AC-007.9 loads the registered mock history with twenty rows per page', async () => {
    renderApp('/import/history');
    const table = await screen.findByRole('table', { name: 'Import History' }, { timeout: 5000 });
    expect(await screen.findByText('Total 25 items')).toBeInTheDocument();
    expect(within(table).getAllByText('Đỗ Minh Quân')).toHaveLength(20);
  });

  it('AC-007.9 changes server pages while retaining filters', async () => {
    const requests: URL[] = [];
    server.use(
      http.get('*/api/v1/imports/history', ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(
          history(url.searchParams.get('page') === '1' ? [CSV] : [JIRA], 21),
        );
      }),
    );
    const user = userEvent.setup();
    renderApp(`/import/history?projectId=${PROJECT}&source=CSV`);
    await screen.findByText('Jira import', {}, { timeout: 5000 });
    await user.click(screen.getByTitle('Next Page'));
    await screen.findByText('resolved-tickets.csv');
    expect(requests.at(-1)?.searchParams.get('page')).toBe('1');
    expect(requests.at(-1)?.searchParams.get('projectId')).toBe(PROJECT);
    expect(requests.at(-1)?.searchParams.get('source')).toBe('CSV');
  });

  it('AC-007.9 shows MSG06 and retries a failed load', async () => {
    let fails = true;
    server.use(
      http.get('*/api/v1/imports/history', () =>
        fails ? problem(500, 'MSG06') : HttpResponse.json(EMPTY),
      ),
    );
    const user = userEvent.setup();
    renderApp('/import/history');
    expect(await screen.findByText(msg('MSG06'), {}, { timeout: 5000 })).toBeInTheDocument();
    fails = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText(msg('MSG04'))).toBeInTheDocument();
  });

  it('AC-007.10 shows MSG04 for an empty page', async () => {
    server.use(http.get('*/api/v1/imports/history', () => HttpResponse.json(EMPTY)));
    renderApp('/import/history');
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByText('resolved-tickets.csv')).not.toBeInTheDocument();
  });
});
