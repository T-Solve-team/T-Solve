import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg, type MsgCode } from '@/shared/messages';
import { renderApp } from '@/test/render';

/** Add project popup on 3.1 (spec STORY-006): server errors of the connection test and Save. */

type User = ReturnType<typeof userEvent.setup>;
const PROJECTS = '/api/v1/projects';
const TEST = '/api/v1/projects/connection-test';
const JIRA = { tracker_name: 'Jira' };

function spyOnProjects() {
  const seen = { tests: 0, creates: 0 };
  server.events.on('request:start', ({ request }) => {
    const path = new URL(request.url).pathname;
    if (request.method !== 'POST') return;
    if (path === TEST) seen.tests += 1;
    if (path === PROJECTS) seen.creates += 1;
  });
  return seen;
}

/** The next POST to `path` answers `response` once; later requests reach the feature's mocks. */
function answerNext(path: string, response: () => Response) {
  server.use(http.post(`*${path}`, response, { once: true }));
}

async function renderPopup() {
  const user = userEvent.setup();
  const spy = spyOnProjects();
  renderApp('/jira-integration', { role: 'PROJECT_MANAGER' });
  await user.click(await screen.findByRole('button', { name: 'Add project' }, { timeout: 5000 }));
  const dialog = await screen.findByRole('dialog', { name: 'Add project' });
  return { user, spy, dialog };
}

const keyInput = (dialog: HTMLElement) =>
  within(dialog).getByRole('textbox', { name: 'Project key' });
const statuses = (dialog: HTMLElement) =>
  within(dialog).queryByRole('combobox', { name: 'Done statuses' });
const range = (dialog: HTMLElement) =>
  within(dialog).getByRole('spinbutton', { name: 'Review range' });

async function click(user: User, dialog: HTMLElement, name: string) {
  await user.click(within(dialog).getByRole('button', { name }));
}

async function enterKey(user: User, dialog: HTMLElement, key: string) {
  await user.clear(keyInput(dialog));
  await user.click(keyInput(dialog));
  await user.paste(key);
}

/** Tests `key` successfully and changes the range to 9, so retained settings can be checked. */
async function testAndEdit(user: User, dialog: HTMLElement, key: string) {
  await enterKey(user, dialog, key);
  await click(user, dialog, 'Test connection');
  await within(dialog).findByRole('combobox', { name: 'Done statuses' });
  await user.clear(range(dialog));
  await user.type(range(dialog), '9');
}

/** The tested settings and the key are still in the open popup (AC-006.20). */
function expectSettingsKept(dialog: HTMLElement, key: string) {
  expect(screen.getByRole('dialog', { name: 'Add project' })).toBe(dialog);
  expect(keyInput(dialog)).toHaveValue(key);
  expect(statuses(dialog)).toBeInTheDocument();
  expect(within(dialog).getByTitle('Done')).toBeInTheDocument();
  expect(within(dialog).getByTitle('Closed')).toBeInTheDocument();
  expect(range(dialog)).toHaveValue('9');
  expect(within(dialog).getByRole('button', { name: 'Save' })).toBeInTheDocument();
}

const MSG49 = msg('MSG49', { ...JIRA, department_name: 'Customer Support' });
const MSG48 = msg('MSG48', JIRA);

type NonField = { name: string; response: () => Response; code: MsgCode };
const NON_FIELD: NonField[] = [
  { name: '403 MSG08', response: () => problem(403, 'MSG08'), code: 'MSG08' },
  { name: '503 MSG06', response: () => problem(503, 'MSG06'), code: 'MSG06' },
  { name: '500 MSG06', response: () => problem(500, 'MSG06'), code: 'MSG06' },
  { name: 'a network failure', response: () => HttpResponse.error(), code: 'MSG06' },
  {
    name: 'an unknown code',
    response: () => HttpResponse.json({ status: 500, code: 'MSG999' }, { status: 500 }),
    code: 'MSG06',
  },
];

