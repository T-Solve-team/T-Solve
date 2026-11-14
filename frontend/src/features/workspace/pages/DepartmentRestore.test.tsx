import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { deleteDepartment, getGetUsersQueryKey, type DepartmentRef } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

// 2.2 "Show deleted" and Restore, UC-15 (KAN-123, STORY-008).
const HR_ID = '00000000-0000-4000-8000-0000000000d2';
const RESTORE_URL = `*/api/v1/departments/${HR_ID}/restore`;
const RESTORE_TEXT = msg('MSG51', { department_name: 'HR' });

type User = ReturnType<typeof userEvent.setup>;

function recordRestores() {
  const requests: Request[] = [];
  const onStart = ({ request }: { request: Request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname.endsWith('/restore'))
      requests.push(request);
  };
  server.events.on('request:start', onStart);
  onTestFinished(() => server.events.removeListener('request:start', onStart));
  return requests;
}

async function openWithHrDeleted(user: User) {
  await deleteDepartment(HR_ID);
  const rendered = renderApp('/workspace-settings', { role: 'ADMIN' });
  await screen.findByRole('table', { name: 'Departments' }, { timeout: 5000 });
  await user.click(screen.getByRole('switch', { name: 'Show deleted' }));
  const table = screen.getByRole('table', { name: 'Departments' });
  await within(table).findByRole('button', { name: 'Restore' });
  return { ...rendered, table };
}

async function openPopup(user: User) {
  await user.click(screen.getByRole('button', { name: 'Restore' }));
  return screen.findByRole('dialog');
}

describe('Show deleted and Restore on 2.2', () => {
  it('AC-008.1 shows the Show deleted switch, turned off', async () => {
    renderApp('/workspace-settings', { role: 'ADMIN' });
    const toggle = await screen.findByRole('switch', { name: 'Show deleted' }, { timeout: 5000 });
    expect(toggle).not.toBeChecked();
  });

  it('AC-008.2 lists Deleted Departments only while the switch is on', async () => {
    const user = userEvent.setup();
    await deleteDepartment(HR_ID);
    renderApp('/workspace-settings', { role: 'ADMIN' });
    const table = await screen.findByRole('table', { name: 'Departments' }, { timeout: 5000 });
    await within(table).findByRole('link', { name: 'IT' });
    expect(within(table).queryByText('HR')).not.toBeInTheDocument();
    await user.click(screen.getByRole('switch', { name: 'Show deleted' }));
    expect(await within(table).findByText('HR')).toBeInTheDocument();
    await user.click(screen.getByRole('switch', { name: 'Show deleted' }));
    await waitFor(() => expect(within(table).queryByText('HR')).not.toBeInTheDocument());
  });

  it('AC-008.3 shows a Deleted tag, Restore and a plain name on Deleted rows only', async () => {
    const user = userEvent.setup();
    const { table } = await openWithHrDeleted(user);
    const hrRow = within(table).getByRole('row', { name: /HR/ });
    expect(within(hrRow).getByText('Deleted')).toBeInTheDocument();
    expect(within(hrRow).getByRole('button', { name: 'Restore' })).toBeInTheDocument();
    expect(within(hrRow).queryByRole('link')).not.toBeInTheDocument();
    const itRow = within(table).getByRole('row', { name: /IT/ });
    expect(within(itRow).getByRole('link', { name: 'IT' })).toBeInTheDocument();
    expect(within(itRow).queryByRole('button', { name: 'Restore' })).not.toBeInTheDocument();
  });

  it('AC-008.4 opens the popup with MSG51, Restore and Cancel', async () => {
    const user = userEvent.setup();
    await openWithHrDeleted(user);
    const popup = await openPopup(user);
    expect(within(popup).getByText(RESTORE_TEXT)).toBeInTheDocument();
    expect(within(popup).getByRole('button', { name: 'Restore' })).toBeInTheDocument();
    expect(within(popup).getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
  });

  it('AC-008.5 Cancel closes the popup and sends nothing', async () => {
    const requests = recordRestores();
    const user = userEvent.setup();
    await openWithHrDeleted(user);
    const popup = await openPopup(user);
    await user.click(within(popup).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(requests).toHaveLength(0);
  });

  it('AC-008.6 AC-008.9 sends one restore however often it is clicked and ignores Cancel while it runs', async () => {
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    const requests = recordRestores();
    server.use(
      http.post(RESTORE_URL, async () => {
        await pending;
        return HttpResponse.json<DepartmentRef>({ departmentId: HR_ID, name: 'HR' });
      }),
    );
    const user = userEvent.setup();
    const { queryClient } = await openWithHrDeleted(user);
    try {
      const popup = await openPopup(user);
      const confirm = within(popup).getByRole('button', { name: 'Restore' });
      await user.click(confirm);
      await user.click(confirm);
      await waitFor(() => expect(requests).toHaveLength(1));
      await user.click(within(popup).getByRole('button', { name: 'Cancel' }));
      expect(screen.getByText(RESTORE_TEXT)).toBeInTheDocument();
    } finally {
      finish?.();
      await waitFor(() => expect(queryClient.isMutating()).toBe(0));
    }
    expect(requests).toHaveLength(1);
  });

  it('AC-008.7 closes, shows MSG52, refreshes every query and shows the row as Active', async () => {
    const user = userEvent.setup();
    const { queryClient, table } = await openWithHrDeleted(user);
    const usersKey = getGetUsersQueryKey({ q: 'cached' });
    queryClient.setQueryData(usersKey, { content: [] });
    const popup = await openPopup(user);
    await user.click(within(popup).getByRole('button', { name: 'Restore' }));
    expect(await screen.findByText(msg('MSG52'))).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(await within(table).findByRole('link', { name: 'HR' })).toBeInTheDocument();
    expect(within(table).queryByRole('button', { name: 'Restore' })).not.toBeInTheDocument();
    expect(queryClient.getQueryState(usersKey)?.isInvalidated).toBe(true);
  });

  it.each([
    [404, 'MSG04'],
    [403, 'MSG08'],
    [500, 'MSG06'],
  ] as const)('AC-008.8 on %s closes the popup and shows %s', async (status, code) => {
    server.use(http.post(RESTORE_URL, () => problem(status, code)));
    const user = userEvent.setup();
    await openWithHrDeleted(user);
    const popup = await openPopup(user);
    await user.click(within(popup).getByRole('button', { name: 'Restore' }));
    expect(await screen.findByText(msg(code))).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});
