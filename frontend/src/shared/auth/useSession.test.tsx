import { screen } from '@testing-library/react';
import { http } from 'msw';
import { MOCK_USERS } from '@/mocks/session';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { useSession } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { renderApp, renderWithProviders } from '@/test/render';

/** Prints what useSession() reports, so a test can read it like a user reads the screen. */
function SessionProbe() {
  const { session, roles, hasRole, hasAnyRole, isPending } = useSession();
  if (isPending) return <p>Loading session</p>;
  return (
    <dl>
      <dt>Signed in</dt>
      <dd>{session ? 'yes' : 'no'}</dd>
      <dt>Roles</dt>
      <dd>{roles.join(', ') || 'none'}</dd>
      <dt>Has Ticket Resolver</dt>
      <dd>{String(hasRole('TICKET_RESOLVER'))}</dd>
      <dt>Has Project Manager</dt>
      <dd>{String(hasRole('PROJECT_MANAGER'))}</dd>
      <dt>Has Project Manager or Admin</dt>
      <dd>{String(hasAnyRole(['PROJECT_MANAGER', 'ADMIN']))}</dd>
      <dt>Has Knowledge Manager</dt>
      <dd>{String(hasAnyRole(['KNOWLEDGE_MANAGER']))}</dd>
      <dt>Session</dt>
      <dd>{session ? JSON.stringify({ ...session, roles: undefined }) : ''}</dd>
    </dl>
  );
}

const value = (term: string) => screen.getByText(term).nextElementSibling?.textContent;

describe('useSession', () => {
  it('AC-001.2 exposes the user, Department, Departments, Projects, Spaces and Workspace state of /me', async () => {
    renderWithProviders(<SessionProbe />, { user: 'pm-km' });
    expect(await screen.findByText('Signed in')).toBeInTheDocument();

    const { roles: _roles, ...expected } = MOCK_USERS['pm-km'];
    expect(JSON.parse(value('Session') ?? '{}')).toEqual(expected);
    expect(value('Roles')).toBe('EMPLOYEE, PROJECT_MANAGER, KNOWLEDGE_MANAGER');
  });

  it('AC-001.4 answers role questions from all of the user’s roles', async () => {
    renderWithProviders(<SessionProbe />, { user: 'admin-tr' });
    expect(await screen.findByText('Signed in')).toBeInTheDocument();

    expect(value('Has Ticket Resolver')).toBe('true');
    expect(value('Has Project Manager')).toBe('false');
    expect(value('Has Project Manager or Admin')).toBe('true');
    expect(value('Has Knowledge Manager')).toBe('false');
  });

  it('AC-001.5 treats a 401 from /me as signed out, not as an error', async () => {
    renderWithProviders(<SessionProbe />, { role: null });
    expect(await screen.findByText('Signed in')).toBeInTheDocument();

    expect(value('Signed in')).toBe('no');
    expect(value('Roles')).toBe('none');
  });

  it('AC-001.6 shows the session error page with Try again when /me fails otherwise', async () => {
    server.use(http.get('*/api/v1/me', () => problem(500)));
    renderApp('/search', { role: 'TICKET_RESOLVER' });

    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { level: 1 })).not.toBeInTheDocument();
  });
});
