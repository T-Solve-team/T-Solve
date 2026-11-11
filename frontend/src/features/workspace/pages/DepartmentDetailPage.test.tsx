import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import {
  createDepartment,
  getDepartment,
  getGetUserDepartmentsQueryKey,
  getGetUsersQueryKey,
  updateDepartment,
  type DepartmentRef,
} from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';
import { resetMockData } from '../mocks/handlers';

const IT_ID = '00000000-0000-4000-8000-0000000000d1';
const HR_ID = '00000000-0000-4000-8000-0000000000d2';
const ONE_URL = `*/api/v1/departments/${IT_ID}`;

const nameField = () => screen.findByRole('textbox', { name: 'Department name' });
const editButton = () => screen.findByRole('button', { name: 'Edit' }, { timeout: 5000 });
const saveButton = () => screen.getByRole('button', { name: 'Save' });

type User = ReturnType<typeof userEvent.setup>;

function openIt(role: 'ADMIN' | 'EMPLOYEE' = 'ADMIN') {
  return renderApp(paths.departmentDetail(IT_ID), { role });
}

async function startEdit(user: User) {
  await user.click(await editButton());
  return nameField();
}

async function typeName(user: User, text: string) {
  const input = await nameField();
  await user.clear(input);
  if (text) await user.paste(text);
  return input;
}

function recordPuts() {
  const requests: Request[] = [];
  const onStart = ({ request }: { request: Request }) => {
    if (
      request.method === 'PUT' &&
      new URL(request.url).pathname === `/api/v1/departments/${IT_ID}`
    )
      requests.push(request);
  };
  server.events.on('request:start', onStart);
  onTestFinished(() => server.events.removeListener('request:start', onStart));
  return requests;
}

