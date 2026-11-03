import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MOCK_USERS } from '@/mocks/session';
import { server } from '@/mocks/server';
import type { CurrentUser } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

const PM = MOCK_USERS.pm; // manages ITSUP and ITNET (IT)
const ITSUP_ID = '00000000-0000-4000-8000-000000000101';

function csvFile(name = 'tickets.csv', size?: number): File {
  const file = new File(['ticket_id,title\n'], name, { type: 'text/csv' });
  if (size !== undefined) Object.defineProperty(file, 'size', { value: size });
  return file;
}

function fileInput(): HTMLInputElement {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) throw new Error('no file input');
  return input;
}

function signInAs(user: CurrentUser) {
  server.use(http.get('*/api/v1/me', () => HttpResponse.json(user)));
}

/** Import Tickets with the mock Project Manager; resolves once the CSV form is shown. */
async function openImport(options: { applyAccept?: boolean } = {}) {
  const user = userEvent.setup(options);
  const view = renderApp('/import', { role: 'PROJECT_MANAGER' });
  await screen.findByRole('button', { name: 'Upload' }, { timeout: 5000 });
  return { user, ...view };
}

async function chooseProject(user: ReturnType<typeof userEvent.setup>, label: string) {
  await user.click(screen.getByRole('combobox', { name: 'Project' }));
  await user.click(await screen.findByTitle(label));
}

/** Counts the upload requests that reach the mock backend. */
function countUploads() {
  const calls = { count: 0 };
  server.events.on('request:start', ({ request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname === '/api/v1/imports/csv') {
      calls.count += 1;
    }
  });
  return calls;
}

