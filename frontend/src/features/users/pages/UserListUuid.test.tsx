import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { getUserDepartments, getUserDetail, getUsers } from '@/shared/api';
import { setDevUser } from '@/shared/auth';
import { renderApp } from '@/test/render';
import { IT_DEPARTMENT_ID } from '../mocks/data';

const USER_ID = '10000000-0000-4000-8000-000000000001';
const page = {
  content: [
    {
      id: USER_ID,
      fullName: 'UUID User',
      email: 'uuid@example.test',
      roles: ['EMPLOYEE', 'ADMIN'],
      department: { departmentId: IT_DEPARTMENT_ID, name: 'IT' },
      status: 'ACTIVE',
    },
  ],
  page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
};

describe('UUID User List integration', () => {
  beforeAll(async () => {
    await import('./UserListPage');
  });

  it('AC-004.10 AC-004.17 omits Last sign-in and preserves the UUID link exactly (BUG-114)', async () => {
    server.use(http.get('*/api/v1/users', () => HttpResponse.json(page)));
    renderApp('/users', { role: 'ADMIN' });
    const table = await screen.findByRole('table', { name: 'Workspace users' }, { timeout: 5000 });
    expect(
      within(table).queryByRole('columnheader', { name: /Last sign-in/ }),
    ).not.toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'UUID User' })).toHaveAttribute(
      'href',
      `/users/${USER_ID}`,
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
    const { router } = renderApp('/users?q=UUID&page=2&sort=lastSignInAt,desc', { role: 'ADMIN' });
    await screen.findByRole('link', { name: 'UUID User' }, { timeout: 5000 });
    expect(requestedSorts.length).toBeGreaterThan(0);
    expect(requestedSorts.every((sort) => sort.join() === 'fullName,asc')).toBe(true);
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).get('sort')).toBe('fullName,asc'),
    );
    expect(new URLSearchParams(router.state.location.search).get('q')).toBe('UUID');
  });

  it('AC-004.10 AC-004.15 keeps UUID list and Department ids without a numeric adapter (BUG-114)', async () => {
    setDevUser('admin');
    const users = await getUsers();
    for (const user of users.content) {
      expect(user.id).toMatch(/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/);
      expect(user).not.toHaveProperty('lastSignInAt');
      if (user.department)
        expect(user.department.departmentId).toMatch(
          /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/,
        );
    }
    const departments = await getUserDepartments();
    expect(departments.map((item) => item.name)).toEqual(['HR', 'IT']);
    for (const department of departments)
      expect(department.departmentId).toMatch(/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/);
    const filtered = await getUsers({ departmentId: IT_DEPARTMENT_ID });
    expect(filtered.content.length).toBeGreaterThan(0);
    expect(
      filtered.content.every((user) => user.department?.departmentId === IT_DEPARTMENT_ID),
    ).toBe(true);
  });

  it('AC-004.10 opens the unchanged UUID detail and rejects a numeric alias (BUG-114)', async () => {
    setDevUser('admin');
    await expect(getUserDetail('1')).rejects.toMatchObject({ status: 404, code: 'MSG04' });
    renderApp(`/users/${USER_ID}`, { role: 'ADMIN' });
    expect(
      await screen.findByText('user1@company.example', {}, { timeout: 5000 }),
    ).toBeInTheDocument();
  });
});
