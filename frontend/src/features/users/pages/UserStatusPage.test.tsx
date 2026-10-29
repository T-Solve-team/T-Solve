import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
import {
  getGetUserDetailQueryKey,
  getGetUsersQueryKey,
  getUserHandoverOptions,
  type UserDetail,
} from '@/shared/api';
import { setDevUser } from '@/shared/auth';

const account = (n: number) => `10000000-0000-4000-8000-${String(n).padStart(12, '0')}`;

describe('User Detail account status', () => {
  beforeAll(async () => {
    await import('./UserDetailPage');
  });

  it('AC-006.1 reactivates an existing account and refreshes detail', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderApp(`/users/${account(11)}`, { role: 'ADMIN' });
    queryClient.setQueryData(getGetUsersQueryKey({ q: 'cached' }), { content: [] });
    await user.click(await screen.findByRole('button', { name: 'Reactivate' }, { timeout: 5000 }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    expect(
      queryClient.getQueryData<UserDetail>(getGetUserDetailQueryKey(account(11)))?.status,
    ).toBe('ACTIVE');
    expect(await screen.findByRole('button', { name: 'Deactivate' })).toBeInTheDocument();
    expect(queryClient.getQueryState(getGetUsersQueryKey({ q: 'cached' }))?.isInvalidated).toBe(
      true,
    );
    expect(screen.getByText('user11@company.example')).toBeInTheDocument();
  });

  it('AC-006.2 confirms MSG57 before deactivating an account without responsibilities', async () => {
    const user = userEvent.setup();
    renderApp(`/users/${account(2)}`, { role: 'ADMIN' });
    await user.click(await screen.findByRole('button', { name: 'Deactivate' }, { timeout: 5000 }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(msg('MSG57', { user_name: 'Trần Thị Lan' }));
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));
    expect(await screen.findByRole('button', { name: 'Reactivate' })).toBeInTheDocument();
  });

  it('AC-006.4 disables self-deactivation with MSG112', async () => {
    const user = userEvent.setup();
    renderApp(`/users/${account(1)}`, { role: 'ADMIN' });
    const button = await screen.findByRole('button', { name: 'Deactivate' }, { timeout: 5000 });
    expect(button).toBeDisabled();
    await user.hover(button.parentElement ?? button);
    expect(await screen.findByRole('tooltip')).toHaveTextContent(msg('MSG112'));
  });

  it('AC-006.8 cancels deactivation without changing the confirmed status', async () => {
    const user = userEvent.setup();
    renderApp(`/users/${account(2)}`, { role: 'ADMIN' });
    await user.click(await screen.findByRole('button', { name: 'Deactivate' }, { timeout: 5000 }));
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }),
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Deactivate' })).toBeEnabled();
    expect(screen.queryByText(msg('MSG05'))).not.toBeInTheDocument();
  });

  it('AC-006.3 shows MSG56 and keeps the confirmation and status when rejected', async () => {
    server.use(http.put('*/api/v1/users/:userId/status', () => problem(409, 'MSG56')));
    const user = userEvent.setup();
    renderApp(`/users/${account(8)}`, { role: 'ADMIN' });
    await user.click(await screen.findByRole('button', { name: 'Deactivate' }, { timeout: 5000 }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'OK' }));
    expect(await screen.findByText(msg('MSG56'))).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG05'))).not.toBeInTheDocument();
  });

  it('AC-006.5 requires all Project recipients, keeps choices after failure and retries atomically', async () => {
    let fails = true;
    server.use(http.put('*/api/v1/users/:userId/status', () => (fails ? problem(500) : undefined)));
    const user = userEvent.setup();
    renderApp(`/users/${account(5)}`, { role: 'ADMIN' });
    await user.click(await screen.findByRole('button', { name: 'Deactivate' }, { timeout: 5000 }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(
      msg('MSG_NEW_2', {
        user_name: 'Ngô Thị Mai',
        responsibilities: 'Projects and Spaces',
        action: 'deactivation',
      }),
    );
    await within(dialog).findByRole('combobox', { name: 'IT Support' });
    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }));
    expect(await screen.findByText(msg('MSG01', { field_name: 'IT Support' }))).toBeInTheDocument();
    for (const name of ['IT Support', 'IT Network']) {
      await user.click(within(dialog).getByRole('combobox', { name }));
      await user.click(
        await screen.findByRole('option', { name: /Bùi Anh Tuấn/, selected: false }),
      );
    }
    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(within(dialog).getAllByText(/Bùi Anh Tuấn/).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('Active')).toBeInTheDocument();
    fails = false;
    server.resetHandlers();
    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }));
    expect(await screen.findByRole('button', { name: 'Reactivate' })).toBeInTheDocument();
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    expect(screen.getByText('Archived Support')).toBeInTheDocument();
  });

  it('AC-006.5 hands over a managed Space before deactivating a Knowledge Manager', async () => {
    const user = userEvent.setup();
    renderApp(`/users/${account(3)}`, { role: 'ADMIN' });
    await user.click(await screen.findByRole('button', { name: 'Deactivate' }, { timeout: 5000 }));
    const dialog = await screen.findByRole('dialog');
    await user.click(await within(dialog).findByRole('combobox', { name: 'IT Knowledge' }));
    await user.click(await screen.findByRole('option', { name: /Võ Thanh Tâm/ }));
    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }));
    expect(await screen.findByRole('button', { name: 'Reactivate' })).toBeInTheDocument();
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
  });

  it.each([5, 3])('AC-006.6 blocks account %s when no eligible replacement exists', async (n) => {
    setDevUser('admin');
    const options = await getUserHandoverOptions(account(n));
    let submissions = 0;
    server.use(
      http.get('*/api/v1/users/:userId/handover-options', () =>
        HttpResponse.json({
          projects: options.projects.map((item) => ({ ...item, recipients: [] })),
          spaces: options.spaces.map((item) => ({ ...item, recipients: [] })),
        }),
      ),
      http.put('*/api/v1/users/:userId/status', () => {
        submissions++;
        return problem(500);
      }),
    );
    const user = userEvent.setup();
    renderApp(`/users/${account(n)}`, { role: 'ADMIN' });
    await user.click(await screen.findByRole('button', { name: 'Deactivate' }, { timeout: 5000 }));
    const dialog = await screen.findByRole('dialog');
    const error = n === 5 ? msg('MSG59', { department_name: 'IT' }) : msg('MSG118');
    expect((await within(dialog).findAllByText(error)).length).toBeGreaterThan(0);
    expect(
      within(dialog)
        .getAllByRole('combobox')
        .every((field) => field.hasAttribute('disabled')),
    ).toBe(true);
    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }));
    expect(submissions).toBe(0);
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('AC-006.8 retries a failed detail load and retains status after cancelling handover', async () => {
    server.use(http.get('*/api/v1/users/:userId', () => problem(500)));
    const user = userEvent.setup();
    renderApp(`/users/${account(5)}`, { role: 'ADMIN' });
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG06'));
    server.resetHandlers();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    await user.click(await screen.findByRole('button', { name: 'Deactivate' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deactivate' })).toBeEnabled();
  });
});
