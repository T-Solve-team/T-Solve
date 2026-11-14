import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { getGetUsersQueryKey } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';

// 2.3 Delete button and 2.4 Delete Department popup, UC-14 (KAN-122, STORY-006).
const IT_ID = '00000000-0000-4000-8000-0000000000d1';
const ONE_URL = `*/api/v1/departments/${IT_ID}`;
const DELETE_TEXT = msg('MSG45', { department_name: 'IT', count: 2 });

type User = ReturnType<typeof userEvent.setup>;

const deleteButton = () => screen.findByRole('button', { name: 'Delete' }, { timeout: 5000 });
const dialog = () => screen.findByRole('dialog');

function openIt(role: 'ADMIN' | 'EMPLOYEE' = 'ADMIN') {
  return renderApp(paths.departmentDetail(IT_ID), { role });
}

function recordDeletes() {
  const requests: Request[] = [];
  const onStart = ({ request }: { request: Request }) => {
    if (
      request.method === 'DELETE' &&
      new URL(request.url).pathname === `/api/v1/departments/${IT_ID}`
    )
      requests.push(request);
  };
  server.events.on('request:start', onStart);
  onTestFinished(() => server.events.removeListener('request:start', onStart));
  return requests;
}

async function openPopup(user: User) {
  await user.click(await deleteButton());
  return dialog();
}

describe('Delete Department on 2.3', () => {
  it('AC-006.1 shows Delete next to Edit', async () => {
    openIt();
    expect(await deleteButton()).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
  });

  it('AC-006.2 opens the popup with MSG45, the name and the Project count, Delete and Cancel', async () => {
    const user = userEvent.setup();
    openIt();
    const popup = await openPopup(user);
    expect(within(popup).getByText(DELETE_TEXT)).toBeInTheDocument();
    expect(within(popup).getByRole('button', { name: 'Delete' })).toBeInTheDocument();
    expect(within(popup).getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
  });

  it('AC-006.3 Cancel closes the popup and sends nothing', async () => {
    const requests = recordDeletes();
    const user = userEvent.setup();
    openIt();
    const popup = await openPopup(user);
    await user.click(within(popup).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(requests).toHaveLength(0);
  });

  it('AC-006.4 AC-006.8 sends one DELETE however often it is clicked and ignores Cancel while it runs', async () => {
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    const requests = recordDeletes();
    server.use(
      http.delete(ONE_URL, async () => {
        await pending;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    const { queryClient } = openIt();
    try {
      const popup = await openPopup(user);
      const confirm = within(popup).getByRole('button', { name: 'Delete' });
      await user.click(confirm);
      await user.click(confirm);
      await waitFor(() => expect(requests).toHaveLength(1));
      await user.click(within(popup).getByRole('button', { name: 'Cancel' }));
      expect(screen.getByText(DELETE_TEXT)).toBeInTheDocument();
    } finally {
      finish?.();
      await waitFor(() => expect(queryClient.isMutating()).toBe(0));
    }
    expect(requests).toHaveLength(1);
  });

  it('AC-006.5 shows MSG46, refreshes every query and opens 2.2 on 204', async () => {
    const user = userEvent.setup();
    const { queryClient } = openIt();
    const usersKey = getGetUsersQueryKey({ q: 'cached' });
    queryClient.setQueryData(usersKey, { content: [] });
    const popup = await openPopup(user);
    await user.click(within(popup).getByRole('button', { name: 'Delete' }));
    expect(await screen.findByText(msg('MSG46'))).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'New Department' })).toBeInTheDocument();
    expect(queryClient.getQueryState(usersKey)?.isInvalidated).toBe(true);
  });

  it.each([
    [404, 'MSG04'],
    [403, 'MSG08'],
    [500, 'MSG06'],
  ] as const)(
    'AC-006.6 on %s closes the popup, stays on 2.3 and shows %s',
    async (status, code) => {
      server.use(http.delete(ONE_URL, () => problem(status, code)));
      const user = userEvent.setup();
      openIt();
      const popup = await openPopup(user);
      await user.click(within(popup).getByRole('button', { name: 'Delete' }));
      expect(await screen.findByText(msg(code))).toBeInTheDocument();
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
    },
  );

  it('AC-006.7 hides Delete while the rename form is open', async () => {
    const user = userEvent.setup();
    openIt();
    await user.click(await screen.findByRole('button', { name: 'Edit' }, { timeout: 5000 }));
    expect(await screen.findByRole('textbox', { name: 'Department name' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
  });
});
