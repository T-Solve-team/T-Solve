import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { getUserDepartments, getUsers } from '@/shared/api';
import { setDevUser } from '@/shared/auth';
import { renderApp } from '@/test/render';

const MAX_ID = '9223372036854775807';
const page = {
  content: [
    {
      id: MAX_ID,
      fullName: 'SQL User',
      email: 'sql@example.test',
      roles: ['EMPLOYEE', 'ADMIN'],
      department: { departmentId: MAX_ID, name: 'SQL Department' },
      status: 'ACTIVE',
    },
  ],
  page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
};

describe('SQL User List compatibility', () => {
  beforeAll(async () => {
    await import('./UserListPage');
  });

  it('AC-004.10 AC-004.17 omits Last sign-in and preserves a BIGINT link exactly', async () => {
    server.use(http.get('*/api/v1/users', () => HttpResponse.json(page)));
    renderApp('/users', { role: 'ADMIN' });
    const table = await screen.findByRole('table', { name: 'Workspace users' }, { timeout: 5000 });
    expect(
      within(table).queryByRole('columnheader', { name: /Last sign-in/ }),
    ).not.toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'SQL User' })).toHaveAttribute(
      'href',
      `/users/${MAX_ID}`,
    );
  });

  it('AC-004.17 normalizes an old sign-in sort before sending any users request', async () => {
    const requestedSorts: string[][] = [];
    server.use(
      http.get('*/api/v1/users', ({ request }) => {
        requestedSorts.push(new URL(request.url).searchParams.getAll('sort'));
        return HttpResponse.json(page);
      }),
    );
    const { router } = renderApp('/users?q=SQL&page=2&sort=lastSignInAt,desc', { role: 'ADMIN' });
    await screen.findByRole('link', { name: 'SQL User' }, { timeout: 5000 });
    expect(requestedSorts.length).toBeGreaterThan(0);
    expect(requestedSorts.every((sort) => sort.join() === 'fullName,asc')).toBe(true);
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).get('sort')).toBe('fullName,asc'),
    );
    expect(new URLSearchParams(router.state.location.search).get('q')).toBe('SQL');
  });

  it('AC-004.10 AC-004.15 mirrors numeric list and Department ids in the mock boundary', async () => {
    setDevUser('admin');
    const users = await getUsers();
    for (const user of users.content) {
      expect(user.id).toMatch(/^[1-9][0-9]*$/);
      expect(user).not.toHaveProperty('lastSignInAt');
      if (user.department) expect(user.department.departmentId).toMatch(/^[1-9][0-9]*$/);
    }
    const departments = await getUserDepartments();
    expect(departments.map((item) => item.name)).toEqual(['HR', 'IT']);
    for (const department of departments) expect(department.departmentId).toMatch(/^[1-9][0-9]*$/);
  });

  it('AC-004.10 opens an existing mock detail through a numeric list link', async () => {
    renderApp('/users/1', { role: 'ADMIN' });
    expect(
      await screen.findByText('user1@company.example', {}, { timeout: 5000 }),
    ).toBeInTheDocument();
  });
});
