import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import {
  fillImportPreviewSolution,
  getGetImportPreviewQueryKey,
  removeImportPreviewRows,
  type FillSolutionRequest,
  type ImportResultResponse,
} from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
import { previewResponse, seedJiraImport, seedPreview } from '../mocks/data';
import { seedJiraPreview } from '../mocks/handlers';

/** The Jira preview of the mock backend: ITSUP-1001…1003, all Needs Solution, resolved 2026-10-01 to 2026-10-15. */
const PATH = '/import/preview?previewId=1';
/** The PM's saved Draft of the mock backend (mocks/handlers.ts), saved 2026-10-16 09:30 Vietnam time. */
const DRAFT_PATH = '/import/preview?previewId=900';

const solution = {
  symptoms: 'Wi-Fi drops every few minutes',
  rootCause: 'Access point on a crowded channel',
  resolutionSteps: 'Move the access point to channel 36',
  scope: 'Meeting rooms on floor 3',
} satisfies Omit<FillSolutionRequest, 'rowNumbers'>;

function rowOf(rowNo: number): HTMLElement {
  const row = screen.getByLabelText(`Select row ${rowNo}`).closest('tr');
  if (!row) throw new Error(`no row ${rowNo}`);
  return row;
}

function statCard(label: 'Valid' | 'Removed' | 'Needs Solution'): HTMLElement {
  const card = screen.getByText(label, { selector: '.ant-typography' }).closest('.ant-card');
  if (!(card instanceof HTMLElement)) throw new Error(`no ${label} card`);
  return card;
}

/** The POST requests (Finish, Save as draft, Fill solution, Remove) that reach the mock backend, by path. */
function recordPosts() {
  const paths: string[] = [];
  server.events.on('request:start', ({ request }) => {
    if (request.method !== 'GET') paths.push(new URL(request.url).pathname);
  });
  return paths;
}

function writeSolution(dialog: HTMLElement) {
  for (const [label, text] of [
    ['Symptoms', solution.symptoms],
    ['Root cause', solution.rootCause],
    ['Resolution steps', solution.resolutionSteps],
    ['Scope', solution.scope],
  ] as const) {
    fireEvent.change(within(dialog).getByRole('textbox', { name: label }), {
      target: { value: text },
    });
  }
}

async function openPreview(path = PATH) {
  const user = userEvent.setup();
  const view = renderApp(path, { role: 'PROJECT_MANAGER' });
  await screen.findByRole('table', { name: 'Import rows' }, { timeout: 5000 });
  await screen.findByText(path === PATH ? 'ITSUP-1001' : 'ITSUP-1003');
  return { user, ...view };
}

/** Every issue gets a Solution, so Finish is enabled (AC-002.10). */
async function solveAll() {
  await fillImportPreviewSolution(1, { ...solution, rowNumbers: [1, 2, 3] });
}

async function openRow(user: ReturnType<typeof userEvent.setup>, rowNo: number) {
  await user.click(within(rowOf(rowNo)).getByRole('button', { name: 'Preview' }));
  const dialog = await screen.findByRole('dialog', { name: 'Import Ticket Preview' });
  await within(dialog).findByText(`ITSUP-100${rowNo}`);
  return dialog;
}

