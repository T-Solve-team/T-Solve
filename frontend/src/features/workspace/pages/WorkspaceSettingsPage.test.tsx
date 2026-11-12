import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { WorkspaceInfo } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';

const INFO: WorkspaceInfo = {
  companyName: 'Example Company',
  jiraSiteUrl: 'https://company.atlassian.net',
  serviceAccountEmail: 'service@company.example',
};
const LABELS = {
  companyName: 'Company name',
  serviceAccountEmail: 'Service account email',
  serviceAccountToken: 'Service account API token',
} as const;
const HINT = 'Leave empty to keep the current token.';

async function findSave() {
  return screen.findByRole('button', { name: 'Save' }, { timeout: 5000 });
}

async function replace(user: ReturnType<typeof userEvent.setup>, label: string, value: string) {
  const input = screen.getByLabelText(label);
  await user.clear(input);
  await user.click(input);
  await user.paste(value);
}

describe('WorkspaceSettingsPage', () => {
  let getCalls = 0;
  let putBodies: unknown[] = [];

  beforeAll(async () => {
    await import('./WorkspaceSettingsPage');
  });

  beforeEach(() => {
    getCalls = 0;
    putBodies = [];
    server.use(
      http.get('*/api/v1/workspace', () => {
        getCalls++;
        return HttpResponse.json(INFO);
      }),
      http.put('*/api/v1/workspace', async ({ request }) => {
        const body = await request.json();
        putBodies.push(body);
        return HttpResponse.json({ ...INFO, ...(body as object) });
      }),
    );
  });

  it('AC-006.1 loads the Workspace: editable company name and email, read-only Jira site', async () => {
    renderApp(paths.workspaceSettings, { role: 'ADMIN' });
    await findSave();
    expect(screen.getByLabelText(LABELS.companyName)).toHaveValue(INFO.companyName);
    expect(screen.getByLabelText(LABELS.companyName)).toBeEnabled();
    expect(screen.getByLabelText(LABELS.serviceAccountEmail)).toHaveValue(INFO.serviceAccountEmail);
    expect(screen.getByLabelText(LABELS.serviceAccountEmail)).toBeEnabled();
    expect(screen.getByText(INFO.jiraSiteUrl)).toBeInTheDocument();
    expect(screen.queryByDisplayValue(INFO.jiraSiteUrl)).not.toBeInTheDocument();
    expect(getCalls).toBe(1);
  });

  it('AC-006.2 starts the masked token field empty with the keep hint and never shows a stored token', async () => {
    server.use(
      http.get('*/api/v1/workspace', () =>
        HttpResponse.json({ ...INFO, serviceAccountToken: 'stored-secret-token' }),
      ),
    );
    renderApp(paths.workspaceSettings, { role: 'ADMIN' });
    await findSave();
    const token = screen.getByLabelText(LABELS.serviceAccountToken);
    expect(token).toHaveAttribute('type', 'password');
    expect(token).toHaveValue('');
    expect(token).not.toBeRequired();
    expect(screen.getByText(HINT)).toBeInTheDocument();
    expect(screen.queryByDisplayValue('stored-secret-token')).not.toBeInTheDocument();
    expect(screen.queryByText('stored-secret-token')).not.toBeInTheDocument();
  });

  it('AC-006.3 sends the edited values and a typed token, shows MSG05 and refreshes the Workspace', async () => {
    const user = userEvent.setup();
    renderApp(paths.workspaceSettings, { role: 'ADMIN' });
    await findSave();
    await replace(user, LABELS.companyName, 'New Company');
    await replace(user, LABELS.serviceAccountEmail, 'new@company.example');
    await replace(user, LABELS.serviceAccountToken, 'new-fake-token');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    expect(putBodies).toEqual([
      {
        companyName: 'New Company',
        serviceAccountEmail: 'new@company.example',
        serviceAccountToken: 'new-fake-token',
      },
    ]);
    await waitFor(() => expect(getCalls).toBe(2));
    await waitFor(() => expect(screen.getByLabelText(LABELS.serviceAccountToken)).toHaveValue(''));
  });

  it('AC-006.3 omits serviceAccountToken from the PUT body when the token field is empty (edge 19)', async () => {
    const user = userEvent.setup();
    renderApp(paths.workspaceSettings, { role: 'ADMIN' });
    await findSave();
    await replace(user, LABELS.companyName, 'New Company');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    expect(putBodies).toHaveLength(1);
    expect(putBodies[0]).toEqual({
      companyName: 'New Company',
      serviceAccountEmail: INFO.serviceAccountEmail,
    });
    expect(putBodies[0]).not.toHaveProperty('serviceAccountToken');
  });

  it.each([
    ['MSG01', 'companyName', { field_name: LABELS.companyName }],
    ['MSG02', 'companyName', { field_name: LABELS.companyName, max_length: 100 }],
    ['MSG03', 'serviceAccountEmail', {}],
    ['MSG156', 'serviceAccountToken', { tracker_name: 'Jira' }],
  ] as const)(
    'AC-006.4 displays server %s under %s and keeps the values entered',
    async (code, field, params) => {
      server.use(
        http.put('*/api/v1/workspace', () =>
          HttpResponse.json(
            { status: 400, code, fieldErrors: [{ field, code, params }] },
            { status: 400 },
          ),
        ),
      );
      const user = userEvent.setup();
      renderApp(paths.workspaceSettings, { role: 'ADMIN' });
      await findSave();
      await replace(user, LABELS.companyName, 'Typed Company');
      await replace(user, LABELS.serviceAccountToken, 'typed-fake-token');
      await user.click(screen.getByRole('button', { name: 'Save' }));
      const message = msg(code, params);
      await waitFor(() =>
        expect(screen.getByLabelText(LABELS[field])).toHaveAccessibleDescription(
          expect.stringContaining(message),
        ),
      );
      expect(screen.getByLabelText(LABELS.companyName)).toHaveValue('Typed Company');
      expect(screen.getByLabelText(LABELS.serviceAccountEmail)).toHaveValue(
        INFO.serviceAccountEmail,
      );
      expect(screen.getByLabelText(LABELS.serviceAccountToken)).toHaveValue('typed-fake-token');
      expect(screen.queryByText(msg('MSG05'))).not.toBeInTheDocument();
      expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
    },
  );

  it('AC-006.5 Cancel restores the loaded values and sends nothing', async () => {
    const user = userEvent.setup();
    renderApp(paths.workspaceSettings, { role: 'ADMIN' });
    await findSave();
    await replace(user, LABELS.companyName, 'Changed Company');
    await replace(user, LABELS.serviceAccountEmail, 'changed@company.example');
    await replace(user, LABELS.serviceAccountToken, 'changed-fake-token');
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByLabelText(LABELS.companyName)).toHaveValue(INFO.companyName);
    expect(screen.getByLabelText(LABELS.serviceAccountEmail)).toHaveValue(INFO.serviceAccountEmail);
    expect(screen.getByLabelText(LABELS.serviceAccountToken)).toHaveValue('');
    expect(putBodies).toHaveLength(0);
  });

  it('AC-006.6 shows the shared error state when loading fails and retries', async () => {
    let fails = true;
    server.use(
      http.get('*/api/v1/workspace', () => (fails ? problem(500) : HttpResponse.json(INFO))),
    );
    const user = userEvent.setup();
    renderApp(paths.workspaceSettings, { role: 'ADMIN' });
    const alert = await screen.findByRole('alert', {}, { timeout: 5000 });
    expect(alert).toHaveTextContent(msg('MSG06'));
    fails = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    await findSave();
    expect(screen.getByLabelText(LABELS.companyName)).toHaveValue(INFO.companyName);
  });
});
