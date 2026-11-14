import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';
import { initialMembers, ITNET_ID, ITSUP_ID } from '../mocks/data';

const UNKNOWN_ID = '00000000-0000-4000-8000-000000000999';

/** Records the path of every remove request of this test. */
function recordRemoves() {
  const removes: string[] = [];
  server.events.on('request:start', ({ request }) => {
    if (request.method === 'DELETE') removes.push(new URL(request.url).pathname);
  });
  return removes;
}

/** Serves the members of `projectId` with one more MEMBER, Lan without her full name. */
function withNamelessMember(projectId: string) {
  const [manager] = initialMembers()[projectId] ?? [];
  const lan = initialMembers()[ITSUP_ID]?.[1];
  server.use(
    http.get('*/api/v1/projects/:projectId/members', () =>
      HttpResponse.json([manager, { ...lan, fullName: null }]),
    ),
  );
}

async function clickRemove(name = 'Trần Thị Lan') {
  const user = userEvent.setup();
  const table = await openMembers();
  await user.click(within(table).getByRole('button', { name: `Remove ${name}` }));
  const dialog = await screen.findByRole('dialog');
  return { user, table, dialog };
}

async function openMembers(projectId = ITSUP_ID) {
  renderApp(paths.projectDetail(projectId), { role: 'PROJECT_MANAGER' });
  return screen.findByRole('table', { name: 'Members' }, { timeout: 5000 });
}

