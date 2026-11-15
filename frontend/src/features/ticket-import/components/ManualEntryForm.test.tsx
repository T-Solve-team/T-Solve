import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import dayjs from 'dayjs';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { MOCK_USERS } from '@/mocks/session';
import type { ImportDraftSummary, ManualDraftResponse, ManualEntryRequest } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

const ITSUP_ID = '00000000-0000-4000-8000-000000000101';
const E1 = '00000000-0000-4000-8000-0000000000e1';
const TEN_MB = 10 * 1024 * 1024;

/** A file of three bytes that claims `size`: only for files the form keeps back, a sent body must match. */
function evidence(name: string, size = 3): File {
  const file = new File(['abc'], name, { type: 'application/octet-stream' });
  if (size !== 3) Object.defineProperty(file, 'size', { value: size });
  return file;
}

function evidenceInput(): HTMLInputElement {
  const input = screen.getByLabelText<HTMLInputElement>('Evidence');
  if (!(input instanceof HTMLInputElement)) throw new Error('no Evidence input');
  return input;
}

type Sent = { path: string; entry: ManualEntryRequest; files: string[] };

/** The manual entry requests that reach the mock backend: the entry part as JSON and the file names. */
function captureSends() {
  const sent: Sent[] = [];
  server.events.on('request:start', ({ request }) => {
    const path = new URL(request.url).pathname;
    if (request.method !== 'POST' || !path.startsWith('/api/v1/imports/manual')) return;
    void request
      .clone()
      .formData()
      .then((form) => {
        const entry = form.get('entry');
        sent.push({
          path,
          entry: JSON.parse(typeof entry === 'string' ? entry : '{}') as ManualEntryRequest,
          files: form.getAll('files').map((file) => (file instanceof File ? file.name : '')),
        });
      });
  });
  return sent;
}

/** Import Tickets on the Manual entry tab, as the mock Project Manager. */
async function openManualTab(options: { applyAccept?: boolean } = {}) {
  const user = userEvent.setup(options);
  const view = renderApp('/import', { role: 'PROJECT_MANAGER' });
  await user.click(await screen.findByRole('tab', { name: 'Manual entry' }, { timeout: 5000 }));
  await screen.findByRole('button', { name: 'Save as draft' }, { timeout: 5000 });
  return { user, ...view };
}

type User = ReturnType<typeof userEvent.setup>;

async function chooseProject(user: User) {
  await user.click(screen.getByRole('combobox', { name: 'Project' }));
  await user.click(await screen.findByTitle('ITSUP · IT'));
}

/** Pastes rather than types: seven fields typed key by key make the test slow on a loaded machine. */
async function put(user: User, label: string, text: string) {
  await user.click(screen.getByLabelText(label));
  await user.paste(text);
}

async function fillRequired(user: User, ticketId = 'T-1') {
  await chooseProject(user);
  await put(user, 'Ticket ID', ticketId);
  await put(user, 'Title', 'Login fails');
  await put(user, 'Description', 'Users cannot sign in');
  await put(user, 'Symptoms', 'Error 500');
  await put(user, 'Root cause', 'Bad token');
  await put(user, 'Resolution steps', 'Restart');
  await put(user, 'Scope', 'All users');
}

const MANUAL_DRAFT: ManualDraftResponse = {
  previewId: 950,
  projectId: ITSUP_ID,
  ticketId: 'T-9',
  title: 'Old title',
  description: null,
  resolvedDate: '2026-11-01',
  symptoms: null,
  rootCause: null,
  resolutionSteps: null,
  workaround: null,
  scope: null,
  evidence: [{ id: E1, fileName: 'a.png', fileType: 'png', fileSize: 1 }],
  savedAt: '2026-11-10T02:30:00Z',
};

/** The Drafts list shows MANUAL_DRAFT, and it can be reopened. */
function serveManualDraft() {
  server.use(
    http.get('*/api/v1/imports/drafts', () =>
      HttpResponse.json<ImportDraftSummary[]>([
        {
          previewId: 950,
          source: 'MANUAL',
          projectId: ITSUP_ID,
          rowCount: 1,
          savedAt: MANUAL_DRAFT.savedAt,
        },
      ]),
    ),
    http.get('*/api/v1/imports/manual/drafts/950', () =>
      HttpResponse.json<ManualDraftResponse>(MANUAL_DRAFT),
    ),
  );
}

async function openManualDraft() {
  serveManualDraft();
  const user = userEvent.setup();
  const view = renderApp('/import', { role: 'PROJECT_MANAGER' });
  const table = await screen.findByRole('table', { name: 'Drafts' }, { timeout: 5000 });
  await user.click(await within(table).findByRole('button', { name: /^Open Draft/ }));
  await waitFor(() => expect(screen.getByLabelText('Title')).toHaveValue('Old title'));
  return { user, ...view };
}