describe('Import Preview of a Jira pull', () => {
  beforeAll(async () => {
    await import('./ImportPreviewPage');
  });

  beforeEach(() => {
    seedJiraPreview();
  });

  afterEach(() => {
    server.events.removeAllListeners();
  });

  describe('Summary', () => {
    it('AC-002.1 shows Jira import, the date range and the Valid, Removed and Needs Solution counts', async () => {
      await openPreview();
      expect(screen.getByText('Jira import')).toBeInTheDocument();
      expect(screen.getByText('Resolved from: 01/10/2026')).toBeInTheDocument();
      expect(screen.getByText('Resolved to: 15/10/2026')).toBeInTheDocument();
      expect(statCard('Valid')).toHaveTextContent('0');
      expect(statCard('Removed')).toHaveTextContent('0');
      expect(statCard('Needs Solution')).toHaveTextContent('3');
      // A Jira issue is never an Error row.
      expect(screen.queryByText('Error', { selector: '.ant-typography' })).toBeNull();
    });

    it('AC-002.1 lists each issue with its Jira key, summary, assignee, resolved date and result', async () => {
      await openPreview();
      const row = rowOf(1);
      for (const text of [
        'ITSUP-1001',
        'Wi-Fi drops in meeting room 1',
        'Phạm Quốc Bảo',
        '2026-10-15',
        'Needs Solution',
      ]) {
        expect(row).toHaveTextContent(text);
      }
    });

    it('AC-001.3 shows MSG35 when more issues match the date range', async () => {
      const preview = previewResponse(seedJiraImport(1, 'p', '2026-10-01', '2026-10-15'));
      server.use(
        http.get('*/api/v1/imports/previews/1', () =>
          Response.json({ ...preview, message: { code: 'MSG35', params: {} } }),
        ),
      );
      await openPreview();
      expect(screen.getByText(msg('MSG35'))).toBeInTheDocument();
    });
  });

  describe('Finish', () => {
    it('AC-002.10 labels the import button Finish and keeps it disabled while an issue Needs Solution', async () => {
      await fillImportPreviewSolution(1, { ...solution, rowNumbers: [1, 2] });
      await openPreview();
      expect(screen.queryByRole('button', { name: 'Confirm' })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Finish' })).toBeDisabled();
    });

    it('AC-002.10 enables Finish once the last issue that Needs Solution gets one', async () => {
      await fillImportPreviewSolution(1, { ...solution, rowNumbers: [1, 2] });
      const { user } = await openPreview();
      const dialog = await openRow(user, 3);
      writeSolution(dialog);

      await user.click(within(dialog).getByRole('button', { name: 'Apply' }));

      await waitFor(() => expect(screen.getByRole('button', { name: 'Finish' })).toBeEnabled());
    });

    it('AC-002.10 enables Finish when the issues left are Valid or Removed', async () => {
      await fillImportPreviewSolution(1, { ...solution, rowNumbers: [1] });
      await removeImportPreviewRows(1, { rowNumbers: [2, 3] });
      await openPreview();
      expect(screen.getByRole('button', { name: 'Finish' })).toBeEnabled();
    });

    it('AC-002.10 keeps Finish disabled when no Valid issue is left', async () => {
      await removeImportPreviewRows(1, { rowNumbers: [1, 2, 3] });
      await openPreview();
      expect(statCard('Removed')).toHaveTextContent('3');
      expect(screen.getByRole('button', { name: 'Finish' })).toBeDisabled();
    });

    it('AC-003.1 publishes on Finish, shows MSG32 and opens Import Tickets without MSG37', async () => {
      await solveAll();
      const { user, router } = await openPreview();

      await user.click(screen.getByRole('button', { name: 'Finish' }));

      expect(await screen.findByText(msg('MSG32', { created: 3, skipped: 0 }))).toBeInTheDocument();
      await waitFor(() => expect(router.state.location.pathname).toBe('/import'));
      expect(screen.queryByText(msg('MSG37'))).not.toBeInTheDocument();
      // No MSG38 dialog when no issue was skipped.
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('AC-003.2 shows MSG38 with the skipped issues next to MSG32 after Finish', async () => {
      const notice = { code: 'MSG38', params: { count: 2, issue_keys: 'ITSUP-1001, ITSUP-1002' } };
      server.use(
        http.post('*/api/v1/imports/previews/1/confirm', () =>
          HttpResponse.json<ImportResultResponse>({
            importBatchId: 7,
            created: 1,
            skipped: 2,
            message: { code: 'MSG32', params: { created: 1, skipped: 2 } },
            notice,
          }),
        ),
      );
      await solveAll();
      const { user, router } = await openPreview();

      await user.click(screen.getByRole('button', { name: 'Finish' }));

      expect(await screen.findByRole('dialog')).toHaveTextContent(msg('MSG38', notice.params));
      expect(screen.getByText(msg('MSG32', { created: 1, skipped: 2 }))).toBeInTheDocument();
      await waitFor(() => expect(router.state.location.pathname).toBe('/import'));
    });
  });

  describe('Fill solution', () => {
    it('AC-002.6 asks MSG36 with the number of selected issues that already have a Solution', async () => {
      await fillImportPreviewSolution(1, { ...solution, rowNumbers: [1] });
      const posts = recordPosts();
      const { user } = await openPreview();
      for (const rowNo of [1, 2, 3]) await user.click(screen.getByLabelText(`Select row ${rowNo}`));
      await user.click(screen.getByRole('button', { name: 'Fill solution' }));
      const dialog = await screen.findByRole('dialog', { name: 'Import Ticket Preview' });
      writeSolution(dialog);

      await user.click(within(dialog).getByRole('button', { name: 'Apply' }));
      const question = (await screen.findByText(msg('MSG36', { count: 1 }))).closest(
        '[role="dialog"]',
      );
      if (!(question instanceof HTMLElement)) throw new Error('no MSG36 dialog');
      expect(posts).toHaveLength(0);
      await user.click(within(question).getByRole('button', { name: 'OK' }));

      await waitFor(() => expect(statCard('Valid')).toHaveTextContent('3'));
      expect(posts).toEqual(['/api/v1/imports/previews/1/fill-solution']);
    });
  });

  describe('Save as draft', () => {
    it('AC-004.1 saves the preview as a Draft, shows MSG41 and stays on the Import Preview', async () => {
      const posts = recordPosts();
      const { user, router } = await openPreview();

      await user.click(screen.getByRole('button', { name: 'Save as draft' }));

      expect(await screen.findByText(msg('MSG41'))).toBeInTheDocument();
      expect(posts).toEqual(['/api/v1/imports/previews/1/draft']);
      expect(router.state.location.pathname).toBe('/import/preview');
      expect(screen.getByRole('table', { name: 'Import rows' })).toBeInTheDocument();
    });

    it('AC-004.1 offers Save as draft only on a Jira preview', async () => {
      server.use(http.get('*/api/v1/imports/previews/1', () => Response.json(seedPreview())));
      await openPreview();
      expect(screen.getByRole('button', { name: 'Confirm' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Save as draft' })).not.toBeInTheDocument();
    });
  });

  describe('leaving', () => {
    it('AC-002.11 asks MSG37 on Cancel: Cancel in the dialog stays, OK opens Import Tickets without importing', async () => {
      const posts = recordPosts();
      const { user, router } = await openPreview();
      const cancel = screen.getByRole('button', { name: 'Cancel' });

      await user.click(cancel);
      let dialog = await screen.findByRole('dialog');
      expect(dialog).toHaveTextContent(msg('MSG37'));
      await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
      expect(router.state.location.pathname).toBe('/import/preview');

      await user.click(cancel);
      dialog = await screen.findByRole('dialog');
      await user.click(within(dialog).getByRole('button', { name: 'OK' }));

      await waitFor(() => expect(router.state.location.pathname).toBe('/import'));
      expect(posts).toHaveLength(0);
    });

    it('AC-002.11 asks MSG37 before leaving for another screen and leaves only on OK', async () => {
      const { user, router } = await openPreview();

      await act(() => router.navigate('/my-profile'));
      const dialog = await screen.findByRole('dialog');
      expect(dialog).toHaveTextContent(msg('MSG37'));
      await user.click(within(dialog).getByRole('button', { name: 'OK' }));

      await waitFor(() => expect(router.state.location.pathname).toBe('/my-profile'));
    });

    it('AC-002.11 leaves a reopened Draft as it was saved on Cancel', async () => {
      const posts = recordPosts();
      const { user, router } = await openPreview(DRAFT_PATH);

      await user.click(screen.getByRole('button', { name: 'Cancel' }));
      const dialog = await screen.findByRole('dialog');
      await user.click(within(dialog).getByRole('button', { name: 'OK' }));

      await waitFor(() => expect(router.state.location.pathname).toBe('/import'));
      const drafts = await screen.findByRole('table', { name: 'Drafts' });
      expect(await within(drafts).findByText('16/10/2026 09:30')).toBeInTheDocument();
      expect(posts).toHaveLength(0);
    });
  });

  describe('a preview that is gone (design Edge 10)', () => {
    /** Another tab pulled again: the backend deleted this preview, so every request about it answers 404. */
    function deletePreview() {
      server.use(
        http.all('*/api/v1/imports/previews/1', () => problem(404, 'MSG04')),
        http.all('*/api/v1/imports/previews/1/*', () => problem(404, 'MSG04')),
      );
    }

    it('AC-002.8 opens Import Tickets without MSG37 when the preview is read again', async () => {
      const { router, queryClient } = await openPreview();
      deletePreview();

      await act(() => queryClient.invalidateQueries({ queryKey: getGetImportPreviewQueryKey(1) }));

      await waitFor(() => expect(router.state.location.pathname).toBe('/import'));
      expect(screen.queryByText(msg('MSG37'))).not.toBeInTheDocument();
    });

    it.each(['Finish', 'Save as draft', 'Remove', 'Apply'])(
      'AC-002.8 opens Import Tickets without MSG37 when %s answers 404',
      async (action) => {
        await solveAll();
        const { user, router } = await openPreview();
        if (action === 'Remove') await user.click(screen.getByLabelText('Select row 1'));
        // The popup has read its issue before the preview is deleted.
        const dialog = action === 'Apply' ? await openRow(user, 1) : null;
        deletePreview();

        await user.click(
          dialog
            ? within(dialog).getByRole('button', { name: 'Apply' })
            : screen.getByRole('button', { name: action }),
        );

        await waitFor(() => expect(router.state.location.pathname).toBe('/import'));
        expect(screen.queryByText(msg('MSG37'))).not.toBeInTheDocument();
      },
    );
  });

  describe('Import Ticket Preview popup', () => {
    function expectSnapshot(dialog: HTMLElement) {
      expect(within(dialog).getByText('Comments')).toBeInTheDocument();
      expect(within(dialog).getByText('Moved the access point.')).toBeInTheDocument();
      expect(within(dialog).getByText(/Lê Văn Tùng/)).toHaveTextContent('02/10/2026 10:15');
      expect(within(dialog).getByText('Source link')).toBeInTheDocument();
      const link = within(dialog).getByRole('link', {
        name: 'https://acme.atlassian.net/browse/ITSUP-1001',
      });
      expect(link).toHaveAttribute('href', 'https://acme.atlassian.net/browse/ITSUP-1001');
      expect(link).toHaveAttribute('target', '_blank');
    }

    it('AC-002.2 shows the comments with author and date and the link to the issue in Jira while writing its Solution', async () => {
      const { user } = await openPreview();
      const dialog = await openRow(user, 1);
      expectSnapshot(dialog);
      expect(within(dialog).getByRole('textbox', { name: 'Symptoms' })).toBeInTheDocument();
    });

    it('AC-002.2 shows the comments and the link to Jira for a Removed issue, read-only', async () => {
      await removeImportPreviewRows(1, { rowNumbers: [1] });
      const { user } = await openPreview();
      const dialog = await openRow(user, 1);
      expectSnapshot(dialog);
      expect(within(dialog).queryByRole('textbox')).not.toBeInTheDocument();
    });
  });
});