describe('ImportTicketsPage', () => {
  beforeAll(async () => {
    await import('./ImportTicketsPage');
  });

  afterEach(() => {
    server.events.removeAllListeners();
  });

  it('AC-008.1 shows the tabs Jira, CSV and Manual entry with CSV selected and the others disabled', async () => {
    await openImport();
    expect(screen.getByRole('tab', { name: 'CSV' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: 'Jira' })).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByRole('tab', { name: 'Manual entry' })).toHaveAttribute(
      'aria-disabled',
      'true',
    );
  });

  it('AC-008.2 offers only the Projects the user manages, with Jira key and Department', async () => {
    const hrMember = {
      projectId: '00000000-0000-4000-8000-000000000201',
      jiraProjectKey: 'HRHELP',
      departmentId: '00000000-0000-4000-8000-0000000000d2',
      departmentName: 'HR',
      roleInProject: 'MEMBER' as const,
    };
    signInAs({ ...PM, projects: [...PM.projects, hrMember] });
    const { user } = await openImport();

    await user.click(screen.getByRole('combobox', { name: 'Project' }));

    expect(await screen.findByTitle('ITSUP · IT')).toBeInTheDocument();
    expect(screen.getByTitle('ITNET · IT')).toBeInTheDocument();
    expect(screen.queryByTitle('HRHELP · HR')).not.toBeInTheDocument();
  });

  it('AC-008.3 saves the template returned by the template endpoint as ticket-import-template.csv', async () => {
    const saved: { name?: string; blob?: Blob } = {};
    URL.createObjectURL = vi.fn((blob: Blob) => {
      saved.blob = blob;
      return 'blob:template';
    });
    const revoke = vi.fn();
    URL.revokeObjectURL = revoke;
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      saved.name = this.download;
    });
    const { user } = await openImport();

    await user.click(screen.getByRole('button', { name: 'Download template' }));

    await waitFor(() => expect(click).toHaveBeenCalledOnce());
    expect(saved.name).toBe('ticket-import-template.csv');
    expect(await saved.blob?.text()).toMatch(/^ticket_id,title,description,author,resolved_date,/);
    await waitFor(() => expect(revoke).toHaveBeenCalledWith('blob:template'));
  });

  it('keeps the template URL alive until the download has started (BUG-106)', async () => {
    URL.createObjectURL = vi.fn(() => 'blob:template');
    const revoke = vi.fn();
    URL.revokeObjectURL = revoke;
    const revokedRightAfterClick: boolean[] = [];
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      // Runs once the code after click() in the same task is done, like a browser starting the download.
      queueMicrotask(() => revokedRightAfterClick.push(revoke.mock.calls.length > 0));
    });
    const { user } = await openImport();

    await user.click(screen.getByRole('button', { name: 'Download template' }));

    await waitFor(() => expect(click).toHaveBeenCalledOnce());
    await waitFor(() => expect(revokedRightAfterClick).toEqual([false]));
    await waitFor(() => expect(revoke).toHaveBeenCalledWith('blob:template'));
  });

  it('AC-008.4 shows MSG01 at Project and CSV file and sends nothing when both are missing', async () => {
    const uploads = countUploads();
    const { user } = await openImport();

    await user.click(screen.getByRole('button', { name: 'Upload' }));

    expect(await screen.findByText(msg('MSG01', { field_name: 'Project' }))).toBeInTheDocument();
    expect(screen.getByText(msg('MSG01', { field_name: 'CSV file' }))).toBeInTheDocument();
    expect(uploads.count).toBe(0);
  });

  it('AC-008.4 shows MSG01 at Project when the user manages no Project (edge case 39)', async () => {
    signInAs({ ...PM, projects: [] });
    const uploads = countUploads();
    const { user } = await openImport();
    await user.upload(fileInput(), csvFile());

    await user.click(screen.getByRole('button', { name: 'Upload' }));

    expect(await screen.findByText(msg('MSG01', { field_name: 'Project' }))).toBeInTheDocument();
    expect(uploads.count).toBe(0);
  });

  it.each([
    ['MSG27', 'not .csv', csvFile('tickets.xlsx')],
    ['MSG28', 'larger than 10 MB', csvFile('big.csv', 10 * 1024 * 1024 + 1)],
  ] as const)(
    'AC-008.5 shows %s at the CSV file field and does not keep a file %s',
    async (code, _, file) => {
      const { user } = await openImport({ applyAccept: false });

      await user.upload(fileInput(), file);

      expect(await screen.findByText(msg(code))).toBeInTheDocument();
      expect(screen.queryByText(file.name)).not.toBeInTheDocument();
    },
  );

  it('AC-008.6 sends the chosen Project and file, then opens the Import Preview of the new preview', async () => {
    const sent: { projectId?: FormDataEntryValue | null; fileText?: string } = {};
    server.events.on('request:start', ({ request }) => {
      if (request.method !== 'POST' || !request.url.endsWith('/api/v1/imports/csv')) return;
      void request
        .clone()
        .formData()
        .then(async (form) => {
          const file = form.get('file');
          sent.projectId = form.get('projectId');
          sent.fileText = typeof file === 'string' ? undefined : await file?.text();
        });
    });
    const { user, router } = await openImport();
    await chooseProject(user, 'ITSUP · IT');
    await user.upload(fileInput(), csvFile());

    await user.click(screen.getByRole('button', { name: 'Upload' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/import/preview'));
    expect(router.state.location.search).toMatch(/^\?previewId=[1-9]\d*$/);
    await waitFor(() =>
      expect(sent).toEqual({ projectId: ITSUP_ID, fileText: 'ticket_id,title\n' }),
    );
  });

  it('shows only the MSG07 dialog, not an Alert, when Upload answers 401 (BUG-105)', async () => {
    server.use(http.post('*/api/v1/imports/csv', () => problem(401, 'MSG07')));
    const { user } = await openImport();
    await chooseProject(user, 'ITSUP · IT');
    await user.upload(fileInput(), csvFile());

    await user.click(screen.getByRole('button', { name: 'Upload' }));

    expect(await screen.findByRole('dialog')).toHaveTextContent(msg('MSG07'));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('AC-008.7 shows MSG29 with the missing columns and keeps the chosen Project and file', async () => {
    server.use(
      http.post('*/api/v1/imports/csv', () =>
        problem(400, 'MSG29', { params: { column_names: 'root_cause, scope' } }),
      ),
    );
    const { user, router } = await openImport();
    await chooseProject(user, 'ITSUP · IT');
    await user.upload(fileInput(), csvFile());

    await user.click(screen.getByRole('button', { name: 'Upload' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      msg('MSG29', { column_names: 'root_cause, scope' }),
    );
    expect(router.state.location.pathname).toBe('/import');
    expect(screen.getByText('tickets.csv')).toBeInTheDocument();
    expect(
      screen.getByRole('combobox', { name: 'Project' }).closest('.ant-select'),
    ).toHaveTextContent('ITSUP · IT');
  });

  it.each([
    [400, 'MSG04'],
    [403, 'MSG08'],
  ] as const)('AC-008.7 shows %s %s above the form', async (status, code) => {
    server.use(http.post('*/api/v1/imports/csv', () => problem(status, code)));
    const { user } = await openImport();
    await chooseProject(user, 'ITSUP · IT');
    await user.upload(fileInput(), csvFile());

    await user.click(screen.getByRole('button', { name: 'Upload' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(msg(code));
    expect(screen.getByText('tickets.csv')).toBeInTheDocument();
  });

  it.each([
    [400, 'MSG27'],
    [413, 'MSG28'],
  ] as const)('AC-008.7 shows %s %s at the CSV file field', async (status, code) => {
    server.use(http.post('*/api/v1/imports/csv', () => problem(status, code)));
    const { user } = await openImport();
    await chooseProject(user, 'ITSUP · IT');
    await user.upload(fileInput(), csvFile());

    await user.click(screen.getByRole('button', { name: 'Upload' }));

    const field = screen.getByText('CSV file').closest('.ant-form-item');
    if (!(field instanceof HTMLElement)) throw new Error('no CSV file field');
    expect(await within(field).findByText(msg(code))).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('AC-008.8 shows Upload as loading and sends one upload for a double click (edge case 34)', async () => {
    let answer: () => void = () => {};
    const answered = new Promise<void>((resolve) => {
      answer = resolve;
    });
    server.use(
      http.post('*/api/v1/imports/csv', async () => {
        await answered;
        return problem(400, 'MSG04');
      }),
    );
    const uploads = countUploads();
    const { user } = await openImport();
    await chooseProject(user, 'ITSUP · IT');
    await user.upload(fileInput(), csvFile());
    const upload = screen.getByRole('button', { name: 'Upload' });

    await user.dblClick(upload);

    await waitFor(() => expect(upload).toHaveClass('ant-btn-loading'));
    expect(uploads.count).toBe(1);
    answer();
    await waitFor(() => expect(upload).not.toHaveClass('ant-btn-loading'));
    expect(uploads.count).toBe(1);
  });
});