describe('ManualEntryForm', () => {
  beforeAll(async () => {
    await import('../pages/ImportTicketsPage');
  });

  afterEach(() => {
    server.events.removeAllListeners();
  });

  it('AC-006.12 offers only the managed Projects and no future resolved date', async () => {
    const { user } = await openManualTab();

    await user.click(screen.getByRole('combobox', { name: 'Project' }));
    expect(await screen.findByTitle('ITSUP · IT')).toBeInTheDocument();
    expect(screen.queryByTitle(/^HRHELP/)).not.toBeInTheDocument();

    await user.click(screen.getByLabelText('Resolved date'));
    const tomorrow = dayjs().add(1, 'day').format('YYYY-MM-DD');
    const cell = await waitFor(() => {
      const found = document.querySelector(`td[title="${tomorrow}"]`);
      if (!found) throw new Error('no cell for tomorrow');
      return found;
    });
    expect(cell).toHaveClass('ant-picker-cell-disabled');
  });

  it('AC-006.12 offers no managed Project that is not Active (BUG-216)', async () => {
    const pm = MOCK_USERS.pm;
    const removed = {
      projectId: '00000000-0000-4000-8000-000000000301',
      jiraProjectKey: 'ITOLD',
      departmentId: '00000000-0000-4000-8000-0000000000d1',
      departmentName: 'IT',
      roleInProject: 'PROJECT_MANAGER' as const,
      projectStatus: 'REMOVED' as const,
    };
    server.use(
      http.get('*/api/v1/me', () =>
        HttpResponse.json({ ...pm, projects: [...pm.projects, removed] }),
      ),
    );
    const { user } = await openManualTab();

    await user.click(screen.getByRole('combobox', { name: 'Project' }));
    expect(await screen.findByTitle('ITSUP · IT')).toBeInTheDocument();
    expect(screen.queryByTitle('ITOLD · IT')).not.toBeInTheDocument();
  });

  it('AC-006.1 Save sends the entry and its files, shows MSG32 and empties the form', async () => {
    const sent = captureSends();
    const { user } = await openManualTab();
    await fillRequired(user);
    await user.upload(evidenceInput(), [evidence('a.png'), evidence('log.TXT')]);

    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(
      await screen.findByText(msg('MSG32', { created: 1, skipped: 0 }), {}, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(sent).toEqual([
      {
        path: '/api/v1/imports/manual',
        entry: expect.objectContaining({
          projectId: ITSUP_ID,
          ticketId: 'T-1',
          title: 'Login fails',
          description: 'Users cannot sign in',
          symptoms: 'Error 500',
          rootCause: 'Bad token',
          resolutionSteps: 'Restart',
          scope: 'All users',
        }) as ManualEntryRequest,
        files: ['a.png', 'log.TXT'],
      },
    ]);
    await waitFor(() => expect(screen.getByLabelText('Title')).toHaveValue(''));
    expect(screen.queryByText('a.png')).not.toBeInTheDocument();
  });

  it('AC-006.12 shows MSG01 at each empty required field and sends nothing', async () => {
    const sent = captureSends();
    const { user } = await openManualTab();

    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(msg('MSG01', { field_name: 'Ticket ID' }))).toBeInTheDocument();
    expect(screen.getByText(msg('MSG01', { field_name: 'Title' }))).toBeInTheDocument();
    expect(screen.getByText(msg('MSG01', { field_name: 'Scope' }))).toBeInTheDocument();
    expect(sent).toHaveLength(0);
  });

  // Two tests, not one: together they ran close to the 15 s limit on a loaded machine (BUG-213).
  it('AC-006.12 checks the Evidence type and size before sending (MSG34)', async () => {
    const { user } = await openManualTab({ applyAccept: false });

    await user.upload(evidenceInput(), evidence('setup.exe'));
    expect(await screen.findByText(msg('MSG34', { file_name: 'setup.exe' }))).toBeInTheDocument();
    await user.upload(evidenceInput(), evidence('big.pdf', TEN_MB + 1));
    expect(await screen.findByText(msg('MSG34', { file_name: 'big.pdf' }))).toBeInTheDocument();
    // a file of exactly 10 MB is fine; removed again, so no 10 MB body is sent
    await user.upload(evidenceInput(), evidence('ten.pdf', TEN_MB));
    expect(await screen.findByText('ten.pdf')).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG34', { file_name: 'ten.pdf' }))).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Remove ten.pdf' }));
    expect(screen.queryByText('ten.pdf')).not.toBeInTheDocument();
  });

  it('AC-006.12 sends only the Evidence files that passed the check', async () => {
    const sent = captureSends();
    const { user } = await openManualTab({ applyAccept: false });
    await fillRequired(user);

    await user.upload(evidenceInput(), evidence('setup.exe'));
    // an upper-case extension is fine
    await user.upload(evidenceInput(), evidence('SHOT.PNG'));

    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(sent).toHaveLength(1));
    expect(sent[0]?.files).toEqual(['SHOT.PNG']);
  });

  it('AC-006.12 checks the Evidence count before sending (MSG72)', async () => {
    const sent = captureSends();
    const { user } = await openManualTab();
    await fillRequired(user);

    await user.upload(
      evidenceInput(),
      ['1', '2', '3', '4', '5', '6'].map((n) => evidence(`${n}.png`)),
    );

    expect(await screen.findByText(msg('MSG72'))).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG72'))).toBeInTheDocument();
    expect(sent).toHaveLength(0);
  });

  it('AC-006.12 shows a rejection next to its field and keeps every value and file', async () => {
    server.use(
      http.post('*/api/v1/imports/manual', () =>
        problem(409, 'MSG31', {
          params: { ticket_id: 'T-1' },
          fieldErrors: [{ field: 'ticketId', code: 'MSG31', params: { ticket_id: 'T-1' } }],
        }),
      ),
    );
    const { user } = await openManualTab();
    await fillRequired(user);
    await user.upload(evidenceInput(), evidence('a.png'));

    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(msg('MSG31', { ticket_id: 'T-1' }))).toBeInTheDocument();
    expect(screen.getByLabelText('Title')).toHaveValue('Login fails');
    expect(screen.getByLabelText('Ticket ID')).toHaveValue('T-1');
    expect(screen.getByText('a.png')).toBeInTheDocument();
  });

  it('AC-007.1 Save as draft needs only the Project, shows MSG41 and lists the Draft', async () => {
    const sent = captureSends();
    const { user } = await openManualTab();
    await chooseProject(user);
    await put(user, 'Title', 'Half done');

    await user.click(screen.getByRole('button', { name: 'Save as draft' }));

    expect(await screen.findByText(msg('MSG41'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(sent).toEqual([
      {
        path: '/api/v1/imports/manual/drafts',
        entry: expect.objectContaining({
          projectId: ITSUP_ID,
          title: 'Half done',
        }) as ManualEntryRequest,
        files: [],
      },
    ]);
    const table = screen.getByRole('table', { name: 'Drafts' });
    expect(await within(table).findByText('Manual')).toBeInTheDocument();
    // the tab stays on the saved Draft
    expect(screen.getByLabelText('Title')).toHaveValue('Half done');
  });

  it('AC-007.2 Save as draft without a Project shows MSG01 and sends nothing', async () => {
    const sent = captureSends();
    const { user } = await openManualTab();

    await user.click(screen.getByRole('button', { name: 'Save as draft' }));

    expect(await screen.findByText(msg('MSG01', { field_name: 'Project' }))).toBeInTheDocument();
    expect(sent).toHaveLength(0);
  });

  it('AC-007.4 Open on a Manual Draft fills the Manual entry tab with its fields and files', async () => {
    await openManualDraft();

    expect(screen.getByRole('tab', { name: 'Manual entry' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(screen.getByLabelText('Ticket ID')).toHaveValue('T-9');
    expect(screen.getByText('a.png')).toBeInTheDocument();
  });

  it('AC-007.5 saving a reopened Draft sends its id and the files it keeps', async () => {
    const sent = captureSends();
    const { user } = await openManualDraft();

    await user.click(screen.getByRole('button', { name: 'Remove a.png' }));
    await user.upload(evidenceInput(), evidence('b.pdf'));
    await user.click(screen.getByRole('button', { name: 'Save as draft' }));

    await waitFor(() => expect(sent).toHaveLength(1));
    expect(sent[0]?.entry).toEqual(
      expect.objectContaining({ draftId: 950, keepEvidenceIds: [], ticketId: 'T-9' }),
    );
    expect(sent[0]?.files).toEqual(['b.pdf']);
  });

  it('AC-007.5 kept and new files together are at most five (MSG72)', async () => {
    const { user } = await openManualDraft();

    await user.upload(
      evidenceInput(),
      ['1', '2', '3', '4', '5'].map((n) => evidence(`${n}.png`)),
    );

    expect(await screen.findByText(msg('MSG72'))).toBeInTheDocument();
  });

  it('edge 23 a Draft gone meanwhile shows MSG04 and goes back to a new entry', async () => {
    server.use(http.post('*/api/v1/imports/manual/drafts', () => problem(404, 'MSG04')));
    const { user } = await openManualDraft();

    await user.click(screen.getByRole('button', { name: 'Save as draft' }));

    expect(await screen.findAllByText(msg('MSG04'))).not.toHaveLength(0);
    await waitFor(() => expect(screen.getByLabelText('Title')).toHaveValue(''));
    expect(screen.queryByText('a.png')).not.toBeInTheDocument();
  });
});
