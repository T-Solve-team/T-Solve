import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import type { ProjectConnectionTest } from '@/shared/api';
import { getTestProjectConnectionMockHandler } from '@/shared/api/generated/projects/projects.msw';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
import { MOCK_PROJECT_IDS } from '../mocks/handlers';

/** Add project popup on 3.1 (spec STORY-006): the normal flow. Errors are in AddProjectModalErrors.test.tsx. */

type User = ReturnType<typeof userEvent.setup>;
const PROJECTS = '/api/v1/projects';
const TEST = '/api/v1/projects/connection-test';
const MAX_RANGE = 2_147_483_647;

/** Records every Projects request the app sends; the feature's mocks still answer them. */
function spyOnProjects() {
  const seen = { lists: 0, tests: [] as unknown[], creates: [] as unknown[] };
  server.events.on('request:start', async ({ request }) => {
    const path = new URL(request.url).pathname;
    if (request.method === 'GET' && path === PROJECTS) seen.lists += 1;
    if (request.method !== 'POST') return;
    if (path === TEST) seen.tests.push(await request.clone().json());
    if (path === PROJECTS) seen.creates.push(await request.clone().json());
  });
  return seen;
}

/** Holds the next POST to `path` until the returned function is called, then lets the mocks answer it. */
function holdNext(path: string) {
  let release: () => void = () => undefined;
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  server.use(
    http.post(
      `*${path}`,
      async () => {
        await held;
        return undefined;
      },
      { once: true },
    ),
  );
  return () => release();
}

async function openPopup(user: User) {
  await user.click(await screen.findByRole('button', { name: 'Add project' }, { timeout: 5000 }));
  return screen.findByRole('dialog', { name: 'Add project' });
}

async function renderPopup() {
  const user = userEvent.setup();
  const spy = spyOnProjects();
  renderApp('/jira-integration', { role: 'PROJECT_MANAGER' });
  const dialog = await openPopup(user);
  return { user, spy, dialog };
}

const keyInput = (dialog: HTMLElement) =>
  within(dialog).getByRole('textbox', { name: 'Project key' });
const statuses = (dialog: HTMLElement) =>
  within(dialog).queryByRole('combobox', { name: 'Done statuses' });
const range = (dialog: HTMLElement) =>
  within(dialog).getByRole('spinbutton', { name: 'Review range' });
const button = (dialog: HTMLElement, name: string) =>
  within(dialog).queryByRole('button', { name });

async function enterKey(user: User, dialog: HTMLElement, key: string) {
  await user.clear(keyInput(dialog));
  await user.click(keyInput(dialog));
  await user.paste(key);
}

async function click(user: User, dialog: HTMLElement, name: string) {
  const target = button(dialog, name);
  if (!target) throw new Error(`No ${name} button`);
  await user.click(target);
}

/** Tests `key` and waits for the settings to appear. */
async function testKey(user: User, dialog: HTMLElement, key: string) {
  await enterKey(user, dialog, key);
  await click(user, dialog, 'Test connection');
  await within(dialog).findByRole('combobox', { name: 'Done statuses' });
}

async function setRange(user: User, dialog: HTMLElement, value: string) {
  await user.clear(range(dialog));
  if (value) await user.type(range(dialog), value);
}

/** Opens Done statuses and answers its options as label → selected. */
async function readOptions(user: User, dialog: HTMLElement) {
  const select = statuses(dialog);
  if (!select) throw new Error('No Done statuses');
  await user.click(select);
  const options = await screen.findAllByRole('option');
  return Object.fromEntries(
    options.map((o) => [o.textContent ?? '', o.getAttribute('aria-selected') === 'true']),
  );
}

async function toggleOption(user: User, label: string) {
  await user.click(await screen.findByRole('option', { name: label }));
}

/** The popup's leave animation ran (jsdom never ends a CSS animation, so the test fires the end event). */
async function expectClosed(dialog: HTMLElement) {
  await waitFor(() => expect(dialog).toHaveClass('ant-zoom-leave-active'));
  fireEvent(dialog, new Event('webkitAnimationEnd'));
  await waitFor(() =>
    expect(screen.queryByRole('dialog', { name: 'Add project' })).not.toBeInTheDocument(),
  );
}

