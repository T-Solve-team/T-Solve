import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

describe('UserListPage', () => {
  beforeAll(async () => {
    await import('./UserListPage');
  });

  it('AC-004.1 displays Workspace users with the SRS columns and named action (BUG-010)', async () => {
    renderApp('/users', { role: 'ADMIN' });
    const table = await screen.findByRole('table', { name: 'Workspace users' }, { timeout: 5000 });
    for (const label of ['Full name', 'Email', 'Department', 'Roles', 'Status', 'Last sign-in']) {
      expect(
        within(table).getByRole('columnheader', { name: new RegExp(label) }),
      ).toBeInTheDocument();
    }
    expect(within(table).getAllByRole('row').slice(1)).toHaveLength(20);
    expect(screen.getByRole('button', { name: 'Add user' })).toBeInTheDocument();
    expect(screen.queryByText('foreign@other.example')).not.toBeInTheDocument();
    expect(screen.getByText('Total 24 items')).toBeInTheDocument();
  });

  it('AC-004.2 searches name/email case-insensitively on Enter and restores it with Back', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/users?q=USER24', { role: 'ADMIN' });
    expect(
      await screen.findByRole('link', { name: 'Vương Khánh Linh' }, { timeout: 5000 }),
    ).toBeInTheDocument();
    const search = screen.getByRole('searchbox', { name: 'Search' });
    await user.clear(search);
    await user.type(search, 'Bùi Anh{Enter}');
    expect(await screen.findByRole('link', { name: 'Bùi Anh Tuấn' })).toBeInTheDocument();
    expect(new URLSearchParams(router.state.location.search).get('q')).toBe('Bùi Anh');
    await act(() => router.navigate(-1));
    expect(await screen.findByRole('link', { name: 'Vương Khánh Linh' })).toBeInTheDocument();
    expect(screen.getByRole('searchbox', { name: 'Search' })).toHaveValue('USER24');
  });

  it('AC-004.8 shows MSG02 for a search over 100 characters without changing the query', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/users?q=USER24', { role: 'ADMIN' });
    await screen.findByRole('link', { name: 'Vương Khánh Linh' }, { timeout: 5000 });
    let requests = 0;
    server.use(
      http.get('*/api/v1/users', () => {
        requests += 1;
        return problem(500);
      }),
    );
    const search = screen.getByRole('searchbox', { name: 'Search' });
    await user.clear(search);
    await user.paste('x'.repeat(101));
    await user.keyboard('{Enter}');
    expect(screen.getByRole('alert')).toHaveTextContent(
      msg('MSG02', { field_name: 'Search', max_length: 100 }),
    );
    expect(search).toHaveAttribute('aria-invalid', 'true');
    expect(search).toHaveValue('x'.repeat(101));
    expect(router.state.location.search).toBe('?q=USER24');
    expect(screen.getByRole('link', { name: 'Vương Khánh Linh' })).toBeInTheDocument();
    expect(requests).toBe(0);
  });

  it('AC-004.2 clears stale search validation when Back restores q (BUG-015)', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/users?q=USER24', { role: 'ADMIN' });
    await screen.findByRole('link', { name: 'Vương Khánh Linh' }, { timeout: 5000 });
    const search = screen.getByRole('searchbox', { name: 'Search' });
    await user.clear(search);
    await user.type(search, 'Bùi Anh{Enter}');
    await screen.findByRole('link', { name: 'Bùi Anh Tuấn' });
    await user.clear(screen.getByRole('searchbox', { name: 'Search' }));
    await user.paste('x'.repeat(101));
    await user.keyboard('{Enter}');
    expect(screen.getByRole('alert')).toBeInTheDocument();
    await act(() => router.navigate(-1));
    await screen.findByRole('link', { name: 'Vương Khánh Linh' });
    expect(screen.getByRole('searchbox', { name: 'Search' })).toHaveValue('USER24');
    expect(screen.getByRole('searchbox', { name: 'Search' })).toHaveAttribute(
      'aria-invalid',
      'false',
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('AC-004.2 combines role, Department and status filters and resets pagination', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/users?page=2&status=ACTIVE&departmentId=2', { role: 'ADMIN' });
    await screen.findByRole('table', { name: 'Workspace users' }, { timeout: 5000 });
    await user.click(screen.getByRole('combobox', { name: 'Role' }));
    await user.click(await screen.findByRole('option', { name: 'Employee only' }));
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).get('page')).toBeNull(),
    );
    expect(await screen.findByRole('link', { name: 'Trần Thị Lan' })).toBeInTheDocument();
    expect(await screen.findByText('Total 6 items')).toBeInTheDocument();
    expect(new URLSearchParams(router.state.location.search).get('status')).toBe('ACTIVE');
    expect(new URLSearchParams(router.state.location.search).get('departmentId')).toBe('2');
  });

  it('AC-004.2 submits Department and status changes through their controls', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/users', { role: 'ADMIN' });
    await screen.findByRole('table', {}, { timeout: 5000 });
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Department' })).toBeEnabled());
    await user.click(screen.getByRole('combobox', { name: 'Department' }));
    await user.click(await screen.findByRole('option', { name: 'IT' }));
    await user.click(screen.getByRole('combobox', { name: 'Status' }));
    await user.click(await screen.findByRole('option', { name: 'Deactivated' }));
    expect(await screen.findByRole('link', { name: 'Tạ Minh Khang' })).toBeInTheDocument();
    expect(await screen.findByText('Total 2 items')).toBeInTheDocument();
    expect(new URLSearchParams(router.state.location.search).get('departmentId')).toBe('1');
    expect(new URLSearchParams(router.state.location.search).get('status')).toBe('DEACTIVATED');
  });

  it('AC-004.3 pages 20 users at a time and sorts via URL state', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/users', { role: 'ADMIN' });
    const table = await screen.findByRole('table', { name: 'Workspace users' }, { timeout: 5000 });
    expect(within(table).getAllByRole('row')[1]).toHaveTextContent('Bùi Anh Tuấn');
    await user.click(screen.getByTitle('2'));
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(5));
    expect(new URLSearchParams(router.state.location.search).get('page')).toBe('2');
    await user.click(within(table).getByRole('columnheader', { name: /Email/ }));
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(21));
    expect(new URLSearchParams(router.state.location.search).get('page')).toBeNull();
    expect(new URLSearchParams(router.state.location.search).get('sort')).toBe('email,asc');
    expect(within(table).getAllByRole('row')[1]).toHaveTextContent('user1@company.example');
  });

  it('AC-004.4 displays loading while the list request is pending', async () => {
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    server.use(
      http.get('*/api/v1/users', async () => {
        await pending;
        return HttpResponse.json({
          content: [],
          page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
        });
      }),
    );
    renderApp('/users', { role: 'ADMIN' });
    expect(await screen.findByText('Loading ...', {}, { timeout: 5000 })).toBeInTheDocument();
    finish?.();
    expect(await screen.findByText(msg('MSG04'))).toBeInTheDocument();
  });

  it('AC-004.5 displays a list error and successfully retries', async () => {
    const user = userEvent.setup();
    let fails = true;
    server.use(
      http.get('*/api/v1/users', () =>
        fails
          ? problem(500)
          : HttpResponse.json({
              content: [],
              page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
            }),
      ),
    );
    renderApp('/users', { role: 'ADMIN' });
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG06'));
    fails = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText(msg('MSG04'))).toBeInTheDocument();
  });

  it('AC-004.5 surfaces Department lookup failure with retry', async () => {
    server.use(http.get('*/api/v1/users/departments', () => problem(500)));
    renderApp('/users', { role: 'ADMIN' });
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG06'));
    expect(screen.getByRole('combobox', { name: 'Department' })).toBeDisabled();
  });

  it('AC-004.6 displays MSG04 when no users match', async () => {
    renderApp('/users?q=not-present', { role: 'ADMIN' });
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('AC-004.1 leaves missing names empty and links the email to User Detail (BUG-012)', async () => {
    server.use(
      http.get('*/api/v1/users', () =>
        HttpResponse.json({
          content: [
            {
              id: '10000000-0000-4000-8000-000000000025',
              fullName: null,
              email: 'new@company.example',
              roles: ['EMPLOYEE'],
              department: null,
              status: 'ACTIVE',
              lastSignInAt: null,
            },
          ],
          page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
        }),
      ),
    );
    renderApp('/users', { role: 'ADMIN' });
    const table = await screen.findByRole('table', { name: 'Workspace users' }, { timeout: 5000 });
    const row = within(table).getByRole('row', { name: /new@company.example/ });
    expect(within(row).getAllByRole('cell')[0]).toBeEmptyDOMElement();
    expect(within(row).getByRole('link', { name: 'new@company.example' })).toHaveAttribute(
      'href',
      '/users/10000000-0000-4000-8000-000000000025',
    );
  });

  it.each(['/users', '/users/new', '/users/10000000-0000-4000-8000-000000000001'])(
    'AC-004.7 denies a non-Admin at %s',
    async (path) => {
      renderApp(path, { role: 'EMPLOYEE' });
      expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
      expect(screen.queryByRole('table', { name: 'Workspace users' })).not.toBeInTheDocument();
    },
  );
});
