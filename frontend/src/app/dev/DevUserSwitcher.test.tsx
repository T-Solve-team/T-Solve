import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, Outlet } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { MOCK_USERS } from '@/mocks/session';
import { AppProviders } from '@/app/providers/AppProviders';
import { buildRoutes } from '@/app/router/routes';
import { ApiError, approveTickets, getMe, getReviewQueue } from '@/shared/api';
import { getDevUser, setDevUser, useSession } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { createTestQueryClient } from '@/test/render';
import { DevUserSwitcher } from './DevUserSwitcher';

/** The error a request rejects with, or null when it succeeds. */
async function failureOf(request: () => Promise<unknown>): Promise<unknown> {
  return request().then(
    () => null,
    (error: unknown) => error,
  );
}

/** The whole app with the switcher next to it (as RootLayout shows it in mock mode). */
function renderAppWithSwitcher(path: string) {
  const router = createMemoryRouter(
    [
      {
        element: (
          <>
            <Outlet />
            <DevUserSwitcher />
          </>
        ),
        children: buildRoutes(),
      },
    ],
    { initialEntries: [path] },
  );
  render(
    <AppProviders queryClient={createTestQueryClient()}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return router;
}

async function pick(option: string) {
  const user = userEvent.setup();
  const [combobox] = screen.getAllByRole('combobox', { name: 'Dev user' });
  if (!combobox) throw new Error('No dev user switcher');
  await user.click(combobox);
  await user.click(await screen.findByTitle(option));
}

function WhoAmI() {
  const { session, isPending } = useSession();
  if (isPending) return null;
  return <p>{session ? `Signed in as ${session.user.fullName}` : 'Nobody is signed in'}</p>;
}

function renderSwitcher() {
  const router = createMemoryRouter([
    {
      path: '/',
      element: (
        <>
          <DevUserSwitcher />
          <WhoAmI />
        </>
      ),
    },
  ]);
  render(
    <AppProviders queryClient={createTestQueryClient()}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
}

describe('DevUserSwitcher', () => {
  it('AC-009.2 signs in the chosen fixture user', async () => {
    const user = userEvent.setup();
    setDevUser('pm');
    renderSwitcher();
    expect(
      await screen.findByText(`Signed in as ${MOCK_USERS.pm.user.fullName}`),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: 'Dev user' }));
    await user.click(await screen.findByTitle('Project Manager + Knowledge Manager'));

    expect(
      await screen.findByText(`Signed in as ${MOCK_USERS['pm-km'].user.fullName}`),
    ).toBeInTheDocument();
    expect(getDevUser()).toBe('pm-km');
  });

  it('AC-009.2 signs out with "Signed out"', async () => {
    const user = userEvent.setup();
    setDevUser('admin');
    renderSwitcher();
    expect(
      await screen.findByText(`Signed in as ${MOCK_USERS.admin.user.fullName}`),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: 'Dev user' }));
    await user.click(await screen.findByTitle('Signed out'));

    expect(await screen.findByText('Nobody is signed in')).toBeInTheDocument();
  });

  it('AC-011.6 offers "Unknown site" and "Suspended Workspace"', async () => {
    const user = userEvent.setup();
    setDevUser('pm');
    renderSwitcher();
    await screen.findByText(`Signed in as ${MOCK_USERS.pm.user.fullName}`);

    await user.click(screen.getByRole('combobox', { name: 'Dev user' }));

    expect(await screen.findByTitle('Unknown site')).toBeInTheDocument();
    expect(screen.getByTitle('Suspended Workspace')).toBeInTheDocument();
  });

  it.each([
    ['Unknown site', 404, 'MSG119'],
    ['Suspended Workspace', 403, 'MSG120'],
  ] as const)(
    'AC-011.6 "%s" makes every mock API request answer %s %s',
    async (option, status, code) => {
      setDevUser('pm');
      renderSwitcher();
      await screen.findByText(`Signed in as ${MOCK_USERS.pm.user.fullName}`);

      await pick(option);

      for (const request of [
        () => getMe(),
        () => getReviewQueue(),
        () => approveTickets({ ticketIds: ['20000000-0000-4000-8000-000000000001'] }),
      ]) {
        const error = await failureOf(request);
        expect(error).toBeInstanceOf(ApiError);
        expect(error).toMatchObject({ status, code });
      }
    },
  );

  it('AC-011.6 picking a fixture user after "Unknown site" leaves the site page (the switcher resets the site state)', async () => {
    setDevUser('pm');
    renderAppWithSwitcher('/review-queue');
    await screen.findByRole('heading', { level: 1, name: 'Review Queue' }, { timeout: 5000 });

    await pick('Unknown site');
    expect(await screen.findByText(msg('MSG119'), {}, { timeout: 5000 })).toBeInTheDocument();

    await pick('Admin');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'User List' }, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG119'))).not.toBeInTheDocument();
  });
});
