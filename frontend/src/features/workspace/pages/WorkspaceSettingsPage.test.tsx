import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { getGetUserDepartmentsQueryKey, type DepartmentRef } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

const DEPARTMENTS_URL = '*/api/v1/departments';
const DEPARTMENT_LIST_URL = '*/api/v1/users/departments';

function recordDepartmentPosts() {
  const requests: Request[] = [];
  const onStart = ({ request }: { request: Request }) => {
    if (
      request.method === 'POST' &&
      new URL(request.url).pathname.endsWith('/api/v1/departments')
    ) {
      requests.push(request);
    }
  };
  server.events.on('request:start', onStart);
  onTestFinished(() => server.events.removeListener('request:start', onStart));
  return requests;
}

async function openDepartmentModal(user: ReturnType<typeof userEvent.setup>) {
  await user.click(
    await screen.findByRole('button', { name: 'New Department' }, { timeout: 5000 }),
  );
  return screen.findByRole('dialog', { name: 'New Department' });
}

const departmentName = (dialog: HTMLElement) =>
  within(dialog).getByRole('textbox', { name: 'Department name' });
const saveButton = (dialog: HTMLElement) => within(dialog).getByRole('button', { name: 'Save' });

async function expectModalClosed() {
  // jsdom does not finish CSS animations; dispatch the end event antd expects on exit.
  await waitFor(() => {
    const dialog = screen.queryByRole('dialog', { name: 'New Department' });
    if (dialog) expect(dialog).toHaveClass('ant-zoom-leave-active');
  });
  const dialog = screen.queryByRole('dialog', { name: 'New Department' });
  if (dialog) fireEvent(dialog, new Event('webkitAnimationEnd'));
  await waitFor(() =>
    expect(screen.queryByRole('dialog', { name: 'New Department' })).not.toBeInTheDocument(),
  );
}

