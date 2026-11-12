import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MOCK_USERS } from '@/mocks/session';
import { server } from '@/mocks/server';
import type { WorkspaceSetupRequest } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { SESSION_QUERY_KEY, type Session } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';

const VALUES: WorkspaceSetupRequest = {
  companyName: 'Example Company',
  jiraSiteUrl: 'https://company.atlassian.net',
  serviceAccountEmail: 'service@company.example',
  serviceAccountToken: 'fake-test-api-token',
};
const LABELS = {
  companyName: 'Company name',
  jiraSiteUrl: 'Jira site address',
  serviceAccountEmail: 'Service account email',
  serviceAccountToken: 'Service account API token',
} as const;
const INFO = {
  companyName: VALUES.companyName,
  jiraSiteUrl: VALUES.jiraSiteUrl,
  serviceAccountEmail: VALUES.serviceAccountEmail,
};

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
  for (const field of Object.keys(LABELS) as (keyof typeof LABELS)[]) {
    await user.click(screen.getByLabelText(LABELS[field]));
    await user.paste(VALUES[field]);
  }
}

describe('WorkspaceSetupPage', () => {
  let workspaceReady = false;
  let meCalls = 0;

  beforeAll(async () => {
    await import('./WorkspaceSetupPage');
  });

  beforeEach(() => {
    workspaceReady = false;
    meCalls = 0;
    server.use(
      http.get('*/api/v1/me', () => {
        meCalls++;
        return HttpResponse.json({ ...MOCK_USERS.admin, workspaceReady });
      }),
    );
  });

  it('AC-007.1 opens Setup with MSG60 for an Admin before setup', async () => {
    const { router } = renderApp(paths.userList, { role: 'ADMIN' });
    expect(await screen.findByText(msg('MSG60'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(paths.workspaceSetup);
  });

  it('AC-007.2 shows four required fields, the Jira placeholder and a masked token', async () => {
    renderApp(paths.workspaceSetup, { role: 'ADMIN' });
    await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    for (const label of Object.values(LABELS)) {
      expect(screen.getByLabelText(label)).toBeRequired();
    }
    expect(screen.getByLabelText(LABELS.jiraSiteUrl)).toHaveAttribute(
      'placeholder',
      'https://company.atlassian.net',
    );
    expect(screen.getByLabelText(LABELS.serviceAccountToken)).toHaveAttribute('type', 'password');
  });

  it('AC-007.3 shows MSG01 for each empty or whitespace-only field without sending', async () => {
    let calls = 0;
    server.use(
      http.post('*/api/v1/workspace/setup', () => {
        calls++;
        return HttpResponse.json(INFO);
      }),
    );
    const user = userEvent.setup();
    renderApp(paths.workspaceSetup, { role: 'ADMIN' });
    const save = await screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
    await user.click(save);
    for (const label of Object.values(LABELS)) {
      expect(await screen.findByText(msg('MSG01', { field_name: label }))).toBeInTheDocument();
    }
    for (const label of Object.values(LABELS)) {
      await user.click(screen.getByLabelText(label));
      await user.paste('   ');
    }
    await user.click(save);
    for (const label of Object.values(LABELS)) {
      expect(await screen.findByText(msg('MSG01', { field_name: label }))).toBeInTheDocument();
    }
    expect(calls).toBe(0);
  });

  it.each([
    ['companyName', 100],
    ['jiraSiteUrl', 200],
    ['serviceAccountEmail', 100],
  ] as const)(
    'AC-007.2 validates the %s limit with MSG02 without truncating input',
    async (field, max) => {
      let calls = 0;
      server.use(
        http.post('*/api/v1/workspace/setup', () => {
          calls++;
          return HttpResponse.json(INFO);
        }),
      );
      const user = userEvent.setup();
      renderApp(paths.workspaceSetup, { role: 'ADMIN' });
      await fillForm(user);
      const input = screen.getByLabelText(LABELS[field]);
      const value = 'x'.repeat(max + 1);
      await user.clear(input);
      await user.paste(value);
      await user.click(screen.getByRole('button', { name: 'Save' }));
      expect(
        await screen.findByText(msg('MSG02', { field_name: LABELS[field], max_length: max })),
      ).toBeInTheDocument();
      expect(input).toHaveValue(value);
      expect(calls).toBe(0);
    },
  );

  it.each([
    ['MSG01', 'companyName', { field_name: LABELS.companyName }],
    ['MSG02', 'companyName', { field_name: LABELS.companyName, max_length: 100 }],
    ['MSG03', 'serviceAccountEmail', {}],
    ['MSG61', 'jiraSiteUrl', {}],
    ['MSG153', 'jiraSiteUrl', { tracker_name: 'Jira' }],
    ['MSG156', 'serviceAccountToken', { tracker_name: 'Jira' }],
  ] as const)(
    'AC-007.4 displays server %s under %s and keeps every value',
    async (code, field, params) => {
      server.use(
        http.post('*/api/v1/workspace/setup', () =>
          HttpResponse.json(
            { status: code === 'MSG153' ? 409 : 400, code, fieldErrors: [{ field, code, params }] },
            { status: code === 'MSG153' ? 409 : 400 },
          ),
        ),
      );
      const user = userEvent.setup();
      const { router } = renderApp(paths.workspaceSetup, { role: 'ADMIN' });
      await fillForm(user);
      await user.click(screen.getByRole('button', { name: 'Save' }));
      const message = msg(code, params);
      await waitFor(() =>
        expect(screen.getByLabelText(LABELS[field])).toHaveAccessibleDescription(message),
      );
      for (const key of Object.keys(LABELS) as (keyof typeof LABELS)[]) {
        expect(screen.getByLabelText(LABELS[key])).toHaveValue(VALUES[key]);
      }
      expect(router.state.location.pathname).toBe(paths.workspaceSetup);
      expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
    },
  );

  it('AC-007.5 sends the form, shows MSG05, refetches /me and opens User List', async () => {
    let body: unknown;
    server.use(
      http.post('*/api/v1/workspace/setup', async ({ request }) => {
        body = await request.json();
        workspaceReady = true;
        return HttpResponse.json(INFO, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    const { router, queryClient } = renderApp(paths.workspaceSetup, { role: 'ADMIN' });
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.pathname).toBe(paths.userList));
    expect(body).toEqual(VALUES);
    expect(meCalls).toBe(2);
    expect(queryClient.getQueryData<Session>(SESSION_QUERY_KEY)?.workspaceReady).toBe(true);
    expect(await screen.findByRole('menu', { name: 'Main menu' })).toBeInTheDocument();
  });

  it('AC-007.6 refetches /me and opens Settings without an error on WORKSPACE_ALREADY_SET_UP', async () => {
    server.use(
      http.post('*/api/v1/workspace/setup', () => {
        workspaceReady = true;
        return HttpResponse.json(
          { status: 409, code: 'WORKSPACE_ALREADY_SET_UP' },
          { status: 409 },
        );
      }),
    );
    const user = userEvent.setup();
    const { router, queryClient } = renderApp(paths.workspaceSetup, { role: 'ADMIN' });
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(paths.workspaceSettings));
    expect(meCalls).toBe(2);
    expect(queryClient.getQueryData<Session>(SESSION_QUERY_KEY)?.workspaceReady).toBe(true);
    expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
    expect(screen.queryByText(msg('MSG05'))).not.toBeInTheDocument();
  });

  it('AC-007.4 keeps input and allows retry after a server failure', async () => {
    let fails = true;
    server.use(
      http.post('*/api/v1/workspace/setup', () => {
        if (fails) return problem(500);
        workspaceReady = true;
        return HttpResponse.json(INFO, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderApp(paths.workspaceSetup, { role: 'ADMIN' });
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(screen.getByLabelText(LABELS.serviceAccountToken)).toHaveValue(
      VALUES.serviceAccountToken,
    );
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled());
    fails = false;
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
  });

  it('AC-007.7 disables Save while pending and sends once on a double click', async () => {
    let finish: (() => void) | undefined;
    let calls = 0;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    server.use(
      http.post('*/api/v1/workspace/setup', async () => {
        calls++;
        await pending;
        workspaceReady = true;
        return HttpResponse.json(INFO, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    const { queryClient } = renderApp(paths.workspaceSetup, { role: 'ADMIN' });
    await fillForm(user);
    const save = screen.getByRole('button', { name: 'Save' });
    try {
      await user.dblClick(save);
      await waitFor(() => expect(save).toBeDisabled());
      expect(save).toHaveAttribute('aria-busy', 'true');
      expect(calls).toBe(1);
    } finally {
      finish?.();
      await waitFor(() => expect(queryClient.isMutating()).toBe(0));
    }
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
  });
});