describe('DepartmentDetailPage', () => {
  it.each([
    [IT_ID, 'IT'],
    [HR_ID, 'HR'],
  ])(
    'AC-004.2 shows %s %s, Edit and the back link when opened directly by URL',
    async (id, name) => {
      renderApp(paths.departmentDetail(id), { role: 'ADMIN' });
      expect(await editButton()).toBeInTheDocument();
      expect(screen.getByText(name)).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Back to Workspace Settings' })).toHaveAttribute(
        'href',
        paths.workspaceSettings,
      );
    },
  );

  it('AC-004.3 shows the loading state while the GET is held', async () => {
    let getCalls = 0;
    server.use(
      http.get(ONE_URL, async () => {
        getCalls++;
        await delay('infinite');
        return HttpResponse.json({});
      }),
    );
    openIt();
    await waitFor(() => expect(getCalls).toBe(1), { timeout: 5000 });
    expect(screen.getByText('Loading ...')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('AC-004.4 shows MSG04 for 404 with Try again and no Edit', async () => {
    server.use(http.get(ONE_URL, () => problem(404, 'MSG04')));
    openIt();
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('AC-004.4 shows MSG06 for 500 and retries', async () => {
    server.use(http.get(ONE_URL, () => problem(500, 'MSG06'), { once: true }));
    const user = userEvent.setup();
    openIt();
    expect(await screen.findByText(msg('MSG06'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await editButton()).toBeInTheDocument();
  });

  it('AC-004.5 Edit shows the field with the current name, Save and Cancel, and hides Edit', async () => {
    const user = userEvent.setup();
    openIt();
    const input = await startEdit(user);
    expect(input).toHaveValue('IT');
    expect(input).toBeRequired();
    expect(saveButton()).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('AC-004.5 Edit focuses the name field without moving focus on first load', async () => {
    const user = userEvent.setup();
    openIt();
    expect(await editButton()).not.toHaveFocus();
    expect(await startEdit(user)).toHaveFocus();
  });

  it.each([[''], ['   ']])(
    'AC-004.6 shows MSG01 for %j, sends no PUT and keeps the form',
    async (text) => {
      const requests = recordPuts();
      const user = userEvent.setup();
      openIt();
      await startEdit(user);
      const input = await typeName(user, text);
      await user.click(saveButton());
      const message = msg('MSG01', { field_name: 'Department name' });
      expect(await screen.findByText(message)).toBeInTheDocument();
      expect(input).toHaveAccessibleDescription(message);
      expect(input).toBeInTheDocument();
      expect(requests).toHaveLength(0);
      expect(saveButton()).toBeInTheDocument();
    },
  );

  it('AC-004.7 shows MSG02 for 51 characters, keeps the text and sends no PUT', async () => {
    const requests = recordPuts();
    const user = userEvent.setup();
    openIt();
    await startEdit(user);
    const name = 'a'.repeat(51);
    const input = await typeName(user, name);
    await user.click(saveButton());
    const message = msg('MSG02', { field_name: 'Department name', max_length: 50 });
    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(input).toHaveAccessibleDescription(message);
    expect(input).toHaveValue(name);
    expect(saveButton()).toBeInTheDocument();
    expect(requests).toHaveLength(0);
  });

  it('AC-004.8 sends the trimmed name in one PUT however many times Save is clicked', async () => {
    const bodies: unknown[] = [];
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    server.use(
      http.put(ONE_URL, async ({ request }) => {
        bodies.push(await request.json());
        await pending;
        return HttpResponse.json<DepartmentRef>({ departmentId: IT_ID, name: 'IT Support' });
      }),
    );
    const user = userEvent.setup();
    const { queryClient } = openIt();
    try {
      await startEdit(user);
      const input = await typeName(user, '  IT Support  ');
      const save = saveButton();
      await user.dblClick(save);
      await waitFor(() => expect(bodies).toEqual([{ name: 'IT Support' }]));
      expect(save).toBeDisabled();
      await user.click(save);
      expect(input).toBeInTheDocument();
      expect(bodies).toHaveLength(1);
    } finally {
      finish?.();
      await waitFor(() => expect(queryClient.isMutating()).toBe(0));
    }
    expect(await editButton()).toBeInTheDocument();
    expect(bodies).toHaveLength(1);
  });

  it('AC-004.9 waits for the refreshed GET, closes with MSG05 and the stored name, and invalidates every query', async () => {
    let stored = 'IT';
    let gets = 0;
    let getsAtPut = 0;
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    server.use(
      http.get(ONE_URL, async () => {
        gets++;
        if (getsAtPut > 0) await pending;
        return HttpResponse.json<DepartmentRef>({ departmentId: IT_ID, name: stored });
      }),
      http.put(ONE_URL, () => {
        getsAtPut = gets;
        stored = 'IT Support';
        return HttpResponse.json<DepartmentRef>({ departmentId: IT_ID, name: stored });
      }),
    );
    const user = userEvent.setup();
    const { queryClient } = openIt();
    const departmentsKey = getGetUserDepartmentsQueryKey();
    const usersKey = getGetUsersQueryKey({ q: 'cached' });
    queryClient.setQueryData(departmentsKey, [{ departmentId: IT_ID, name: 'IT' }]);
    queryClient.setQueryData(usersKey, { content: [] });
    try {
      await startEdit(user);
      const input = await typeName(user, 'it support');
      await user.click(saveButton());
      expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
      await waitFor(() => expect(gets).toBeGreaterThan(getsAtPut));
      expect(getsAtPut).toBeGreaterThan(0);
      expect(input).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Save/ })).toBeDisabled();
      expect(queryClient.getQueryState(departmentsKey)?.isInvalidated).toBe(true);
      expect(queryClient.getQueryState(usersKey)?.isInvalidated).toBe(true);
    } finally {
      finish?.();
      await waitFor(() => expect(queryClient.isMutating()).toBe(0));
    }
    expect(await screen.findByText('IT Support')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Department name' })).not.toBeInTheDocument();
    expect(await editButton()).toBeInTheDocument();
    expect(await editButton()).toHaveFocus();
    await waitFor(() => expect(gets).toBeGreaterThan(getsAtPut));
  });

  it('AC-004.9 keeps the saved name when the refetch after a save fails', async () => {
    let gets = 0;
    server.use(
      http.get(ONE_URL, () => {
        gets++;
        if (gets > 1) return problem(500, 'MSG06');
        return HttpResponse.json<DepartmentRef>({ departmentId: IT_ID, name: 'IT' });
      }),
      http.put(ONE_URL, () =>
        HttpResponse.json<DepartmentRef>({ departmentId: IT_ID, name: 'IT Support' }),
      ),
    );
    const user = userEvent.setup();
    const { queryClient } = openIt();
    await startEdit(user);
    await typeName(user, 'it support');
    await user.click(saveButton());
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await waitFor(() => expect(queryClient.isMutating()).toBe(0));
    expect(gets).toBeGreaterThan(1);
    expect(screen.queryByText('IT Support')).toBeInTheDocument();
    expect(await editButton()).toHaveFocus();
    expect(screen.queryByRole('textbox', { name: 'Department name' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
    expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
  });

  it('AC-004.10 shows MSG43 below the field on 409 and keeps the form and text', async () => {
    server.use(
      http.put(ONE_URL, () =>
        problem(409, 'MSG43', { fieldErrors: [{ field: 'name', code: 'MSG43' }] }),
      ),
    );
    const user = userEvent.setup();
    openIt();
    await startEdit(user);
    const input = await typeName(user, 'HR');
    await user.click(saveButton());
    expect(await screen.findByText(msg('MSG43'))).toBeInTheDocument();
    expect(input).toHaveAccessibleDescription(msg('MSG43'));
    expect(input).toHaveValue('HR');
    expect(saveButton()).toBeInTheDocument();
  });

  it.each([
    [404, 'MSG04'],
    [403, 'MSG08'],
    [500, 'MSG06'],
  ] as const)('AC-004.11 shows %s %s on Save and keeps the form and text', async (status, code) => {
    server.use(http.put(ONE_URL, () => problem(status, code)));
    const user = userEvent.setup();
    openIt();
    await startEdit(user);
    const input = await typeName(user, 'IT Support');
    await user.click(saveButton());
    expect(await screen.findByText(msg(code))).toBeInTheDocument();
    expect(input).toHaveValue('IT Support');
    expect(saveButton()).toBeInTheDocument();
  });

  it('AC-004.12 Cancel sends nothing, shows the current name, and the next Edit shows it again', async () => {
    const requests = recordPuts();
    const user = userEvent.setup();
    openIt();
    await startEdit(user);
    await typeName(user, 'Typed');
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(await editButton()).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Department name' })).not.toBeInTheDocument();
    expect(screen.getByText('IT')).toBeInTheDocument();
    expect(await startEdit(user)).toHaveValue('IT');
    expect(requests).toHaveLength(0);
  });

  it('AC-004.12 Cancel returns focus to Edit', async () => {
    const user = userEvent.setup();
    openIt();
    await startEdit(user);
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(await editButton()).toHaveFocus();
  });

  it('AC-004.12 reopens with the saved current name after Cancel, with no stale errors', async () => {
    const requests = recordPuts();
    const user = userEvent.setup();
    openIt();
    await startEdit(user);
    await typeName(user, 'IT Support');
    await user.click(saveButton());
    expect(await editButton()).toBeInTheDocument();
    await startEdit(user);
    const input = await typeName(user, 'HR');
    await user.click(saveButton());
    expect(await screen.findByText(msg('MSG43'))).toBeInTheDocument();
    expect(input).toHaveValue('HR');
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(await screen.findByText('IT Support')).toBeInTheDocument();
    expect(await startEdit(user)).toHaveValue('IT Support');
    expect(screen.queryByText(msg('MSG43'))).not.toBeInTheDocument();
    expect(requests).toHaveLength(2);
  });

  it('AC-004.13 ignores Cancel while a save is running', async () => {
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    const requests = recordPuts();
    server.use(
      http.put(ONE_URL, async () => {
        await pending;
        return HttpResponse.json<DepartmentRef>({ departmentId: IT_ID, name: 'IT Support' });
      }),
    );
    const user = userEvent.setup();
    const { queryClient } = openIt();
    try {
      await startEdit(user);
      const input = await typeName(user, 'IT Support');
      await user.click(saveButton());
      await waitFor(() => expect(requests).toHaveLength(1));
      await user.click(screen.getByRole('button', { name: 'Cancel' }));
      expect(input).toBeInTheDocument();
      expect(input).toHaveValue('IT Support');
      expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    } finally {
      finish?.();
      await waitFor(() => expect(queryClient.isMutating()).toBe(0));
    }
    expect(await editButton()).toBeInTheDocument();
  });

  it('AC-004.14 shows MSG08 and no Edit to a user without the Admin role', async () => {
    openIt('EMPLOYEE');
    expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });
});

describe('Department mock handlers', () => {
  it('returns MSG04 for an unknown Department on GET and PUT', async () => {
    const id = '00000000-0000-4000-8000-0000000000ff';
    await expect(getDepartment(id)).rejects.toMatchObject({ status: 404, code: 'MSG04' });
    await expect(updateDepartment(id, { name: 'Support' })).rejects.toMatchObject({
      status: 404,
      code: 'MSG04',
    });
  });

  it.each([
    ['', 'MSG01'],
    ['   ', 'MSG01'],
    ['a'.repeat(51), 'MSG02'],
  ])('validates %j with %s on POST and PUT', async (name, code) => {
    for (const save of [
      () => createDepartment({ name }),
      () => updateDepartment(IT_ID, { name }),
    ]) {
      await expect(save()).rejects.toMatchObject({
        status: 400,
        code,
        fieldErrors: [{ field: 'name', code }],
      });
    }
    expect(await getDepartment(IT_ID)).toEqual({ departmentId: IT_ID, name: 'IT' });
  });

  it('reserves seeded and newly created names ignoring case, and allows a case-only rename of itself', async () => {
    const conflict = {
      status: 409,
      code: 'MSG43',
      fieldErrors: [{ field: 'name', code: 'MSG43' }],
    };
    await expect(createDepartment({ name: ' it ' })).rejects.toMatchObject(conflict);
    await expect(updateDepartment(IT_ID, { name: ' hr ' })).rejects.toMatchObject(conflict);
    const created = await createDepartment({ name: '  Support  ' });
    expect(created.name).toBe('Support');
    await expect(updateDepartment(IT_ID, { name: 'support' })).rejects.toMatchObject(conflict);
    expect(await updateDepartment(IT_ID, { name: ' it ' })).toEqual({
      departmentId: IT_ID,
      name: 'it',
    });
    expect(await getDepartment(IT_ID)).toEqual({ departmentId: IT_ID, name: 'it' });
  });

  it('restores both seeds and removes created Departments on reset', async () => {
    const created = await createDepartment({ name: 'Support' });
    await updateDepartment(IT_ID, { name: 'Engineering' });
    await updateDepartment(HR_ID, { name: 'People' });
    resetMockData();
    expect(await getDepartment(IT_ID)).toEqual({ departmentId: IT_ID, name: 'IT' });
    expect(await getDepartment(HR_ID)).toEqual({ departmentId: HR_ID, name: 'HR' });
    await expect(getDepartment(created.departmentId)).rejects.toMatchObject({
      status: 404,
      code: 'MSG04',
    });
  });
});