describe('AddProjectModal errors', () => {
  beforeAll(async () => {
    await import('../pages/JiraIntegrationPage');
  });
  afterEach(() => {
    server.events.removeAllListeners();
  });

  it('AC-006.16 AC-006.20 shows MSG49 of the connection test below Project key with the server params (row 36)', async () => {
    const { user, dialog } = await renderPopup();

    await enterKey(user, dialog, 'sup');
    await click(user, dialog, 'Test connection');
    expect(await within(dialog).findByText(MSG49)).toBeInTheDocument();
    expect(screen.getAllByText(MSG49)).toHaveLength(1);
    expect(keyInput(dialog)).toHaveValue('sup');
    expect(statuses(dialog)).not.toBeInTheDocument();
  });

  it('AC-006.16 AC-006.20 shows MSG49 of Save below Project key and keeps the tested settings', async () => {
    const { user, dialog, spy } = await renderPopup();
    await testAndEdit(user, dialog, 'new1');
    const params = { ...JIRA, department_name: 'Customer Support' };
    answerNext(PROJECTS, () =>
      problem(409, 'MSG49', {
        params,
        fieldErrors: [{ field: 'jiraProjectKey', code: 'MSG49', params }],
      }),
    );

    await click(user, dialog, 'Save');
    expect(await within(dialog).findByText(MSG49)).toBeInTheDocument();
    expect(screen.getAllByText(MSG49)).toHaveLength(1);
    expectSettingsKept(dialog, 'new1');
    expect(spy.creates).toBe(1);
  });

  it('AC-006.17 AC-006.20 shows MSG48 of the connection test inline in the popup', async () => {
    const { user, dialog } = await renderPopup();

    await enterKey(user, dialog, 'missing');
    await click(user, dialog, 'Test connection');
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(MSG48);
    expect(screen.getAllByText(MSG48)).toHaveLength(1);
    expect(keyInput(dialog)).toHaveValue('missing');
    expect(statuses(dialog)).not.toBeInTheDocument();
  });

  it('AC-006.17 AC-006.20 shows MSG48 of Save inline and keeps the tested settings', async () => {
    const { user, dialog } = await renderPopup();
    await testAndEdit(user, dialog, 'new1');
    answerNext(PROJECTS, () => problem(409, 'MSG48', { params: JIRA }));

    await click(user, dialog, 'Save');
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(MSG48);
    expect(screen.getAllByText(MSG48)).toHaveLength(1);
    expectSettingsKept(dialog, 'new1');
  });

  it('AC-006.8 AC-006.20 shows no settings of an earlier key when the next key fails (row 12)', async () => {
    const { user, dialog } = await renderPopup();
    await testAndEdit(user, dialog, 'new1');

    await enterKey(user, dialog, 'missing');
    await click(user, dialog, 'Test connection');
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(MSG48);
    expect(statuses(dialog)).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
  });

  it('AC-006.18 shows another field error of the connection test below Project key by code and params', async () => {
    const params = { field_name: 'jiraProjectKey', max_length: 20 };
    answerNext(TEST, () =>
      problem(400, 'MSG02', {
        params,
        fieldErrors: [{ field: 'jiraProjectKey', code: 'MSG02', params }],
      }),
    );
    const { user, dialog } = await renderPopup();

    await enterKey(user, dialog, 'new1');
    await click(user, dialog, 'Test connection');
    expect(await within(dialog).findByText(msg('MSG02', params))).toBeInTheDocument();
    expect(keyInput(dialog)).toHaveValue('new1');
  });

  it('AC-006.18 AC-006.20 shows Save field errors below Done statuses and Review range', async () => {
    const { user, dialog } = await renderPopup();
    await testAndEdit(user, dialog, 'new1');
    const required = { field_name: 'doneStatusIds' };
    answerNext(PROJECTS, () =>
      problem(400, 'MSG01', {
        params: required,
        fieldErrors: [
          { field: 'doneStatusIds', code: 'MSG01', params: required },
          { field: 'reviewRange', code: 'MSG44', params: {} },
        ],
      }),
    );

    await click(user, dialog, 'Save');
    expect(await within(dialog).findByText(msg('MSG01', required))).toBeInTheDocument();
    expect(within(dialog).getByText(msg('MSG44'))).toBeInTheDocument();
    expectSettingsKept(dialog, 'new1');
  });

  it.each(NON_FIELD)(
    'AC-006.19 AC-006.20 shows the connection test error $name once by its code and keeps the key (rows 21, 24)',
    async ({ response, code }) => {
      answerNext(TEST, response);
      const { user, dialog, spy } = await renderPopup();

      await enterKey(user, dialog, 'new1');
      await click(user, dialog, 'Test connection');
      expect(await screen.findByText(msg(code))).toBeInTheDocument();
      expect(screen.getAllByText(msg(code))).toHaveLength(1);
      expect(screen.getByRole('dialog', { name: 'Add project' })).toBe(dialog);
      expect(keyInput(dialog)).toHaveValue('new1');
      expect(statuses(dialog)).not.toBeInTheDocument();
      await waitFor(() => expect(keyInput(dialog)).toBeEnabled());
      expect(spy.tests).toBe(1);
    },
  );

  it.each(NON_FIELD)(
    'AC-006.19 AC-006.20 shows the Save error $name once by its code and keeps the settings (rows 21, 24)',
    async ({ response, code }) => {
      const { user, dialog, spy } = await renderPopup();
      await testAndEdit(user, dialog, 'new1');
      answerNext(PROJECTS, response);

      await click(user, dialog, 'Save');
      expect(await screen.findByText(msg(code))).toBeInTheDocument();
      expect(screen.getAllByText(msg(code))).toHaveLength(1);
      expectSettingsKept(dialog, 'new1');
      expect(spy.creates).toBe(1);
    },
  );
});