describe('WorkspaceSettingsPage', () => {
  // 2.2 opens only after Setup (KAN-119): the company information section loads a set-up Workspace.
  beforeEach(() => {
    server.use(
      http.get('*/api/v1/workspace', () =>
        HttpResponse.json({
          companyName: 'Example Company',
          jiraSiteUrl: 'https://company.atlassian.net',
          serviceAccountEmail: 'service@company.example',
        }),
      ),
    );
  });

  it('AC-002.1 shows New Department and the Active Departments in a Name-only table', async () => {
    renderApp('/workspace-settings', { role: 'ADMIN' });
    const table = await screen.findByRole('table', { name: 'Departments' }, { timeout: 5000 });
    expect(screen.getByRole('button', { name: 'New Department' })).toBeInTheDocument();
    expect(within(table).getAllByRole('columnheader')).toHaveLength(1);
    expect(within(table).getByRole('columnheader', { name: 'Name' })).toBeInTheDocument();
    expect(await within(table).findByRole('row', { name: 'IT' })).toBeInTheDocument();
    expect(within(table).getByRole('row', { name: 'HR' })).toBeInTheDocument();
  });

  it('AC-002.2 shows Loading ... in place of the table and keeps New Department', async () => {
    server.use(
      http.get(DEPARTMENT_LIST_URL, async () => {
        await delay('infinite');
        return HttpResponse.json([]);
      }),
    );
    renderApp('/workspace-settings', { role: 'ADMIN' });
    expect(
      await screen.findByRole('button', { name: 'New Department' }, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(screen.getByText('Loading ...')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Departments' })).not.toBeInTheDocument();
  });

  it('AC-002.3 shows MSG06 with retry instead of the table and keeps New Department', async () => {
    server.use(http.get(DEPARTMENT_LIST_URL, () => problem(500, 'MSG06'), { once: true }));
    const user = userEvent.setup();
    renderApp('/workspace-settings', { role: 'ADMIN' });
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG06'));
    expect(screen.queryByRole('table', { name: 'Departments' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New Department' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    const table = await screen.findByRole('table', { name: 'Departments' });
    expect(await within(table).findByRole('row', { name: 'IT' })).toBeInTheDocument();
  });

  it('AC-002.4 shows MSG04 inside the table when there are no Active Departments', async () => {
    server.use(http.get(DEPARTMENT_LIST_URL, () => HttpResponse.json([])));
    renderApp('/workspace-settings', { role: 'ADMIN' });
    const table = await screen.findByRole('table', { name: 'Departments' }, { timeout: 5000 });
    expect(within(table).getByText(msg('MSG04'))).toBeInTheDocument();
  });

  it('AC-002.5 opens New Department with an empty required name, Save and Cancel', async () => {
    const user = userEvent.setup();
    renderApp('/workspace-settings', { role: 'ADMIN' });
    const dialog = await openDepartmentModal(user);
    expect(departmentName(dialog)).toHaveValue('');
    expect(departmentName(dialog)).toBeRequired();
    expect(saveButton(dialog)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
  });

  it('AC-002.6 shows MSG01 below empty or whitespace-only names without a POST', async () => {
    const requests = recordDepartmentPosts();
    const user = userEvent.setup();
    renderApp('/workspace-settings', { role: 'ADMIN' });
    const dialog = await openDepartmentModal(user);
    const input = departmentName(dialog);
    for (const value of ['', '   ']) {
      await user.clear(input);
      if (value) await user.type(input, value);
      await user.click(saveButton(dialog));
      const message = msg('MSG01', { field_name: 'Department name' });
      expect(await within(dialog).findByText(message)).toBeInTheDocument();
      expect(input).toHaveAccessibleDescription(message);
      expect(screen.getByRole('dialog', { name: 'New Department' })).toBe(dialog);
      expect(requests).toHaveLength(0);
    }
  });

  it('AC-002.7 shows MSG02 below a 51-character name and keeps every character without a POST', async () => {
    const requests = recordDepartmentPosts();
    const user = userEvent.setup();
    renderApp('/workspace-settings', { role: 'ADMIN' });
    const dialog = await openDepartmentModal(user);
    const input = departmentName(dialog);
    const value = 'x'.repeat(51);
    await user.click(input);
    await user.paste(value);
    await user.click(saveButton(dialog));
    const message = msg('MSG02', { field_name: 'Department name', max_length: 50 });
    expect(await within(dialog).findByText(message)).toBeInTheDocument();
    expect(input).toHaveAccessibleDescription(message);
    expect(input).toHaveValue(value);
    expect(screen.getByRole('dialog', { name: 'New Department' })).toBe(dialog);
    expect(requests).toHaveLength(0);
  });

  it('AC-002.8 sends exactly the trimmed name in the POST body', async () => {
    const bodies: unknown[] = [];
    server.use(
      http.post(DEPARTMENTS_URL, async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ departmentId: 'd-2', name: 'IT Support' }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderApp('/workspace-settings', { role: 'ADMIN' });
    const dialog = await openDepartmentModal(user);
    await user.click(departmentName(dialog));
    await user.paste('  IT Support  ');
    await user.click(saveButton(dialog));
    await waitFor(() => expect(bodies).toEqual([{ name: 'IT Support' }]));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await expectModalClosed();
  });

  it('AC-002.8 sends only one POST after repeated Save clicks while the request is pending', async () => {
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    const requests = recordDepartmentPosts();
    server.use(
      http.post(DEPARTMENTS_URL, async () => {
        await pending;
        return HttpResponse.json({ departmentId: 'd-2', name: 'IT Support' }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    const { queryClient } = renderApp('/workspace-settings', { role: 'ADMIN' });
    try {
      const dialog = await openDepartmentModal(user);
      await user.click(departmentName(dialog));
      await user.paste('IT Support');
      const save = saveButton(dialog);
      await user.dblClick(save);
      await waitFor(() => expect(requests).toHaveLength(1));
      await user.click(save);
      expect(requests).toHaveLength(1);
      expect(screen.getByRole('dialog', { name: 'New Department' })).toBe(dialog);
    } finally {
      finish?.();
      await waitFor(() => expect(queryClient.isMutating()).toBe(0));
    }
    await expectModalClosed();
    expect(requests).toHaveLength(1);
  });

  it('AC-002.9 closes on 201, shows MSG05 and refreshes the shared Departments query and table', async () => {
    const departments: DepartmentRef[] = [{ departmentId: 'd-1', name: 'IT' }];
    let getCalls = 0;
    let getCallsAtPost = 0;
    server.use(
      http.get(DEPARTMENT_LIST_URL, () => {
        getCalls++;
        return HttpResponse.json(departments);
      }),
      http.post(DEPARTMENTS_URL, () => {
        getCallsAtPost = getCalls;
        const created: DepartmentRef = { departmentId: 'd-2', name: 'IT Support' };
        departments.push(created);
        return HttpResponse.json(created, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    const { queryClient } = renderApp('/workspace-settings', { role: 'ADMIN' });
    const table = await screen.findByRole('table', { name: 'Departments' }, { timeout: 5000 });
    expect(await within(table).findByRole('row', { name: 'IT' })).toBeInTheDocument();
    const dialog = await openDepartmentModal(user);
    await user.click(departmentName(dialog));
    await user.paste('IT Support');
    await user.click(saveButton(dialog));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await expectModalClosed();
    const refreshedTable = screen.getByRole('table', { name: 'Departments' });
    expect(
      await within(refreshedTable).findByRole('row', { name: 'IT Support' }),
    ).toBeInTheDocument();
    expect(getCallsAtPost).toBeGreaterThan(0);
    expect(getCalls).toBeGreaterThan(getCallsAtPost);
    expect(queryClient.getQueryData(getGetUserDepartmentsQueryKey())).toEqual(departments);
  });

  it('AC-002.10 shows MSG43 below the name on 409 and keeps the popup and typed name', async () => {
    server.use(
      http.post(DEPARTMENTS_URL, () =>
        problem(409, 'MSG43', { fieldErrors: [{ field: 'name', code: 'MSG43' }] }),
      ),
    );
    const user = userEvent.setup();
    renderApp('/workspace-settings', { role: 'ADMIN' });
    const dialog = await openDepartmentModal(user);
    const input = departmentName(dialog);
    await user.click(input);
    await user.paste('IT');
    await user.click(saveButton(dialog));
    expect(await within(dialog).findByText(msg('MSG43'))).toBeInTheDocument();
    expect(input).toHaveAccessibleDescription(msg('MSG43'));
    expect(input).toHaveValue('IT');
    expect(screen.getByRole('dialog', { name: 'New Department' })).toBe(dialog);
  });

  it.each([
    [403, 'MSG08'],
    [500, 'MSG06'],
  ] as const)('AC-002.11 shows %s %s and keeps the popup and typed name', async (status, code) => {
    server.use(http.post(DEPARTMENTS_URL, () => problem(status, code)));
    const user = userEvent.setup();
    renderApp('/workspace-settings', { role: 'ADMIN' });
    const dialog = await openDepartmentModal(user);
    const input = departmentName(dialog);
    await user.click(input);
    await user.paste('IT Support');
    await user.click(saveButton(dialog));
    expect(await screen.findByText(msg(code))).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'New Department' })).toBe(dialog);
    expect(input).toHaveValue('IT Support');
  });

  it.each(['Cancel', 'Close'] as const)(
    'AC-002.12 closes with %s without a POST and reopens with an empty name',
    async (action) => {
      const requests = recordDepartmentPosts();
      const user = userEvent.setup();
      renderApp('/workspace-settings', { role: 'ADMIN' });
      const dialog = await openDepartmentModal(user);
      await user.click(departmentName(dialog));
      await user.paste('Unsaved Department');
      await user.click(within(dialog).getByRole('button', { name: action }));
      await expectModalClosed();
      expect(requests).toHaveLength(0);
      const reopened = await openDepartmentModal(user);
      expect(departmentName(reopened)).toHaveValue('');
      expect(requests).toHaveLength(0);
    },
  );

  it('AC-002.13 shows MSG08 and no New Department button for an Employee', async () => {
    renderApp('/workspace-settings', { role: 'EMPLOYEE' });
    expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New Department' })).not.toBeInTheDocument();
  });

  it('AC-002.14 ignores Cancel and Close while saving and closes after success', async () => {
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    const requests = recordDepartmentPosts();
    server.use(
      http.post(DEPARTMENTS_URL, async () => {
        await pending;
        return HttpResponse.json({ departmentId: 'd-2', name: 'IT Support' }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    const { queryClient } = renderApp('/workspace-settings', { role: 'ADMIN' });
    try {
      const dialog = await openDepartmentModal(user);
      await user.click(departmentName(dialog));
      await user.paste('IT Support');
      await user.click(saveButton(dialog));
      await waitFor(() => expect(requests).toHaveLength(1));
      await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
      expect(screen.getByRole('dialog', { name: 'New Department' })).toBe(dialog);
      await user.click(within(dialog).getByRole('button', { name: 'Close' }));
      expect(screen.getByRole('dialog', { name: 'New Department' })).toBe(dialog);
      expect(departmentName(dialog)).toHaveValue('IT Support');
      expect(requests).toHaveLength(1);
    } finally {
      finish?.();
      await waitFor(() => expect(queryClient.isMutating()).toBe(0));
    }
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await expectModalClosed();
  });
});
