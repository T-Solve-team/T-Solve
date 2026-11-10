import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import dayjs from 'dayjs';
import { http } from 'msw';
import { server } from '@/mocks/server';
import type { JiraPullRequest } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

const ITSUP_ID = '00000000-0000-4000-8000-000000000101';

/** dd/MM/yyyy as typed into the picker, and yyyy-MM-dd as sent; relative to today so none is in the future. */
function daysAgo(days: number) {
  const date = dayjs().subtract(days, 'day');
  return { typed: date.format('DD/MM/YYYY'), sent: date.format('YYYY-MM-DD') };
}

/** Import Tickets on the Jira tab, as the mock Project Manager. */
async function openJiraTab() {
  const user = userEvent.setup();
  const view = renderApp('/import', { role: 'PROJECT_MANAGER' });
  await user.click(await screen.findByRole('tab', { name: 'Jira' }, { timeout: 5000 }));
  await screen.findByRole('button', { name: 'Pull from Jira' }, { timeout: 5000 });
  return { user, ...view };
}

async function fill(
  user: ReturnType<typeof userEvent.setup>,
  { from, to }: { from?: string; to?: string },
) {
  // Enter confirms a typed date and submits the form, so the Project comes last
  if (from) await user.type(screen.getByLabelText('Resolved from'), `${from}{Enter}`);
  if (to) await user.type(screen.getByLabelText('Resolved to'), `${to}{Enter}`);
  await user.click(screen.getByRole('combobox', { name: 'Project' }));
  await user.click(await screen.findByTitle('ITSUP · IT'));
}

/** The pull requests that reach the mock backend, with their bodies. */
function capturePulls() {
  const bodies: JiraPullRequest[] = [];
  server.events.on('request:start', ({ request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname === '/api/v1/imports/jira') {
      void request
        .clone()
        .json()
        .then((body: JiraPullRequest) => bodies.push(body));
    }
  });
  return bodies;
}

describe('JiraPullForm', () => {
  beforeAll(async () => {
    await import('../pages/ImportTicketsPage');
  });

  afterEach(() => {
    server.events.removeAllListeners();
  });

  it('AC-001.1 sends the Project and the resolved dates, then opens the Import Preview', async () => {
    const pulls = capturePulls();
    const { user, router } = await openJiraTab();
    const from = daysAgo(10);
    const to = daysAgo(1);
    await fill(user, { from: from.typed, to: to.typed });

    await user.click(screen.getByRole('button', { name: 'Pull from Jira' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/import/preview'));
    expect(router.state.location.search).toMatch(/^\?previewId=[1-9]\d*$/);
    expect(pulls).toEqual([{ projectId: ITSUP_ID, resolvedFrom: from.sent, resolvedTo: to.sent }]);
  });

  it('AC-001.4 shows MSG01 at both dates and sends nothing when they are missing', async () => {
    const pulls = capturePulls();
    const { user } = await openJiraTab();
    await fill(user, {});

    await user.click(screen.getByRole('button', { name: 'Pull from Jira' }));

    expect(
      await screen.findByText(msg('MSG01', { field_name: 'Resolved from' })),
    ).toBeInTheDocument();
    expect(screen.getByText(msg('MSG01', { field_name: 'Resolved to' }))).toBeInTheDocument();
    expect(pulls).toHaveLength(0);
  });

  it('AC-001.5 shows MSG39 and sends nothing when Resolved from is after Resolved to', async () => {
    const pulls = capturePulls();
    const { user } = await openJiraTab();
    await fill(user, { from: daysAgo(1).typed, to: daysAgo(10).typed });

    await user.click(screen.getByRole('button', { name: 'Pull from Jira' }));

    expect(await screen.findByText(msg('MSG39'))).toBeInTheDocument();
    expect(pulls).toHaveLength(0);
  });

  it.each([
    [404, 'MSG04', {}],
    [502, 'MSG50', { error_message: 'HTTP 401' }],
    [403, 'MSG08', {}],
  ] as const)(
    'AC-001.6 AC-001.7 AC-001.8 shows %s %s above the form and stays on Import Tickets',
    async (status, code, params) => {
      server.use(http.post('*/api/v1/imports/jira', () => problem(status, code, { params })));
      const { user, router } = await openJiraTab();
      await fill(user, { from: daysAgo(10).typed, to: daysAgo(1).typed });

      await user.click(screen.getByRole('button', { name: 'Pull from Jira' }));

      expect(await screen.findByRole('alert')).toHaveTextContent(
        msg(code, { tracker_name: 'Jira', ...params }),
      );
      expect(router.state.location.pathname).toBe('/import');
    },
  );
});
