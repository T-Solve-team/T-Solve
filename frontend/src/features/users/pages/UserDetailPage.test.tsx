import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { getGetUsersQueryKey } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

describe('UserDetailPage creation', () => {
  beforeAll(async () => {
    await import('./UserDetailPage');
  });

  it('AC-005.1 opens creation mode from Add user', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/users', { role: 'ADMIN' });
    await user.click(await screen.findByRole('button', { name: 'Add user' }, { timeout: 5000 }));
    expect(
      await screen.findByRole('textbox', { name: 'Email' }, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/users/new');
    expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument();
  });

  it('AC-005.2 validates required, malformed and overlong email without cutting input', async () => {
    const user = userEvent.setup();
    renderApp('/users/new', { role: 'ADMIN' });
    const save = await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    await user.click(save);
    expect(await screen.findByText(msg('MSG01', { field_name: 'Email' }))).toBeInTheDocument();
    const email = screen.getByRole('textbox', { name: 'Email' });
    await user.type(email, 'invalid');
    await user.click(save);
    expect(await screen.findByText(msg('MSG03'))).toBeInTheDocument();
    await user.clear(email);
    const long = `${'a'.repeat(90)}@company.example`;
    await user.type(email, long);
    await user.click(save);
    expect(
      await screen.findByText(msg('MSG02', { field_name: 'Email', max_length: 100 })),
    ).toBeInTheDocument();
    expect(email).toHaveValue(long);
  });

  it.each([
    ['outside@example.org', 'MSG03'],
    ['user1@company.example', 'MSG11'],
    ['user11@company.example', 'MSG111'],
  ] as const)('AC-005.3 preserves %s and displays %s on rejected creation', async (value, code) => {
    const user = userEvent.setup();
    renderApp('/users/new', { role: 'ADMIN' });
    const email = await screen.findByRole('textbox', { name: 'Email' }, { timeout: 5000 });
    await user.type(email, value);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg(code, { domain: 'company.example' }))).toBeInTheDocument();
    expect(email).toHaveValue(value);
  });

  it('AC-005.4 keeps Employee selected and disabled', async () => {
    renderApp('/users/new', { role: 'ADMIN' });
    const employee = await screen.findByRole('checkbox', { name: 'Employee' }, { timeout: 5000 });
    expect(employee).toBeChecked();
    expect(employee).toBeDisabled();
  });

  it('AC-005.5 requires Department for Project Managers', async () => {
    const user = userEvent.setup();
    renderApp('/users/new', { role: 'ADMIN' });
    await user.type(
      await screen.findByRole('textbox', { name: 'Email' }, { timeout: 5000 }),
      'new@company.example',
    );
    await user.click(screen.getByRole('checkbox', { name: 'Project Manager' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG01', { field_name: 'Department' }))).toBeInTheDocument();
    await user.click(screen.getByRole('combobox', { name: 'Department' }));
    expect(await screen.findByRole('option', { name: 'IT' })).toBeInTheDocument();
  });

  it('AC-005.5 creates a Project Manager after selecting a UUID Department (BUG-014, BUG-021)', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/users/new', { role: 'ADMIN' });
    await user.type(
      await screen.findByRole('textbox', { name: 'Email' }, { timeout: 5000 }),
      'preview@company.example',
    );
    await user.click(screen.getByRole('checkbox', { name: 'Project Manager' }));
    await user.click(screen.getByRole('combobox', { name: 'Department' }));
    await user.click(await screen.findByRole('option', { name: 'IT' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG12'))).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.pathname).toBe('/users'));
    expect(
      await screen.findByRole('link', { name: 'preview@company.example' }, { timeout: 5000 }),
    ).toBeInTheDocument();
  });

  it('AC-005.6 requires API-provided Spaces and shows assignment conflicts', async () => {
    const user = userEvent.setup();
    renderApp('/users/new', { role: 'ADMIN' });
    await user.type(
      await screen.findByRole('textbox', { name: 'Email' }, { timeout: 5000 }),
      'new@company.example',
    );
    await user.click(screen.getByRole('checkbox', { name: 'Knowledge Manager' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG01', { field_name: 'Spaces' }))).toBeInTheDocument();
    await user.click(screen.getByRole('combobox', { name: 'Spaces' }));
    await user.click(await screen.findByRole('option', { name: 'IT Knowledge' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(
      await screen.findByText(msg('MSG117', { space_name: 'IT Knowledge' })),
    ).toBeInTheDocument();
  });

  it('AC-005.7 creates one account, invalidates user lists and returns with MSG12', async () => {
    const user = userEvent.setup();
    const { router, queryClient } = renderApp('/users/new', { role: 'ADMIN' });
    queryClient.setQueryData(getGetUsersQueryKey({ q: 'another' }), { content: [] });
    await user.type(
      await screen.findByRole('textbox', { name: 'Email' }, { timeout: 5000 }),
      'new@company.example',
    );
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG12'))).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.pathname).toBe('/users'));
    expect(queryClient.getQueryState(getGetUsersQueryKey({ q: 'another' }))?.isInvalidated).toBe(
      true,
    );
    expect(
      await screen.findByRole('link', { name: 'new@company.example' }, { timeout: 5000 }),
    ).toBeInTheDocument();
  });

  it('AC-005.8 preserves the form after a Deactivated duplicate response', async () => {
    const user = userEvent.setup();
    renderApp('/users/new', { role: 'ADMIN' });
    const email = await screen.findByRole('textbox', { name: 'Email' }, { timeout: 5000 });
    await user.type(email, 'user11@company.example');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG111'))).toBeInTheDocument();
    expect(email).toHaveValue('user11@company.example');
  });

  it('AC-005.7 retains input after a network failure and allows retry', async () => {
    server.use(http.post('*/api/v1/users', () => problem(500)));
    const user = userEvent.setup();
    renderApp('/users/new', { role: 'ADMIN' });
    const email = await screen.findByRole('textbox', { name: 'Email' }, { timeout: 5000 });
    await user.type(email, 'retry@company.example');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(email).toHaveValue('retry@company.example');
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
  });

  it('AC-005.7 disables Save during a pending request to prevent double submission (BUG-013)', async () => {
    let finish: (() => void) | undefined;
    let calls = 0;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    server.use(
      http.post('*/api/v1/users', async () => {
        calls++;
        await pending;
        return HttpResponse.json(
          {
            id: '10000000-0000-4000-8000-000000000100',
            email: 'pending@company.example',
            roles: ['EMPLOYEE'],
            fullName: null,
            department: null,
            status: 'ACTIVE',
            lastSignInAt: null,
          },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();
    renderApp('/users/new', { role: 'ADMIN' });
    await user.type(
      await screen.findByRole('textbox', { name: 'Email' }, { timeout: 5000 }),
      'pending@company.example',
    );
    const save = screen.getByRole('button', { name: 'Save' });
    await user.dblClick(save);
    await waitFor(() => expect(save).toBeDisabled());
    expect(calls).toBe(1);
    expect(screen.getByRole('textbox', { name: 'Email' })).toHaveValue('pending@company.example');
    finish?.();
    expect(await screen.findByText(msg('MSG12'))).toBeInTheDocument();
  });

  it('AC-005.6 retries failed creation options before showing assignment controls', async () => {
    let fails = true;
    server.use(
      http.get('*/api/v1/users/creation-options', () =>
        fails ? problem(500) : HttpResponse.json({ companyDomain: 'company.example', spaces: [] }),
      ),
    );
    const user = userEvent.setup();
    renderApp('/users/new', { role: 'ADMIN' });
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG06'));
    fails = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('textbox', { name: 'Email' })).toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: 'Knowledge Manager' }));
    expect(await screen.findByText(msg('MSG04'))).toBeInTheDocument();
  });
});
