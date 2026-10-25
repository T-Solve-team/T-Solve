import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { MOCK_USERS } from '@/mocks/session';
import { server } from '@/mocks/server';
import type { CurrentUser } from '@/shared/api';
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

const PROJECT_P = '00000000-0000-4000-8000-000000000101';
const PROJECT_P_LOOKALIKE = '00000000-0000-4000-8000-000000000102';
const PROJECT_OTHER = '00000000-0000-4000-8000-000000000999';
const DEPARTMENT_IT = '00000000-0000-4000-8000-0000000000d1';
const SPACE_IT = '00000000-0000-4000-8000-0000000000a1';

const UUID_USER: CurrentUser = {
  user: {
    id: '00000000-0000-4000-8000-000000000003',
    fullName: 'Đỗ Minh Quân',
    email: 'quan.do@company.example',
  },
  roles: ['EMPLOYEE', 'PROJECT_MANAGER'],
  department: { departmentId: DEPARTMENT_IT, name: 'IT' },
  departments: [{ departmentId: DEPARTMENT_IT, name: 'IT' }],
  projects: [
    {
      projectId: PROJECT_P,
      jiraProjectKey: 'ITSUP',
      departmentId: DEPARTMENT_IT,
      departmentName: 'IT',
      roleInProject: 'PROJECT_MANAGER',
    },
    {
      projectId: PROJECT_P_LOOKALIKE,
      jiraProjectKey: 'ITNET',
      departmentId: DEPARTMENT_IT,
      departmentName: 'IT',
      roleInProject: 'MEMBER',
    },
  ],
  managedSpaces: [{ spaceId: SPACE_IT, name: 'IT Space' }],
  workspaceReady: true,
};

/** Prints the answers of the Project checks for the ids it is given, as `id:manager:member` lines. */
function ProjectChecks({ projectIds }: { projectIds: string[] }) {
  const { isPending, isProjectManagerOf, isMemberOf } = useSession();
  if (isPending) return <p>Loading session</p>;
  return (
    <ul>
      {projectIds.map((id) => (
        <li key={id}>{`${id}:${isProjectManagerOf(id)}:${isMemberOf(id)}`}</li>
      ))}
    </ul>
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

  it('AC-001.8 exposes the ids of /me as UUID strings', async () => {
    server.use(http.get('*/api/v1/me', () => HttpResponse.json(UUID_USER)));
    renderWithProviders(<SessionProbe />, { user: 'pm' });
    expect(await screen.findByText('Signed in')).toBeInTheDocument();

    const session = JSON.parse(value('Session') ?? '{}') as CurrentUser;
    expect(session.user.id).toBe(UUID_USER.user.id);
    expect(session.department?.departmentId).toBe(DEPARTMENT_IT);
    expect(session.departments.map((d) => d.departmentId)).toEqual([DEPARTMENT_IT]);
    expect(session.projects.map((p) => [p.projectId, p.departmentId])).toEqual([
      [PROJECT_P, DEPARTMENT_IT],
      [PROJECT_P_LOOKALIKE, DEPARTMENT_IT],
    ]);
    expect(session.managedSpaces.map((s) => s.spaceId)).toEqual([SPACE_IT]);
  });

  it('AC-001.8 isProjectManagerOf compares a Project id as a string', async () => {
    server.use(http.get('*/api/v1/me', () => HttpResponse.json(UUID_USER)));
    renderWithProviders(
      <ProjectChecks projectIds={[PROJECT_P, PROJECT_OTHER, PROJECT_P_LOOKALIKE]} />,
      { user: 'pm' },
    );

    // Manager of P; not of another UUID; not of the Project that differs in the last characters (member only).
    expect(await screen.findByText(`${PROJECT_P}:true:true`)).toBeInTheDocument();
    expect(screen.getByText(`${PROJECT_OTHER}:false:false`)).toBeInTheDocument();
    expect(screen.getByText(`${PROJECT_P_LOOKALIKE}:false:true`)).toBeInTheDocument();
  });

  it('AC-001.8 isMemberOf compares a Project id as a string', async () => {
    server.use(http.get('*/api/v1/me', () => HttpResponse.json(UUID_USER)));
    renderWithProviders(
      <ProjectChecks projectIds={[PROJECT_P, PROJECT_P_LOOKALIKE, PROJECT_OTHER]} />,
      { user: 'pm' },
    );

    await screen.findByText(`${PROJECT_P}:true:true`);
    expect(screen.getByText(`${PROJECT_P_LOOKALIKE}:false:true`)).toBeInTheDocument();
    expect(screen.getByText(`${PROJECT_OTHER}:false:false`)).toBeInTheDocument();
  });

  it('AC-001.8 isMemberOf is false for every Project while signed out', async () => {
    renderWithProviders(<ProjectChecks projectIds={[PROJECT_P]} />, { role: null });

    expect(await screen.findByText(`${PROJECT_P}:false:false`)).toBeInTheDocument();
  });

  it('AC-001.8 does not coerce an id to a number', async () => {
    server.use(http.get('*/api/v1/me', () => HttpResponse.json(UUID_USER)));
    renderWithProviders(<ProjectChecks projectIds={['101', '00000000']} />, { user: 'pm' });

    expect(await screen.findByText('101:false:false')).toBeInTheDocument();
    expect(screen.getByText('00000000:false:false')).toBeInTheDocument();
  });
});
