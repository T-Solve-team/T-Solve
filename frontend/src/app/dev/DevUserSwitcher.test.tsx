import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { MOCK_USERS } from '@/mocks/session';
import { AppProviders } from '@/app/providers/AppProviders';
import { getDevUser, setDevUser, useSession } from '@/shared/auth';
import { createTestQueryClient } from '@/test/render';
import { DevUserSwitcher } from './DevUserSwitcher';

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
});