describe('ProjectDetailPage', () => {
  it('AC-001.5 shows the header and the Members tab with Added on as a date in the app time zone', async () => {
    const table = await openMembers();
    expect(screen.getByText('ITSUP')).toBeInTheDocument();
    expect(screen.getByText('IT')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    const managerLabel = screen.getByText('Project Manager', {
      selector: '.ant-descriptions-item-label',
    });
    expect(managerLabel.closest('.ant-descriptions-item')).toHaveTextContent('Đỗ Minh Quân');
    expect(screen.getByRole('tab', { name: 'Members' })).toBeInTheDocument();
    const headers = within(table)
      .getAllByRole('columnheader')
      .map((header) => header.textContent);
    expect(headers).toEqual(['Full name', 'Email', 'Role', 'Added on', '']);
    const lan = within(table).getByRole('row', { name: /Trần Thị Lan/ });
    // 2026-10-01T18:30:00Z is 2 October in Vietnam (UTC+7).
    expect(within(lan).getByText('02/10/2026')).toBeInTheDocument();
    expect(within(lan).getByText('lan.tran@company.example')).toBeInTheDocument();
    expect(within(lan).getByText('Member')).toBeInTheDocument();
    const manager = within(table).getByRole('row', { name: /quan.do@company.example/ });
    expect(within(manager).getByText('Project Manager')).toBeInTheDocument();
  });

  it('AC-001.6 shows MSG08 in place of the screen when the backend answers 403', async () => {
    server.use(http.get('*/api/v1/projects/:projectId', () => problem(403, 'MSG08')));
    renderApp(paths.projectDetail(ITSUP_ID), { role: 'PROJECT_MANAGER' });
    expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Members' })).not.toBeInTheDocument();
  });

  it('AC-001.6 shows MSG04 when the Project does not exist (404)', async () => {
    renderApp(paths.projectDetail(UNKNOWN_ID), { role: 'PROJECT_MANAGER' });
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Members' })).not.toBeInTheDocument();
  });

  it('AC-001.6 shows MSG06 when the request fails otherwise', async () => {
    server.use(http.get('*/api/v1/projects/:projectId/members', () => problem(500)));
    renderApp(paths.projectDetail(ITSUP_ID), { role: 'PROJECT_MANAGER' });
    expect(await screen.findByText(msg('MSG06'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Members' })).not.toBeInTheDocument();
  });

  afterEach(() => server.events.removeAllListeners());

  it("AC-001.7 shows no Remove button on the Project Manager's row", async () => {
    const table = await openMembers();
    const manager = within(table).getByRole('row', { name: /quan.do@company.example/ });
    expect(within(manager).queryByRole('button')).not.toBeInTheDocument();
  });

  it('AC-003.6 shows a Remove button on every MEMBER row while the Project is Active', async () => {
    const table = await openMembers();
    const lan = within(table).getByRole('row', { name: /Trần Thị Lan/ });
    expect(within(lan).getByRole('button', { name: 'Remove Trần Thị Lan' })).toBeInTheDocument();
  });

  it('AC-003.6 shows no Remove button while the Project is Removed', async () => {
    withNamelessMember(ITNET_ID);
    const table = await openMembers(ITNET_ID);
    expect(
      within(table).getByRole('row', { name: /lan.tran@company.example/ }),
    ).toBeInTheDocument();
    expect(within(table).queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument();
  });

  it('AC-003.7 asks MSG55 with the full name and the Jira key and sends no request yet', async () => {
    const removes = recordRemoves();
    const { dialog } = await clickRemove();
    expect(dialog).toHaveTextContent(
      msg('MSG55', { user_name: 'Trần Thị Lan', project_key: 'ITSUP' }),
    );
    expect(removes).toEqual([]);
  });

  it('AC-003.7 uses the email in MSG55 when the member has no full name', async () => {
    withNamelessMember(ITSUP_ID);
    const { dialog } = await clickRemove('lan.tran@company.example');
    expect(dialog).toHaveTextContent(
      msg('MSG55', { user_name: 'lan.tran@company.example', project_key: 'ITSUP' }),
    );
  });

  it('AC-003.8 shows MSG05 and the returned member list after the confirm and a 200', async () => {
    const removes = recordRemoves();
    const { user, table, dialog } = await clickRemove();
    await user.click(within(dialog).getByRole('button', { name: 'Remove' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await waitFor(() =>
      expect(within(table).queryByRole('row', { name: /Trần Thị Lan/ })).not.toBeInTheDocument(),
    );
    expect(removes).toEqual([
      `/api/v1/projects/${ITSUP_ID}/members/00000000-0000-4000-8000-000000000002`,
    ]);
  });

  it('AC-003.8 a removed member is offered again in the Add member popup', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderApp(paths.projectDetail(ITSUP_ID), { role: 'PROJECT_MANAGER' });
    // The test client has no staleTime; use the app's 30 s so the reopened popup would reuse a stale cache.
    queryClient.setDefaultOptions({ queries: { retry: false, staleTime: 30_000 } });
    const table = await screen.findByRole('table', { name: 'Members' }, { timeout: 5000 });
    await user.click(screen.getByRole('button', { name: 'Add member' }));
    const popup = await screen.findByRole('dialog', { name: 'Add member' });
    await within(popup).findByRole('row', { name: /Phạm Quốc Bảo/ });
    expect(within(popup).queryByRole('row', { name: /Trần Thị Lan/ })).not.toBeInTheDocument();
    await user.click(within(popup).getByRole('button', { name: 'Cancel' }));
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'Add member' })).not.toBeInTheDocument(),
    );

    await user.click(within(table).getByRole('button', { name: 'Remove Trần Thị Lan' }));
    const confirm = await screen.findByRole('dialog');
    await user.click(within(confirm).getByRole('button', { name: 'Remove' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Add member' }));
    // The closed confirm dialog stays mounted and shares the dialog name id, so find the popup by its table.
    const candidates = await screen.findByRole('table', { name: 'Member candidates' });
    expect(
      await within(candidates).findByRole('row', { name: /Trần Thị Lan/ }, { timeout: 3000 }),
    ).toBeInTheDocument();
  });

  it('AC-003.9 shows MSG06 and keeps the member list when the remove fails with 409', async () => {
    server.use(
      http.delete('*/api/v1/projects/:projectId/members/:userId', () => problem(409, 'MSG06')),
    );
    const { user, table, dialog } = await clickRemove();
    await user.click(within(dialog).getByRole('button', { name: 'Remove' }));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(within(table).getByRole('row', { name: /Trần Thị Lan/ })).toBeInTheDocument();
  });

  it('AC-003.10 sends no remove request and changes nothing on Cancel', async () => {
    const removes = recordRemoves();
    const { user, table, dialog } = await clickRemove();
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(removes).toEqual([]);
    expect(within(table).getByRole('row', { name: /Trần Thị Lan/ })).toBeInTheDocument();
  });

  it('AC-002.9 shows "Add member" while the Project is Active', async () => {
    await openMembers();
    expect(screen.getByRole('button', { name: 'Add member' })).toBeInTheDocument();
  });

  it('AC-002.9 hides "Add member" while the Project is Removed', async () => {
    await openMembers(ITNET_ID);
    expect(screen.getByText('Removed')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add member' })).not.toBeInTheDocument();
  });
});
