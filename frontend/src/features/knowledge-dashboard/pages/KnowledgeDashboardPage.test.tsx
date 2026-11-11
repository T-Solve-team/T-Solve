import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';
import { presetRange } from '../hooks/useDashboardFilters';

const IT = '00000000-0000-4000-8000-0000000000d1';
const HR = '00000000-0000-4000-8000-0000000000d2';
const ITSUP = '00000000-0000-4000-8000-000000000101';

function recordRequests() {
  const requests: URL[] = [];
  server.use(
    http.get('*/api/v1/dashboard', ({ request }) => {
      requests.push(new URL(request.url));
      return undefined;
    }),
  );
  return requests;
}

async function choose(label: string, option: string) {
  const user = userEvent.setup();
  await user.click(screen.getByRole('combobox', { name: label }));
  await user.click(await screen.findByRole('option', { name: option }));
}

async function ready() {
  await screen.findByRole('heading', { name: 'Solution outcomes' }, { timeout: 5000 });
}

describe('KnowledgeDashboardPage', () => {
  it('AC-005.1 shows the five dashboard sections', async () => {
    const requests = recordRequests();
    renderApp(paths.knowledgeDashboard, { role: 'ADMIN' });
    for (const name of [
      'Solution outcomes',
      'Knowledge coverage',
      'Reuse & feedback',
      'Knowledge gaps',
      'Suggestion results',
    ]) {
      expect(await screen.findByRole('heading', { name }, { timeout: 5000 })).toBeInTheDocument();
    }
    expect(requests).toHaveLength(1);
  });

  it.each([
    '',
    '&from=2026-01-01',
    '&from=2026-02-02&to=2026-02-01',
    '&from=2099-01-01&to=2099-01-02',
    '&from=2024-01-01&to=2025-01-01',
    '&from=2026-02-30&to=2026-03-01',
  ])('AC-005.3 blocks invalid custom dates %s without a request', async (dates) => {
    let requests = 0;
    server.use(
      http.get('*/api/v1/dashboard', () => {
        requests++;
        return problem(500);
      }),
    );
    renderApp(`${paths.knowledgeDashboard}?period=custom${dates}`, { role: 'ADMIN' });
    expect(await screen.findByText(msg('MSG39'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(requests).toBe(0);
  });

  it('AC-005.2 requests each preset once and records its dates in the URL', async () => {
    const requests = recordRequests();
    const { router } = renderApp(paths.knowledgeDashboard, { role: 'ADMIN' });
    await ready();
    expect(requests[0]?.searchParams.get('from')).toBe(presetRange('30d').from);
    expect(screen.getByText('Last 30 days')).toBeInTheDocument();
    for (const [period, label] of [
      ['7d', 'Last 7 days'],
      ['90d', 'Last 90 days'],
    ] as const) {
      await choose('Period', label);
      await ready();
      const params = new URLSearchParams(router.state.location.search);
      expect(params.get('period')).toBe(period);
      expect(params.get('from')).toBe(presetRange(period).from);
      expect(requests.at(-1)?.searchParams.get('from')).toBe(params.get('from'));
      expect(requests.at(-1)?.searchParams.get('to')).toBe(params.get('to'));
    }
    expect(requests).toHaveLength(3);
  });

  it('AC-005.3 sends a custom request only after both dates are entered', async () => {
    const user = userEvent.setup();
    const requests = recordRequests();
    const { router } = renderApp(paths.knowledgeDashboard, { role: 'ADMIN' });
    await ready();
    await choose('Period', 'Custom range');
    expect(await screen.findByText(msg('MSG39'))).toBeInTheDocument();
    const from = screen.getByRole('textbox', { name: 'From date' });
    await user.click(from);
    await user.paste('01/01/2026');
    await user.keyboard('{Enter}');
    expect(requests).toHaveLength(1);
    const to = screen.getByRole('textbox', { name: 'To date' });
    await user.click(to);
    await user.paste('07/01/2026');
    await user.keyboard('{Enter}');
    await ready();
    expect(requests).toHaveLength(2);
    expect(new URLSearchParams(router.state.location.search).get('from')).toBe('2026-01-01');
    expect(new URLSearchParams(router.state.location.search).get('to')).toBe('2026-01-07');
    expect(screen.queryByText(msg('MSG39'))).not.toBeInTheDocument();
  });

  it('AC-005.4 offers Admin Department options and clears the Project atomically on Department change', async () => {
    const requests = recordRequests();
    const { router } = renderApp(paths.knowledgeDashboard, { role: 'ADMIN' });
    await ready();
    expect(screen.getByText('All Departments')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Project' })).toBeDisabled();
    await choose('Department', 'IT');
    await ready();
    await choose('Project', 'ITSUP');
    await ready();
    expect(requests.at(-1)?.searchParams.get('projectId')).toBe(ITSUP);
    await choose('Department', 'HR');
    await ready();
    expect(requests).toHaveLength(4);
    expect(requests.at(-1)?.searchParams.get('departmentId')).toBe(HR);
    expect(requests.at(-1)?.searchParams.has('projectId')).toBe(false);
    expect(new URLSearchParams(router.state.location.search).has('projectId')).toBe(false);
    await userEvent.setup().click(screen.getByRole('combobox', { name: 'Project' }));
    expect(await screen.findByRole('option', { name: 'HRHELP' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'ITSUP' })).not.toBeInTheDocument();
  });

  it('AC-005.4 fixes the Project Manager Department and offers every Project in it', async () => {
    const requests = recordRequests();
    renderApp(paths.knowledgeDashboard, { role: 'PROJECT_MANAGER' });
    await ready();
    expect(screen.getByRole('combobox', { name: 'Department' })).toBeDisabled();
    expect(screen.getByText('IT')).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('combobox', { name: 'Project' }));
    expect(await screen.findByRole('option', { name: 'ITSUP' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'ITNET' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'HRHELP' })).not.toBeInTheDocument();
    expect(requests).toHaveLength(1);
    expect(requests[0]?.searchParams.get('departmentId')).toBe(IT);
  });

  it('AC-005.5 restores filters after Back and remounting the saved URL', async () => {
    const saved = `${paths.knowledgeDashboard}?period=custom&from=2026-01-01&to=2026-01-07&departmentId=${IT}&projectId=${ITSUP}`;
    const view = renderApp(saved, { role: 'ADMIN' });
    await ready();
    await choose('Department', 'HR');
    await ready();
    await act(() => view.router.navigate(-1));
    await ready();
    expect(view.router.state.location.search).toBe(saved.slice(saved.indexOf('?')));
    expect(screen.getByRole('textbox', { name: 'From date' })).toHaveValue('01/01/2026');
    expect(screen.getByRole('textbox', { name: 'To date' })).toHaveValue('07/01/2026');
    expect(screen.getByText('ITSUP')).toBeInTheDocument();
    view.unmount();
    renderApp(saved, { role: 'ADMIN' });
    await ready();
    expect(screen.getByText('Custom range')).toBeInTheDocument();
    expect(screen.getByText('IT')).toBeInTheDocument();
    expect(screen.getByText('ITSUP')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'To date' })).toHaveValue('07/01/2026');
  });

  it('AC-005.7 shows LoadingState while the dashboard request is pending', async () => {
    let finish = () => {};
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    let started = false;
    server.use(
      http.get('*/api/v1/dashboard', async () => {
        started = true;
        await pending;
        return problem(500, 'MSG06');
      }),
    );
    renderApp(paths.knowledgeDashboard, { role: 'ADMIN' });
    try {
      await waitFor(() => expect(started).toBe(true));
      expect(screen.getByText('Loading ...')).toBeInTheDocument();
    } finally {
      finish();
    }
    await screen.findByText(msg('MSG06'));
  });

  it('AC-005.7 retries MSG06 with the same filters and no automatic extra request', async () => {
    let failing = true;
    const requests: string[] = [];
    server.use(
      http.get('*/api/v1/dashboard', ({ request }) => {
        requests.push(request.url);
        return failing ? problem(500, 'MSG06') : undefined;
      }),
    );
    const saved = `${paths.knowledgeDashboard}?period=custom&from=2026-01-01&to=2026-01-07&departmentId=${IT}&projectId=${ITSUP}`;
    const { router } = renderApp(saved, { role: 'ADMIN' });
    await screen.findByText(msg('MSG06'), {}, { timeout: 5000 });
    expect(requests).toHaveLength(1);
    failing = false;
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }));
    await ready();
    expect(requests).toHaveLength(2);
    expect(requests[0]).toBe(requests[1]);
    expect(router.state.location.search).toBe(saved.slice(saved.indexOf('?')));
  });

  it('AC-005.11 explains the Department scope of gaps when a Project is selected', async () => {
    renderApp(`${paths.knowledgeDashboard}?departmentId=${IT}&projectId=${ITSUP}`, {
      role: 'ADMIN',
    });
    await ready();
    expect(screen.getByText('Figures count the whole Department.')).toBeInTheDocument();
  });
});
