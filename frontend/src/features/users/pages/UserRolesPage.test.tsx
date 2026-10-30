import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { MOCK_USERS } from '@/mocks/session';
import {
  getGetUserDetailQueryKey,
  getGetUsersQueryKey,
  getUserDetail,
  updateUserRoles,
  type UserDetail,
} from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { setDevUser } from '@/shared/auth';
import { HANDBOOK_SPACE_ID } from '../mocks/data';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

const account = (n: number) => `10000000-0000-4000-8000-${String(n).padStart(12, '0')}`;

describe('User Detail roles and assignments', () => {
  beforeAll(async () => {
    await import('./UserDetailPage');
  });

  it('AC-007.1 loads confirmed roles, Department and managed Spaces into the edit form', async () => {
    renderApp(`/users/${account(3)}`, { role: 'ADMIN' });
    expect(
      await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Knowledge Manager' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Employee' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Employee' })).toBeDisabled();
    expect(screen.getByRole('combobox', { name: 'Department' })).toBeInTheDocument();
    expect(screen.getAllByText('IT Knowledge').length).toBeGreaterThan(0);
    expect(screen.getByRole('combobox', { name: 'Spaces' })).toBeInTheDocument();
  });

  it('AC-007.2 requires a Department for PM and saves valid UUID assignments retaining Employee', async () => {
    const user = userEvent.setup();
    renderApp(`/users/${account(2)}`, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    const form = within(screen.getByRole('form', { name: 'User roles' }));
    await user.click(form.getByRole('checkbox', { name: 'Project Manager' }));
    await user.click(form.getByRole('button', { name: 'Clear' }));
    await user.click(form.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG01', { field_name: 'Department' }))).toBeInTheDocument();
    await user.click(form.getByRole('combobox', { name: 'Department' }));
    await user.click(await screen.findByRole('option', { name: 'IT' }));
    await user.click(form.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Project Manager' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Employee' })).toBeChecked();
    expect((await getUserDetail(account(2))).department?.departmentId).toBe(
      '00000000-0000-4000-8000-0000000000d1',
    );
  });

  it('AC-007.3 / AC-008.2 hands over old-Department Projects before a Department change', async () => {
    const user = userEvent.setup();
    renderApp(`/users/${account(5)}`, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    const form = within(screen.getByRole('form', { name: 'User roles' }));
    await user.click(form.getByRole('combobox', { name: 'Department' }));
    await user.click(await screen.findByRole('option', { name: 'HR' }));
    await user.click(form.getByRole('button', { name: 'Save' }));
    const dialog = await screen.findByRole('dialog', { name: 'Hand Over Responsibilities' });
    for (const name of ['IT Support', 'IT Network']) {
      await user.click(await within(dialog).findByRole('combobox', { name }));
      await user.click(
        await screen.findByRole('option', { name: /Bùi Anh Tuấn/, selected: false }),
      );
    }
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    const after = await getUserDetail(account(5));
    expect(after.department?.name).toBe('HR');
    expect(new Set(after.roles)).toEqual(new Set(['EMPLOYEE', 'PROJECT_MANAGER']));
    expect(
      after.projects
        .filter((project) => project.isActive)
        .every((project) => project.managerId === account(9)),
    ).toBe(true);
  });

  it('AC-007.4 explains the disabled own Admin role without hover (BUG-036)', async () => {
    renderApp(`/users/${account(1)}`, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    const form = within(screen.getByRole('form', { name: 'User roles' }));
    expect(form.getByRole('checkbox', { name: 'Admin' })).toBeChecked();
    expect(form.getByRole('checkbox', { name: 'Admin' })).toBeDisabled();
    expect(form.getByRole('checkbox', { name: 'Admin' })).toHaveAccessibleDescription(
      msg('MSG113'),
    );
    expect(screen.getByText(msg('MSG113'))).toBeVisible();
  });

  it('AC-007.5 confirms MSG78, preserves unsaved roles on cancel, then removes only current memberships', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderApp(`/users/${account(4)}`, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    await user.click(screen.getByRole('checkbox', { name: 'Ticket Resolver' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    let dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(msg('MSG78', { user_name: 'Phạm Quốc Bảo', count: 4 }));
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(screen.getByRole('checkbox', { name: 'Ticket Resolver' })).not.toBeChecked();
    expect(
      queryClient.getQueryData<UserDetail>(getGetUserDetailQueryKey(account(4)))?.roles,
    ).toContain('TICKET_RESOLVER');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    const after = await getUserDetail(account(4));
    expect(after.roles).not.toContain('TICKET_RESOLVER');
    expect(after.ticketResolverProjectIds).toEqual([]);
    expect(after.projects.map((project) => project.name)).toContain('IT Support');
  });

  it('AC-007.6 refreshes session and invalidates cached lists after saving (BUG-033)', async () => {
    let sessionReads = 0;
    server.use(
      http.get('*/api/v1/me', () => {
        sessionReads++;
        return HttpResponse.json(MOCK_USERS.admin);
      }),
    );
    const user = userEvent.setup();
    const { queryClient } = renderApp(`/users/${account(2)}`, { role: 'ADMIN' });
    queryClient.setQueryData(getGetUsersQueryKey({ q: 'cached' }), { content: [] });
    await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    await user.click(screen.getByRole('checkbox', { name: 'Admin' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    expect(queryClient.getQueryState(getGetUsersQueryKey({ q: 'cached' }))?.isInvalidated).toBe(
      true,
    );
    expect(sessionReads).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole('checkbox', { name: 'Admin' })).toBeChecked();
    // Saving remounts the form and refetches its assignment options after the success toast.
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });

  it('AC-007.7 Cancel restores the current roles and Department after a background refetch (BUG-037)', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderApp(`/users/${account(2)}`, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    const latest: UserDetail = {
      ...(await getUserDetail(account(2))),
      roles: ['EMPLOYEE', 'TICKET_RESOLVER'],
      department: { departmentId: '00000000-0000-4000-8000-0000000000d2', name: 'HR' },
    };
    server.use(http.get(`*/api/v1/users/${account(2)}`, () => HttpResponse.json(latest)));
    await act(async () => {
      await queryClient.refetchQueries({ queryKey: getGetUserDetailQueryKey(account(2)) });
    });
    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: 'Ticket Resolver' })).toBeChecked(),
    );
    await user.click(screen.getByRole('checkbox', { name: 'Admin' }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByRole('checkbox', { name: 'Admin' })).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Ticket Resolver' })).toBeChecked();
    expect(screen.getByText('HR', { exact: true })).toBeVisible();
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });

  it('AC-007.3 hands over a Space successfully when removing Knowledge Manager', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderApp(`/users/${account(3)}`, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    await user.click(screen.getByRole('checkbox', { name: 'Knowledge Manager' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    const dialog = await screen.findByRole('dialog', { name: 'Hand Over Responsibilities' });
    await user.click(await within(dialog).findByRole('combobox', { name: 'IT Knowledge' }));
    await user.click(await screen.findByRole('option', { name: /Võ Thanh Tâm/ }));
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    const after = await getUserDetail(account(3));
    expect(after.roles).not.toContain('KNOWLEDGE_MANAGER');
    expect(after.managedSpaces).toEqual([]);
    expect((await getUserDetail(account(7))).managedSpaces.map((space) => space.name)).toContain(
      'IT Knowledge',
    );
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });

  it('AC-007.5 confirms MSG78 before handing over a Space and removing current memberships', async () => {
    setDevUser('admin');
    await updateUserRoles(account(4), {
      roles: ['EMPLOYEE', 'TICKET_RESOLVER', 'KNOWLEDGE_MANAGER'],
      departmentId: null,
      spaceIds: [HANDBOOK_SPACE_ID],
    });
    const user = userEvent.setup();
    const { queryClient } = renderApp(`/users/${account(4)}`, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    await user.click(screen.getByRole('checkbox', { name: 'Ticket Resolver' }));
    await user.click(screen.getByRole('checkbox', { name: 'Knowledge Manager' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    const confirmation = await screen.findByRole('dialog');
    expect(confirmation).toHaveTextContent(msg('MSG78', { user_name: 'Phạm Quốc Bảo', count: 4 }));
    await user.click(within(confirmation).getByRole('button', { name: 'OK' }));
    const handover = await screen.findByRole('dialog', { name: 'Hand Over Responsibilities' });
    await user.click(
      await within(handover).findByRole('combobox', { name: 'Engineering Handbook' }),
    );
    await user.click(await screen.findByRole('option', { name: /Võ Thanh Tâm/ }));
    await user.click(within(handover).getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    const after = await getUserDetail(account(4));
    expect(after.roles).toEqual(['EMPLOYEE']);
    expect(after.ticketResolverProjectIds).toEqual([]);
    expect(after.managedSpaces).toEqual([]);
    expect(after.projects.map((project) => project.name)).toContain('IT Support');
    expect((await getUserDetail(account(7))).managedSpaces.map((space) => space.spaceId)).toContain(
      HANDBOOK_SPACE_ID,
    );
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });

  it('AC-007.7 displays assignment field errors in handover without hiding the error or choices (BUG-026)', async () => {
    server.use(
      http.put('*/api/v1/users/:userId', () =>
        problem(409, 'MSG117', {
          params: { space_name: 'Engineering Handbook' },
          fieldErrors: [
            { field: 'spaceIds', code: 'MSG117', params: { space_name: 'Engineering Handbook' } },
          ],
        }),
      ),
    );
    const user = userEvent.setup();
    renderApp(`/users/${account(3)}`, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    await user.click(screen.getByRole('checkbox', { name: 'Knowledge Manager' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    const dialog = await screen.findByRole('dialog', { name: 'Hand Over Responsibilities' });
    await user.click(await within(dialog).findByRole('combobox', { name: 'IT Knowledge' }));
    await user.click(await screen.findByRole('option', { name: /Võ Thanh Tâm/ }));
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(
      await screen.findByText(msg('MSG117', { space_name: 'Engineering Handbook' })),
    ).toBeInTheDocument();
    expect(within(dialog).getByText(/Võ Thanh Tâm/)).toBeInTheDocument();
    expect((await getUserDetail(account(3))).roles).toContain('KNOWLEDGE_MANAGER');
  });

  it('AC-007.7 disables role controls and sends one update while saving (BUG-028)', async () => {
    let finish: (() => void) | undefined;
    let calls = 0;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    server.use(
      http.put('*/api/v1/users/:userId', async () => {
        calls++;
        await pending;
        return problem(500);
      }),
    );
    const user = userEvent.setup();
    renderApp(`/users/${account(2)}`, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    const admin = screen.getByRole('checkbox', { name: 'Admin' });
    await user.click(admin);
    const save = screen.getByRole('button', { name: 'Save' });
    try {
      await user.dblClick(save);
      await waitFor(() => expect(save).toBeDisabled());
      expect(calls).toBe(1);
      expect(save).toHaveAccessibleName('Save');
      expect(save).toHaveAttribute('aria-busy', 'true');
      expect(admin).toBeDisabled();
    } finally {
      finish?.();
    }
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    await waitFor(() => expect(save).toBeEnabled());
    expect(admin).toBeChecked();
  });

  it('AC-007.7 retains edits on a failed save, permits retry and resets only on Cancel', async () => {
    server.use(http.put('*/api/v1/users/:userId', () => problem(500)));
    const user = userEvent.setup();
    renderApp(`/users/${account(2)}`, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    await user.click(screen.getByRole('checkbox', { name: 'Admin' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Admin' })).toBeChecked();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Admin' })).not.toBeChecked());
  });
});
