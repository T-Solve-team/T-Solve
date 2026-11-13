import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { MOCK_USERS } from '@/mocks/session';
import type { CurrentUser } from '@/shared/api';
import { getListProjectsMockHandler } from '@/shared/api/generated/projects/projects.msw';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
import { MOCK_PROJECT_IDS } from '../mocks/handlers';

const HEADINGS = ['Project ID', 'Jira project key', 'Project Manager', 'Members', 'Status'];

/** Counts every `GET /api/v1/projects` the app sends during one test. */
function countListRequests() {
  const seen = { count: 0 };
  const onStart = ({ request }: { request: Request }) => {
    if (request.method === 'GET' && new URL(request.url).pathname === '/api/v1/projects') {
      seen.count += 1;
    }
  };
  server.events.on('request:start', onStart);
  return seen;
}

function cellsOf(row: HTMLElement): string[] {
  return within(row)
    .getAllByRole('cell')
    .map((cell) => cell.textContent ?? '');
}

async function findRow(key: string) {
  return screen.findByRole('row', { name: new RegExp(`\\b${key}\\b`) }, { timeout: 5000 });
}

describe('JiraIntegrationPage', () => {
  beforeAll(async () => {
    // Load the lazy page once, so the first test's wait covers only the render (guideline 10 §6).
    await import('./JiraIntegrationPage');
  });
  afterEach(() => {
    server.events.removeAllListeners();
  });

  it('AC-005.1 AC-005.2 AC-005.5 loads the Projects and shows the read-only Department and the table', async () => {
    const requests = countListRequests();
    renderApp('/jira-integration', { role: 'PROJECT_MANAGER' });

    const oldRow = await findRow('OLD');
    const department = screen.getByLabelText('Department');
    expect(department).toHaveValue('IT Operations');
    expect(department).toHaveAttribute('readonly');
    expect(requests.count).toBe(1);

    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(HEADINGS);
    const rows = screen.getAllByRole('row').slice(1);
    expect(rows.map((row) => cellsOf(row)[1])).toEqual(['OLD', 'OPS']);
    expect(cellsOf(oldRow)).toEqual([MOCK_PROJECT_IDS.OLD, 'OLD', 'Đỗ Minh Quân', '2', 'Removed']);
    expect(cellsOf(await findRow('OPS'))).toEqual([
      MOCK_PROJECT_IDS.OPS,
      'OPS',
      'Đỗ Minh Quân',
      '3',
      'Active',
    ]);
    expect(screen.queryByRole('row', { name: /\bSUP\b/ })).not.toBeInTheDocument();
  });

  it('AC-005.3 keeps the returned row order instead of sorting by key (D55)', async () => {
    const row = (suffix: string, jiraProjectKey: string) => ({
      projectId: `00000000-0000-4000-8000-0000000003${suffix}`,
      jiraProjectKey,
      projectManager: null,
      memberCount: 0,
      status: 'ACTIVE' as const,
    });
    server.use(
      getListProjectsMockHandler({
        department: { departmentId: '00000000-0000-4000-8000-0000000003d1', name: 'IT Operations' },
        projects: [row('b1', 'ZED'), row('b2', 'ABC')],
      }),
    );
    renderApp('/jira-integration', { role: 'PROJECT_MANAGER' });

    await findRow('ZED');
    const rows = screen.getAllByRole('row').slice(1);
    expect(rows.map((r) => cellsOf(r)[1])).toEqual(['ZED', 'ABC']);
  });

  it('AC-005.4 shows MSG54 when a Project has no Project Manager', async () => {
    server.use(
      getListProjectsMockHandler({
        department: { departmentId: '00000000-0000-4000-8000-0000000003d1', name: 'IT Operations' },
        projects: [
          {
            projectId: '00000000-0000-4000-8000-0000000003a9',
            jiraProjectKey: 'NOPM',
            projectManager: null,
            memberCount: 0,
            status: 'ACTIVE',
          },
        ],
      }),
    );
    renderApp('/jira-integration', { role: 'PROJECT_MANAGER' });

    expect(cellsOf(await findRow('NOPM'))).toEqual([
      '00000000-0000-4000-8000-0000000003a9',
      'NOPM',
      msg('MSG54'),
      '0',
      'Active',
    ]);
  });

  it('AC-005.6 shows the loading state in place of the table while the first request runs', async () => {
    let release: () => void = () => undefined;
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    const request = { started: false };
    server.use(
      http.get(
        '*/api/v1/projects',
        async () => {
          request.started = true;
          await held;
          return undefined; // fall through to the feature's handler
        },
        { once: true },
      ),
    );
    renderApp('/jira-integration', { role: 'PROJECT_MANAGER' });

    // Only once the page's own request is held, so the route's loading view cannot pass the check.
    await waitFor(() => expect(request.started).toBe(true), { timeout: 5000 });
    expect(screen.getByText('Loading ...')).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'Project ID' })).not.toBeInTheDocument();
    release();
    expect(await findRow('OPS')).toBeInTheDocument();
    expect(screen.queryByText('Loading ...')).not.toBeInTheDocument();
  });

  it('AC-005.7 shows the returned message with its params and retries the request', async () => {
    const requests = countListRequests();
    // Any coded answer is shown as returned; MSG48 is used here because it carries params.
    server.use(
      http.get(
        '*/api/v1/projects',
        () => problem(409, 'MSG48', { params: { tracker_name: 'Jira' } }),
        {
          once: true,
        },
      ),
    );
    const user = userEvent.setup();
    renderApp('/jira-integration', { role: 'PROJECT_MANAGER' });

    const alert = await screen.findByRole('alert', {}, { timeout: 5000 });
    expect(alert).toHaveTextContent(msg('MSG48', { tracker_name: 'Jira' }));
    expect(screen.queryByRole('columnheader', { name: 'Project ID' })).not.toBeInTheDocument();

    await user.click(within(alert).getByRole('button', { name: 'Try again' }));
    expect(await findRow('OPS')).toBeInTheDocument();
    expect(requests.count).toBe(2);
  });

  it('AC-005.7 shows MSG06 when the request cannot reach the server', async () => {
    server.use(http.get('*/api/v1/projects', () => HttpResponse.error(), { once: true }));
    renderApp('/jira-integration', { role: 'PROJECT_MANAGER' });

    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG06'));
  });

  it('AC-005.8 shows MSG04 in place of the table when the Department has no Projects', async () => {
    server.use(
      getListProjectsMockHandler({
        department: { departmentId: '00000000-0000-4000-8000-0000000003d1', name: 'IT Operations' },
        projects: [],
      }),
    );
    renderApp('/jira-integration', { role: 'PROJECT_MANAGER' });

    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByLabelText('Department')).toHaveValue('IT Operations');
    expect(screen.queryByRole('columnheader', { name: 'Project ID' })).not.toBeInTheDocument();
  });

  it('AC-005.9 keeps the placeholder for an Admin without the Project Manager role, with no request and no Add project', async () => {
    const requests = countListRequests();
    renderApp('/jira-integration', { role: 'ADMIN' });

    expect(
      await screen.findByText(/3\.1 Jira Integration — not built yet/, {}, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Department')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add project' })).not.toBeInTheDocument();
    expect(requests.count).toBe(0);
  });

  it('AC-005.9 shows the Projects to a user who is both Admin and Project Manager', async () => {
    const adminPm: CurrentUser = {
      ...MOCK_USERS.pm,
      roles: ['EMPLOYEE', 'ADMIN', 'PROJECT_MANAGER'],
    };
    server.use(http.get('*/api/v1/me', () => HttpResponse.json(adminPm)));
    renderApp('/jira-integration', { role: 'PROJECT_MANAGER' });

    expect(await findRow('OPS')).toBeInTheDocument();
    expect(screen.queryByText(/not built yet/)).not.toBeInTheDocument();
  });

  it('AC-005.10 shows the route guard MSG08 page to an Employee without a Projects request', async () => {
    const requests = countListRequests();
    renderApp('/jira-integration', { role: 'EMPLOYEE' });

    expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByLabelText('Department')).not.toBeInTheDocument();
    expect(requests.count).toBe(0);
  });
});
