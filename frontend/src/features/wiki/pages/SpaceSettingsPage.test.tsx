import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { GrantWikiPermissionsRequest, WikiSpaceSettings } from '@/shared/api';
import {
  getGetWikiSpaceSettingsMockHandler,
  getListWikiPermissionCandidatesMockHandler,
} from '@/shared/api/generated/wiki/wiki.msw';
import { problem } from '@/shared/api/mocks';
import { formatDateTime } from '@/shared/lib';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';

const SPACE = '00000000-0000-4000-8000-0000000000a1';
const DEPARTMENT = '00000000-0000-4000-8000-0000000000d2';
const USER = '00000000-0000-4000-8000-000000000002';
const DATE = '2026-11-15T08:00:00Z';
const settings: WikiSpaceSettings = {
  spaceId: SPACE,
  name: 'IT Space',
  departmentName: 'IT',
  knowledgeManagers: ['Le Thu Huong', 'Nguyen Van An'],
  ownDepartment: { departmentId: '00000000-0000-4000-8000-0000000000d1', name: 'IT', locked: true },
  departments: [
    {
      permissionId: '00000000-0000-4000-8000-000000000501',
      departmentId: DEPARTMENT,
      name: 'HR',
      grantedBy: 'Le Thu Huong',
      grantedAt: DATE,
    },
  ],
  users: [
    {
      permissionId: '00000000-0000-4000-8000-000000000502',
      userId: USER,
      fullName: 'Tran Thi Lan',
      email: 'lan.tran@company.example',
      homeDepartment: null,
      active: false,
      grantedBy: 'Le Thu Huong',
      grantedAt: DATE,
    },
  ],
};
const candidates = {
  departments: [{ departmentId: DEPARTMENT, name: 'HR' }],
  users: [
    {
      userId: USER,
      fullName: 'Tran Thi Lan',
      email: 'lan.tran@company.example',
      homeDepartment: null,
    },
  ],
};

beforeEach(() => server.use(getGetWikiSpaceSettingsMockHandler(settings)));

async function openGrant() {
  const user = userEvent.setup();
  renderApp(paths.spaceSettings(SPACE), { role: 'KNOWLEDGE_MANAGER' });
  await user.click(await screen.findByRole('button', { name: 'Grant access' }, { timeout: 5000 }));
  const dialog = await screen.findByRole('dialog');
  return { user, dialog };
}

