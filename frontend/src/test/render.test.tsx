import { screen } from '@testing-library/react';
import { MOCK_USERS } from '@/mocks/session';
import { ApiError, getReviewQueue } from '@/shared/api';
import { useSession } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { renderApp, renderWithProviders } from './render';

function WhoAmI() {
  const { session, roles, isPending } = useSession();
  if (isPending) return null;
  return <p>{session ? `${session.user.fullName}: ${roles.join(', ')}` : 'Signed out'}</p>;
}

describe('test helpers', () => {
  it('AC-009.3 signs in the fixture user with exactly the given role', async () => {
    renderWithProviders(<WhoAmI />, { role: 'KNOWLEDGE_MANAGER' });
    expect(
      await screen.findByText(`${MOCK_USERS.km.user.fullName}: EMPLOYEE, KNOWLEDGE_MANAGER`),
    ).toBeInTheDocument();
  });

  it('AC-009.3 signs in the Project Manager by default and signs out with role null', async () => {
    const { unmount } = renderWithProviders(<WhoAmI />);
    expect(
      await screen.findByText(`${MOCK_USERS.pm.user.fullName}: EMPLOYEE, PROJECT_MANAGER`),
    ).toBeInTheDocument();
    unmount();

    renderWithProviders(<WhoAmI />, { role: null });
    expect(await screen.findByText('Signed out')).toBeInTheDocument();
  });

  it('AC-009.4 signs in the named fixture user', async () => {
    renderWithProviders(<WhoAmI />, { user: 'admin-tr' });
    expect(
      await screen.findByText(
        `${MOCK_USERS['admin-tr'].user.fullName}: EMPLOYEE, ADMIN, TICKET_RESOLVER`,
      ),
    ).toBeInTheDocument();
  });

  it.each([
    ['unknown', 'MSG119', 404],
    ['suspended', 'MSG120', 403],
  ] as const)(
    'AC-011.6 renderApp and renderWithProviders accept { site: %j } for the %s answers',
    async (site, code, status) => {
      const { unmount } = renderApp('/', { site });
      expect(await screen.findByText(msg(code), {}, { timeout: 5000 })).toBeInTheDocument();
      unmount();

      renderWithProviders(<div />, { site });
      const error = await getReviewQueue().then(
        () => null,
        (thrown: unknown) => thrown,
      );
      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status, code });
    },
  );

  // Runs after the site tests above: the site state of one test never leaks into the next (Q4).
  it('AC-011.6 without site the mocks answer normally', async () => {
    renderApp('/review-queue');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Review Queue' }, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG119'))).not.toBeInTheDocument();
  });
});
