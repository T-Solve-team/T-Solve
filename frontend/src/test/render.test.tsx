import { screen } from '@testing-library/react';
import { MOCK_USERS } from '@/mocks/session';
import { useSession } from '@/shared/auth';
import { renderWithProviders } from './render';

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
});