function cellsOf(row: HTMLElement): string[] {
  return within(row)
    .getAllByRole('cell')
    .map((cell) => cell.textContent ?? '');
}

const projectKeyRequired = msg('MSG01', { field_name: 'Project key' });

describe('AddProjectModal', () => {
  beforeAll(async () => {
    await import('../pages/JiraIntegrationPage');
  });
  afterEach(() => {
    server.events.removeAllListeners();
  });

  it('AC-006.1 AC-006.24 opens "Add project" with a required empty Project key, Test connection and Cancel only', async () => {
    const { dialog, spy } = await renderPopup();

    expect(keyInput(dialog)).toHaveValue('');
    expect(within(dialog).getByText('Project key')).toHaveClass('ant-form-item-required');
    expect(button(dialog, 'Test connection')).toBeInTheDocument();
    expect(button(dialog, 'Cancel')).toBeInTheDocument();
    expect(button(dialog, 'Save')).not.toBeInTheDocument();
    expect(statuses(dialog)).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('spinbutton')).not.toBeInTheDocument();
    // Row 3: the key is the only input; no token, service-account or field mapping field.
    expect(within(dialog).getAllByRole('textbox')).toHaveLength(1);
    expect(dialog).not.toHaveTextContent(/token|service account|e-?mail|credential|mapping/i);
    expect(spy.tests).toHaveLength(0);
  });

  it('AC-006.2 shows MSG01 for an empty, whitespace-only or 21-space key and sends no request', async () => {
    const { user, dialog, spy } = await renderPopup();

    await click(user, dialog, 'Test connection');
    expect(await within(dialog).findByText(projectKeyRequired)).toBeInTheDocument();
    for (const blank of ['   ', ' '.repeat(21)]) {
      await enterKey(user, dialog, blank);
      await click(user, dialog, 'Test connection');
      expect(await within(dialog).findByText(projectKeyRequired)).toBeInTheDocument();
      expect(
        within(dialog).queryByText(msg('MSG02', { field_name: 'Project key', max_length: 20 })),
      ).not.toBeInTheDocument();
      expect(keyInput(dialog)).toHaveValue(blank);
    }
    expect(spy.tests).toHaveLength(0);
  });

  it('AC-006.3 shows MSG02 for 21 UTF-16 code units, keeps the pasted value and sends no request; 20 are sent', async () => {
    const { user, dialog, spy } = await renderPopup();
    const tooLong = `${'A'.repeat(19)}😀`; // 20 characters, 21 UTF-16 code units

    await enterKey(user, dialog, tooLong);
    await click(user, dialog, 'Test connection');
    expect(
      await within(dialog).findByText(msg('MSG02', { field_name: 'Project key', max_length: 20 })),
    ).toBeInTheDocument();
    expect(keyInput(dialog)).toHaveValue(tooLong);
    expect(spy.tests).toHaveLength(0);

    const longest = `${'A'.repeat(18)}😀`;
    await enterKey(user, dialog, longest);
    await click(user, dialog, 'Test connection');
    await waitFor(() => expect(spy.tests).toEqual([{ jiraProjectKey: longest }]));
  });

  it('AC-006.4 AC-006.5 sends one connection test with the original key and shows MSG47 once', async () => {
    const { user, dialog, spy } = await renderPopup();

    await testKey(user, dialog, ' new1 ');
    expect(spy.tests).toEqual([{ jiraProjectKey: ' new1 ' }]);
    expect(await screen.findByText(msg('MSG47', { tracker_name: 'Jira' }))).toBeInTheDocument();
    expect(screen.getAllByText(msg('MSG47', { tracker_name: 'Jira' }))).toHaveLength(1);
    expect(spy.creates).toHaveLength(0);
  });

  it('AC-006.6 AC-006.7 reveals every status preselected from doneStatusIds, the prefilled Review range and Save', async () => {
    const { user, dialog } = await renderPopup();

    await testKey(user, dialog, 'new1');
    expect(range(dialog)).toHaveValue('3');
    expect(button(dialog, 'Save')).toBeInTheDocument();
    expect(button(dialog, 'Test connection')).not.toBeInTheDocument();
    expect(await readOptions(user, dialog)).toEqual({
      'To Do': false,
      'In Progress': false,
      Done: true,
      Closed: true,
    });
  });

  it('AC-006.6 AC-006.7 prefills the reconnect settings of a Removed Project', async () => {
    const { user, dialog } = await renderPopup();

    await testKey(user, dialog, 'old');
    expect(range(dialog)).toHaveValue('7');
    expect(await readOptions(user, dialog)).toEqual({
      'To Do': false,
      'In Progress': false,
      Done: false,
      Closed: true,
    });
  });

  it('AC-006.6 labels statuses that share a name as name (id)', async () => {
    const answer: ProjectConnectionTest = {
      jiraProjectKey: 'TWIN',
      statuses: [
        { id: '20001', name: 'Done', done: true },
        { id: '20002', name: 'Done', done: true },
        { id: '20003', name: 'Waiting', done: false },
      ],
      reconnect: false,
      doneStatusIds: ['20002'],
      reviewRange: 3,
    };
    server.use(getTestProjectConnectionMockHandler(answer));
    const { user, dialog } = await renderPopup();

    await testKey(user, dialog, 'twin');
    expect(await readOptions(user, dialog)).toEqual({
      'Done (20001)': false,
      'Done (20002)': true,
      Waiting: false,
    });
  });

  it('AC-006.6 AC-006.9 asks for a Done status when Jira returns no statuses (row 14)', async () => {
    server.use(
      getTestProjectConnectionMockHandler({
        jiraProjectKey: 'EMPTY',
        statuses: [],
        reconnect: false,
        doneStatusIds: [],
        reviewRange: 3,
      }),
    );
    const { user, dialog, spy } = await renderPopup();

    await testKey(user, dialog, 'empty');
    await click(user, dialog, 'Save');
    expect(
      await within(dialog).findByText(msg('MSG01', { field_name: 'Done statuses' })),
    ).toBeInTheDocument();
    expect(spy.creates).toHaveLength(0);
  });

  it('AC-006.7 AC-006.11 requires a legacy invalid prefilled range to be corrected before Save (row 37)', async () => {
    server.use(
      getTestProjectConnectionMockHandler({
        jiraProjectKey: 'NEW1',
        statuses: [{ id: '10002', name: 'Done', done: true }],
        reconnect: false,
        doneStatusIds: ['10002'],
        reviewRange: 0,
      }),
    );
    const { user, dialog, spy } = await renderPopup();

    await testKey(user, dialog, 'new1');
    expect(range(dialog)).toHaveValue('0');
    await click(user, dialog, 'Save');
    expect(await within(dialog).findByText(msg('MSG44'))).toBeInTheDocument();
    expect(spy.creates).toHaveLength(0);

    await setRange(user, dialog, '5');
    await click(user, dialog, 'Save');
    await waitFor(() =>
      expect(spy.creates).toEqual([
        { jiraProjectKey: 'NEW1', doneStatusIds: ['10002'], reviewRange: 5 },
      ]),
    );
  });

  it('AC-006.8 hides the settings and Save when the key is edited after a test, until the next test (row 18)', async () => {
    const { user, dialog, spy } = await renderPopup();

    await testKey(user, dialog, 'new1');
    await user.type(keyInput(dialog), '2');
    expect(statuses(dialog)).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('spinbutton')).not.toBeInTheDocument();
    expect(button(dialog, 'Save')).not.toBeInTheDocument();
    expect(button(dialog, 'Test connection')).toBeInTheDocument();

    await click(user, dialog, 'Test connection');
    expect(
      await within(dialog).findByRole('combobox', { name: 'Done statuses' }),
    ).toBeInTheDocument();
    expect(spy.tests).toEqual([{ jiraProjectKey: 'new1' }, { jiraProjectKey: 'new12' }]);
  });

  it('AC-006.9 shows MSG01 below Done statuses when none is selected and sends no Save', async () => {
    const { user, dialog, spy } = await renderPopup();

    await testKey(user, dialog, 'new1');
    await readOptions(user, dialog);
    await toggleOption(user, 'Done');
    await toggleOption(user, 'Closed');
    await click(user, dialog, 'Save');
    expect(
      await within(dialog).findByText(msg('MSG01', { field_name: 'Done statuses' })),
    ).toBeInTheDocument();
    expect(spy.creates).toHaveLength(0);
  });

  it('AC-006.10 shows MSG01 below Review range when it is empty and sends no Save', async () => {
    const { user, dialog, spy } = await renderPopup();

    await testKey(user, dialog, 'new1');
    await setRange(user, dialog, '');
    await click(user, dialog, 'Save');
    expect(
      await within(dialog).findByText(msg('MSG01', { field_name: 'Review range' })),
    ).toBeInTheDocument();
    expect(spy.creates).toHaveLength(0);
  });

  it('AC-006.11 shows MSG44 for 0, -1, 2.5 and 2147483648 without rounding, then saves 2147483647', async () => {
    const { user, dialog, spy } = await renderPopup();

    await testKey(user, dialog, 'new1');
    for (const value of ['0', '-1', '2.5', String(MAX_RANGE + 1)]) {
      await setRange(user, dialog, value);
      await click(user, dialog, 'Save');
      expect(await within(dialog).findByText(msg('MSG44'))).toBeInTheDocument();
      expect(range(dialog)).toHaveValue(value);
      expect(spy.creates).toHaveLength(0);
    }
    await setRange(user, dialog, String(MAX_RANGE));
    await click(user, dialog, 'Save');
    await waitFor(() =>
      expect(spy.creates).toEqual([
        { jiraProjectKey: 'NEW1', doneStatusIds: ['10002', '10003'], reviewRange: MAX_RANGE },
      ]),
    );
  });

  it('AC-006.12 saves once with the tested canonical key, the selected ids including a non-Done status and a numeric range', async () => {
    const { user, dialog, spy } = await renderPopup();

    await testKey(user, dialog, ' new1 ');
    await readOptions(user, dialog);
    await toggleOption(user, 'To Do');
    const release = holdNext(PROJECTS);
    await click(user, dialog, 'Save');
    await click(user, dialog, 'Save');
    release();
    await expectClosed(dialog);
    expect(spy.creates).toEqual([
      { jiraProjectKey: 'NEW1', doneStatusIds: ['10002', '10003', '10000'], reviewRange: 3 },
    ]);
  });

  it('AC-006.13 AC-006.14 AC-006.15 closes, shows MSG05 once and refreshes the table after a new Project (201)', async () => {
    const { user, dialog, spy } = await renderPopup();
    expect(spy.lists).toBe(1);

    await testKey(user, dialog, 'new1');
    await click(user, dialog, 'Save');
    await expectClosed(dialog);
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    expect(screen.getAllByText(msg('MSG05'))).toHaveLength(1);
    const row = await screen.findByRole('row', { name: /\bNEW1\b/ });
    expect(cellsOf(row).slice(1)).toEqual(['NEW1', 'Đỗ Minh Quân', '1', 'Active']);
    expect(spy.lists).toBe(2);
  });

  it('AC-006.13 AC-006.14 AC-006.15 reconnects OLD (200) and keeps its Project ID and member count (row 19)', async () => {
    const { user, dialog, spy } = await renderPopup();

    await testKey(user, dialog, 'old');
    await click(user, dialog, 'Save');
    await expectClosed(dialog);
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await waitFor(() =>
      expect(cellsOf(screen.getByRole('row', { name: /\bOLD\b/ }))).toEqual([
        MOCK_PROJECT_IDS.OLD,
        'OLD',
        'Đỗ Minh Quân',
        '2',
        'Active',
      ]),
    );
    expect(spy.lists).toBe(2);
  });

  it('AC-006.4 AC-006.21 AC-006.22 locks the popup while the connection test runs (rows 12, 20)', async () => {
    const { user, dialog, spy } = await renderPopup();
    await enterKey(user, dialog, 'new1');
    const release = holdNext(TEST);

    await click(user, dialog, 'Test connection');
    await waitFor(() => expect(spy.tests).toHaveLength(1));
    expect(keyInput(dialog)).toBeDisabled();
    await user.click(within(dialog).getByRole('button', { name: /Test connection/ }));
    await click(user, dialog, 'Cancel');
    await click(user, dialog, 'Close');
    await user.keyboard('{Escape}');
    expect(dialog).not.toHaveClass('ant-zoom-leave-active');

    release();
    expect(
      await within(dialog).findByRole('combobox', { name: 'Done statuses' }),
    ).toBeInTheDocument();
    expect(spy.tests).toHaveLength(1);
    expect(keyInput(dialog)).toBeEnabled();
  });

  it('AC-006.12 AC-006.21 AC-006.22 locks the popup while Save runs, including a mask click (rows 12, 20)', async () => {
    const { user, dialog, spy } = await renderPopup();
    await testKey(user, dialog, 'new1');
    const release = holdNext(PROJECTS);

    await click(user, dialog, 'Save');
    await waitFor(() => expect(spy.creates).toHaveLength(1));
    expect(keyInput(dialog)).toBeDisabled();
    expect(range(dialog)).toBeDisabled();
    expect(statuses(dialog)).toBeDisabled();
    await user.click(within(dialog).getByRole('button', { name: /Save/ }));
    await click(user, dialog, 'Cancel');
    await click(user, dialog, 'Close');
    await user.keyboard('{Escape}');
    const wrap = dialog.closest('.ant-modal-wrap');
    if (!(wrap instanceof HTMLElement)) throw new Error('No modal mask');
    await user.click(wrap);
    expect(dialog).not.toHaveClass('ant-zoom-leave-active');

    release();
    await expectClosed(dialog);
    expect(spy.creates).toHaveLength(1);
  });

  it('AC-006.23 closes the idle popup by Cancel and the close icon without a request and reopens empty (row 18)', async () => {
    const { user, dialog, spy } = await renderPopup();
    await click(user, dialog, 'Test connection');
    expect(await within(dialog).findByText(projectKeyRequired)).toBeInTheDocument();
    await enterKey(user, dialog, 'draft');
    await click(user, dialog, 'Cancel');
    await expectClosed(dialog);

    let reopened = await openPopup(user);
    expect(keyInput(reopened)).toHaveValue('');
    expect(within(reopened).queryByText(projectKeyRequired)).not.toBeInTheDocument();
    await testKey(user, reopened, 'new1');
    await click(user, reopened, 'Close');
    await expectClosed(reopened);

    reopened = await openPopup(user);
    expect(keyInput(reopened)).toHaveValue('');
    expect(statuses(reopened)).not.toBeInTheDocument();
    expect(button(reopened, 'Test connection')).toBeInTheDocument();
    expect(spy.tests).toHaveLength(1);
    expect(spy.creates).toHaveLength(0);
  });

  it('AC-006.23 closes the idle popup by Escape and a mask click without a request (row 20)', async () => {
    const { user, dialog, spy } = await renderPopup();
    await enterKey(user, dialog, 'draft');
    await user.keyboard('{Escape}');
    await expectClosed(dialog);

    const reopened = await openPopup(user);
    expect(keyInput(reopened)).toHaveValue('');
    const wrap = reopened.closest('.ant-modal-wrap');
    if (!(wrap instanceof HTMLElement)) throw new Error('No modal mask');
    await user.click(wrap);
    await expectClosed(reopened);
    expect(spy.tests).toHaveLength(0);
    expect(spy.creates).toHaveLength(0);
  });
});