describe('SpaceSettingsPage permissions', () => {
  it('AC-006.11 joins every Knowledge Manager name with a comma', async () => {
    renderApp(paths.spaceSettings(SPACE), { role: 'KNOWLEDGE_MANAGER' });
    expect(await screen.findByText('Le Thu Huong, Nguyen Van An')).toBeInTheDocument();
  });

  it('AC-006.11 shows a dash in the header when there are no Knowledge Managers', async () => {
    server.use(getGetWikiSpaceSettingsMockHandler({ ...settings, knowledgeManagers: [] }));
    renderApp(paths.spaceSettings(SPACE), { role: 'KNOWLEDGE_MANAGER' });
    const label = await screen.findByText('Knowledge Manager');
    expect(label.closest('tr')).toHaveTextContent('Knowledge Manager—');
  });

  it('AC-006.12 AC-006.13 grants and revokes through stateful mocks and offers the revoked Department again (BUG-284)', async () => {
    server.resetHandlers();
    const { user, dialog } = await openGrant();
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled();
    await user.click(await within(dialog).findByRole('combobox', { name: 'Departments' }));
    expect(screen.queryByRole('option', { name: 'IT' })).not.toBeInTheDocument();
    await user.click(await screen.findByRole('option', { name: 'HR' }));
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    const row = await screen.findByRole('row', { name: /^HR/ });
    await user.click(within(row).getByRole('button', { name: 'Remove' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'OK' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.queryByRole('row', { name: /^HR/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Grant access' }));
    const reopened = await screen.findByRole('dialog');
    expect(within(reopened).getByRole('button', { name: 'Save' })).toBeDisabled();
    await user.click(await within(reopened).findByRole('combobox', { name: 'Departments' }));
    expect(await screen.findByRole('option', { name: 'HR' })).toBeInTheDocument();
  });

  it('AC-006.11 shows the Space header, required tables and locked own Department first with MSG107', async () => {
    const user = userEvent.setup();
    renderApp(paths.spaceSettings(SPACE), { role: 'KNOWLEDGE_MANAGER' });
    expect(
      await screen.findByRole('heading', { name: 'IT Space' }, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(screen.getByText('Knowledge Manager')).toBeInTheDocument();
    expect(screen.getAllByText('Le Thu Huong').length).toBeGreaterThan(0);
    expect(screen.getByRole('tab', { name: 'Permissions' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    const departments = within(screen.getByRole('table', { name: 'Departments' }));
    expect(departments.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
      'Department',
      'Granted by',
      'Date',
      'Remove',
    ]);
    const own = departments.getAllByRole('row')[1];
    if (!own) throw new Error('Missing own Department');
    expect(within(own).getByText('IT')).toBeInTheDocument();
    expect(within(own).queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument();
    expect(within(own).queryByText('Le Thu Huong')).not.toBeInTheDocument();
    await user.hover(within(own).getByRole('img', { name: 'Locked' }));
    expect(await screen.findByRole('tooltip')).toHaveTextContent(msg('MSG107'));
    const users = within(screen.getByRole('table', { name: 'Users' }));
    expect(users.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
      'Full name',
      'Email',
      'Home Department',
      'Granted by',
      'Date',
      'Remove',
    ]);
    const row = users.getByRole('row', { name: /Tran Thi Lan/ });
    expect(within(row).getByText('lan.tran@company.example')).toBeInTheDocument();
    expect(within(row).getByText(formatDateTime(DATE))).toBeInTheDocument();
    expect(within(row).getByText('—')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: 'Remove' })).toBeEnabled();
  });

  it('AC-006.11 shows MSG04 when there are no user permissions and keeps the own Department', async () => {
    server.use(getGetWikiSpaceSettingsMockHandler({ ...settings, users: [], departments: [] }));
    renderApp(paths.spaceSettings(SPACE), { role: 'KNOWLEDGE_MANAGER' });
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Departments' })).toHaveTextContent('IT');
    expect(screen.queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument();
  });

  it('AC-006.11 shows loading and an in-place settings error with retry', async () => {
    let release = () => {};
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.get('*/api/v1/wiki/spaces/:spaceId/settings', async () => {
        await pending;
        return problem(500);
      }),
    );
    const user = userEvent.setup();
    renderApp(paths.spaceSettings(SPACE), { role: 'KNOWLEDGE_MANAGER' });
    expect(await screen.findByText('Loading ...', {}, { timeout: 5000 })).toBeInTheDocument();
    release();
    expect(await screen.findByRole('alert')).toHaveTextContent(msg('MSG06'));
    server.use(getGetWikiSpaceSettingsMockHandler(settings));
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('heading', { name: 'IT Space' })).toBeInTheDocument();
  });

  it('AC-006.12 selects Departments and users, sends both id lists once, shows MSG05 and refreshes', async () => {
    server.use(
      getGetWikiSpaceSettingsMockHandler({ ...settings, departments: [], users: [] }),
      getListWikiPermissionCandidatesMockHandler(candidates),
    );
    const save = vi.fn<(body: GrantWikiPermissionsRequest) => void>();
    let release = () => {};
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.post('*/api/v1/wiki/spaces/:spaceId/permissions', async ({ request }) => {
        save((await request.json()) as GrantWikiPermissionsRequest);
        await pending;
        server.use(
          getGetWikiSpaceSettingsMockHandler(settings),
          getListWikiPermissionCandidatesMockHandler({ departments: [], users: [] }),
        );
        return new HttpResponse(null, { status: 201 });
      }),
    );
    const { user, dialog } = await openGrant();
    await user.click(await within(dialog).findByRole('combobox', { name: 'Departments' }));
    await user.click(await screen.findByRole('option', { name: 'HR' }));
    await user.click(within(dialog).getByRole('combobox', { name: 'Users' }));
    await user.click(await screen.findByRole('option', { name: /Tran Thi Lan/ }));
    await user.dblClick(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(save).toHaveBeenCalledExactlyOnceWith({
        departmentIds: [DEPARTMENT],
        userIds: [USER],
      }),
    );
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled();
    release();
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('table', { name: 'Users' })).toHaveTextContent('Tran Thi Lan');
    await user.click(screen.getByRole('button', { name: 'Grant access' }));
    expect(
      await within(await screen.findByRole('dialog')).findByText(msg('MSG04')),
    ).toBeInTheDocument();
  });

  it('AC-006.12 shows MSG04 without candidates and disables Save', async () => {
    server.use(getListWikiPermissionCandidatesMockHandler({ departments: [], users: [] }));
    const { dialog } = await openGrant();
    expect(await within(dialog).findByText(msg('MSG04'))).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  it('AC-006.12 keeps selections after a failed grant and Cancel sends no further request', async () => {
    server.use(getListWikiPermissionCandidatesMockHandler(candidates));
    const save = vi.fn(() => problem(404, 'MSG04'));
    server.use(http.post('*/api/v1/wiki/spaces/:spaceId/permissions', save));
    const { user, dialog } = await openGrant();
    await user.click(await within(dialog).findByRole('combobox', { name: 'Departments' }));
    await user.click(await screen.findByRole('option', { name: 'HR' }));
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG04'))).toBeInTheDocument();
    expect(within(dialog).getByText('HR')).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG05'))).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(save).toHaveBeenCalledTimes(1);
  });

  it('AC-006.12 shows a candidate query error and retries in the modal', async () => {
    server.use(http.get('*/api/v1/wiki/spaces/:spaceId/permission-candidates', () => problem(500)));
    const { user, dialog } = await openGrant();
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(msg('MSG06'));
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled();
    server.use(getListWikiPermissionCandidatesMockHandler(candidates));
    await user.click(within(dialog).getByRole('button', { name: 'Try again' }));
    expect(
      await within(dialog).findByRole('combobox', { name: 'Departments' }),
    ).toBeInTheDocument();
  });

  it.each(['HR', 'Tran Thi Lan'])(
    'AC-006.13 confirms Remove for %s with MSG106, cancels safely, then refreshes with MSG05',
    async (name) => {
      const remove = vi.fn<(id: string) => void>();
      server.use(
        http.delete('*/api/v1/wiki/permissions/:permissionId', ({ params }) => {
          remove(String(params.permissionId));
          server.use(
            getGetWikiSpaceSettingsMockHandler({ ...settings, departments: [], users: [] }),
          );
          return new HttpResponse(null, { status: 204 });
        }),
      );
      const user = userEvent.setup();
      renderApp(paths.spaceSettings(SPACE), { role: 'KNOWLEDGE_MANAGER' });
      const row = await screen.findByRole('row', { name: new RegExp(name) }, { timeout: 5000 });
      await user.click(within(row).getByRole('button', { name: 'Remove' }));
      let dialog = await screen.findByRole('dialog');
      expect(dialog).toHaveTextContent(msg('MSG106', { grantee: name, space_name: 'IT Space' }));
      expect(remove).not.toHaveBeenCalled();
      await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      await user.click(within(row).getByRole('button', { name: 'Remove' }));
      dialog = await screen.findByRole('dialog');
      await user.click(within(dialog).getByRole('button', { name: 'OK' }));
      expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(remove).toHaveBeenCalledExactlyOnceWith(
        name === 'HR' ? settings.departments[0]?.permissionId : settings.users[0]?.permissionId,
      );
      expect(screen.queryByRole('row', { name: new RegExp(name) })).not.toBeInTheDocument();
    },
  );

  it('AC-006.13 keeps the permission and confirmation after a failed revoke', async () => {
    server.use(http.delete('*/api/v1/wiki/permissions/:permissionId', () => problem(403, 'MSG08')));
    const user = userEvent.setup();
    renderApp(paths.spaceSettings(SPACE), { role: 'KNOWLEDGE_MANAGER' });
    const row = await screen.findByRole('row', { name: /HR/ }, { timeout: 5000 });
    await user.click(within(row).getByRole('button', { name: 'Remove' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));
    expect(await screen.findByText(msg('MSG08'))).toBeInTheDocument();
    expect(row).toBeInTheDocument();
    expect(dialog).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG05'))).not.toBeInTheDocument();
  });

  it.each(['EMPLOYEE', 'KNOWLEDGE_MANAGER'] as const)(
    'AC-006.14 shows MSG08 for a non-manager with role %s',
    async (role) => {
      const read = vi.fn(() => settings);
      server.use(getGetWikiSpaceSettingsMockHandler(read));
      const view = renderApp(paths.spaceSettings('00000000-0000-4000-8000-0000000000a2'), { role });
      expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
      await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
      expect(read).not.toHaveBeenCalled();
      expect(screen.queryByRole('button', { name: 'Grant access' })).not.toBeInTheDocument();
    },
  );

  it('AC-006.14 honors a server denial even when the Space list still calls the user a manager', async () => {
    server.use(http.get('*/api/v1/wiki/spaces/:spaceId/settings', () => problem(403, 'MSG08')));
    renderApp(paths.spaceSettings(SPACE), { role: 'KNOWLEDGE_MANAGER' });
    expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Grant access' })).not.toBeInTheDocument();
  });
});
