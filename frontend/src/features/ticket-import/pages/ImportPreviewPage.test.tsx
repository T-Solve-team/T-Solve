import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http } from 'msw';
import { server } from '@/mocks/server';
import { fillImportPreviewSolution, type FillSolutionRequest } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
import { seedPreview } from '../mocks/data';
import { seedFileWithoutSolutions } from '../mocks/handlers';

/** The seed preview of the mock backend: 25 rows, 20 Valid, 4 Error, 1 Removed (mocks/data.ts). */
const PATH = '/import/preview?previewId=1';

function rowOf(rowNo: number): HTMLElement {
  const row = screen.getByLabelText(`Select row ${rowNo}`).closest('tr');
  if (!row) throw new Error(`no row ${rowNo}`);
  return row;
}

/** The Summary's StatCard with this label. */
function statCard(label: 'Valid' | 'Error' | 'Removed' | 'Needs Solution'): HTMLElement {
  const card = screen.getByText(label, { selector: '.ant-typography' }).closest('.ant-card');
  if (!(card instanceof HTMLElement)) throw new Error(`no ${label} card`);
  return card;
}

/** Counts the confirm requests that reach the mock backend. */
function countConfirms() {
  const calls = { count: 0 };
  server.events.on('request:start', ({ request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname.endsWith('/confirm')) {
      calls.count += 1;
    }
  });
  return calls;
}

async function openPreview() {
  const user = userEvent.setup();
  const view = renderApp(PATH, { role: 'PROJECT_MANAGER' });
  await screen.findByRole('table', { name: 'Import rows' }, { timeout: 5000 });
  await screen.findByText('ITSUP-0871');
  return { user, ...view };
}

describe('ImportPreviewPage', () => {
  beforeAll(async () => {
    await import('./ImportPreviewPage');
  });

  it('AC-009.1 shows the Summary: file name, Valid / Error / Removed counts and MSG30', async () => {
    await openPreview();
    expect(screen.getByText('itsup-resolved-2026-09.csv')).toBeInTheDocument();
    for (const [label, value] of [
      ['Valid', '20'],
      ['Error', '4'],
      ['Removed', '1'],
    ] as const) {
      const card = screen.getByText(label, { selector: '.ant-typography' }).closest('.ant-card');
      expect(card).toHaveTextContent(value);
    }
    expect(screen.getByText(msg('MSG30', { count: 4 }))).toBeInTheDocument();
  });

  it('AC-009.1 shows no MSG30 when no row is Error', async () => {
    server.use(
      http.get('*/api/v1/imports/previews/1', () =>
        Response.json({ ...seedPreview(), errorCount: 0, message: null }),
      ),
    );
    await openPreview();
    expect(screen.queryByText(msg('MSG30', { count: 4 }))).not.toBeInTheDocument();
  });

  it('AC-009.2 lists the rows with Ticket ID, Title, Author, Resolved date, Solution, Result and Error details', async () => {
    await openPreview();
    const table = screen.getByRole('table', { name: 'Import rows' });
    for (const column of [
      'Ticket ID',
      'Title',
      'Author',
      'Resolved date',
      'Solution',
      'Result',
      'Error details',
    ]) {
      expect(within(table).getByRole('columnheader', { name: column })).toBeInTheDocument();
    }

    const valid = rowOf(1);
    expect(valid).toHaveTextContent('ITSUP-0871');
    expect(valid).toHaveTextContent('Outlook keeps asking for password after M365 migration');
    expect(valid).toHaveTextContent('Nguyễn Thị Thu Hà');
    expect(valid).toHaveTextContent('2026-09-12');
    expect(valid).toHaveTextContent('Symptoms');
    expect(valid).toHaveTextContent('Root cause');
    expect(valid).toHaveTextContent('Resolution steps');
    expect(valid).toHaveTextContent('Workaround');
    expect(valid).toHaveTextContent('Scope');
    expect(valid).toHaveTextContent('Valid');

    expect(rowOf(2)).toHaveTextContent(msg('MSG01', { field_name: 'Root cause' }));
    expect(rowOf(3)).toHaveTextContent(msg('MSG_NEW_3', { ticket_id: 'ITSUP-0880' }));
    expect(rowOf(5)).toHaveTextContent(msg('MSG31', { ticket_id: 'ITSUP-0715' }));
    expect(rowOf(6)).toHaveTextContent('Removed');
  });

  it('AC-009.2 shows MSG06 for a row error code the catalog does not know (edge case 38)', async () => {
    const preview = seedPreview();
    const rows = preview.rows.content.map((row) =>
      row.rowNo === 2 ? { ...row, errors: [{ code: 'MSG999', field: null, params: {} }] } : row,
    );
    server.use(
      http.get('*/api/v1/imports/previews/1/rows', () =>
        Response.json({ ...preview.rows, content: rows }),
      ),
    );
    await openPreview();
    expect(rowOf(2)).toHaveTextContent(msg('MSG06'));
  });

  it('AC-009.2 lists 20 rows per page and shows the next page', async () => {
    const { user } = await openPreview();
    const table = screen.getByRole('table', { name: 'Import rows' });
    expect(within(table).getAllByRole('checkbox', { name: /^Select row/ })).toHaveLength(20);
    expect(screen.getByText('Total 25 items')).toBeInTheDocument();

    await user.click(screen.getByRole('listitem', { name: '2' }));

    expect(await screen.findByLabelText('Select row 21')).toBeInTheDocument();
    expect(screen.queryByLabelText('Select row 1')).not.toBeInTheDocument();
  });

  it('AC-009.3 lets the user select rows except Removed ones', async () => {
    const { user } = await openPreview();
    await user.click(screen.getByLabelText('Select row 1'));
    expect(screen.getByLabelText('Select row 1')).toBeChecked();
    expect(screen.getByLabelText('Select row 6')).toBeDisabled();
  });

  it.each(['', '?previewId=abc', '?previewId=0', '?previewId=-3', '?previewId=1.5'])(
    'AC-009.11 opens Import Tickets for the address /import/preview%s (edge case 33)',
    async (search) => {
      const { router } = renderApp(`/import/preview${search}`, { role: 'PROJECT_MANAGER' });
      await waitFor(() => expect(router.state.location.pathname).toBe('/import'), {
        timeout: 5000,
      });
    },
  );

  it('answers MSG04 for a preview or row that is gone, like the backend (BUG-107)', async () => {
    for (const path of ['/previews/99', '/previews/99/rows', '/previews/1/rows/999']) {
      const response = await fetch(`http://localhost/api/v1/imports${path}`);
      expect(response.status).toBe(404);
      expect(await response.json()).toMatchObject({ code: 'MSG04' });
    }
  });

  it('AC-009.12 shows MSG04 instead of the Summary and rows when the preview does not exist', async () => {
    renderApp('/import/preview?previewId=99', { role: 'PROJECT_MANAGER' });
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG04'));
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('AC-009.12 shows MSG08 when the caller may not read the preview', async () => {
    server.use(http.get('*/api/v1/imports/previews/1', () => problem(403, 'MSG08')));
    renderApp(PATH, { role: 'PROJECT_MANAGER' });
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG08'));
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('AC-009.13 shows the loading state while the Summary is loading', async () => {
    server.use(
      http.get('*/api/v1/imports/previews/1', async () => {
        await delay('infinite');
        return problem(500);
      }),
    );
    renderApp(PATH, { role: 'PROJECT_MANAGER' });
    expect(await screen.findByText('Loading ...', {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('AC-009.13 shows the loading state while a page of rows is loading', async () => {
    server.use(
      http.get('*/api/v1/imports/previews/1/rows', async () => {
        await delay('infinite');
        return problem(500);
      }),
    );
    renderApp(PATH, { role: 'PROJECT_MANAGER' });
    const table = await screen.findByRole('table', { name: 'Import rows' }, { timeout: 5000 });
    const wrapper = table.closest('.ant-table-wrapper');
    await waitFor(() => expect(wrapper?.querySelector('.ant-spin-spinning')).not.toBeNull());
  });
  describe('actions', () => {
    afterEach(() => {
      server.events.removeAllListeners();
    });

    it('AC-009.3 disables Remove while no row is selected', async () => {
      const { user } = await openPreview();
      const remove = screen.getByRole('button', { name: 'Remove' });
      expect(remove).toBeDisabled();
      await user.click(screen.getByLabelText('Select row 1'));
      expect(remove).toBeEnabled();
    });

    it('AC-009.3 removes the selected rows, refreshes the Summary and rows and clears the selection', async () => {
      const { user } = await openPreview();
      await user.click(screen.getByLabelText('Select row 1'));
      await user.click(screen.getByLabelText('Select row 2'));

      await user.click(screen.getByRole('button', { name: 'Remove' }));

      await waitFor(() => expect(statCard('Removed')).toHaveTextContent('3'));
      expect(statCard('Valid')).toHaveTextContent('19');
      expect(statCard('Error')).toHaveTextContent('3');
      await waitFor(() => expect(screen.getByLabelText('Select row 1')).toBeDisabled());
      expect(rowOf(1)).toHaveTextContent('Removed');
      expect(rowOf(2)).toHaveTextContent('Removed');
      expect(screen.queryAllByRole('checkbox', { checked: true })).toHaveLength(0);
      // jsdom never ends antd's fade-out of the loading icon, so "loading" stays in the button's name.
      expect(screen.getByRole('button', { name: /Remove/ })).toBeDisabled();
    });

    it('AC-009.5 disables Confirm while the preview has no Valid row (edge case 35)', async () => {
      server.use(
        http.get('*/api/v1/imports/previews/1', () =>
          Response.json({ ...seedPreview(), validCount: 0 }),
        ),
      );
      await openPreview();
      expect(screen.getByRole('button', { name: 'Confirm' })).toBeDisabled();
    });

    it('AC-009.6 shows MSG32 with the counts and opens Import Tickets without asking MSG37 (edge case 36)', async () => {
      const { user, router } = await openPreview();

      await user.click(screen.getByRole('button', { name: 'Confirm' }));

      expect(
        await screen.findByText(msg('MSG32', { created: 20, skipped: 5 })),
      ).toBeInTheDocument();
      await waitFor(() => expect(router.state.location.pathname).toBe('/import'));
      expect(screen.queryByText(msg('MSG37'))).not.toBeInTheDocument();
    });

    it.each([
      [409, 'MSG30', { count: 4 }],
      [409, 'MSG40', {}],
      [409, 'MSG33', {}],
      [500, 'MSG33', {}],
    ] as const)(
      'AC-009.7 shows %s %s from Confirm and stays on the Import Preview',
      async (status, code, params) => {
        server.use(
          http.post('*/api/v1/imports/previews/1/confirm', () => problem(status, code, { params })),
        );
        const { user, router } = await openPreview();

        await user.click(screen.getByRole('button', { name: 'Confirm' }));

        // The toast: MSG30 is also on the Summary.
        await waitFor(() =>
          expect(document.querySelector('.ant-message')).toHaveTextContent(msg(code, params)),
        );
        expect(router.state.location.pathname).toBe('/import/preview');
        expect(screen.getByRole('table', { name: 'Import rows' })).toBeInTheDocument();
      },
    );

    it('shows only the MSG07 dialog, not a toast, when Confirm answers 401 (BUG-123)', async () => {
      server.use(http.post('*/api/v1/imports/previews/1/confirm', () => problem(401, 'MSG07')));
      const { user } = await openPreview();

      await user.click(screen.getByRole('button', { name: 'Confirm' }));

      expect(await screen.findByRole('dialog')).toHaveTextContent(msg('MSG07'));
      expect(document.querySelector('.ant-message')).not.toBeInTheDocument();
    });

    it('AC-009.8 shows Confirm as loading and sends one Confirm for a double click (edge case 34)', async () => {
      let answer: () => void = () => {};
      const answered = new Promise<void>((resolve) => {
        answer = resolve;
      });
      server.use(
        http.post('*/api/v1/imports/previews/1/confirm', async () => {
          await answered;
          return problem(409, 'MSG40');
        }),
      );
      const confirms = countConfirms();
      const { user } = await openPreview();
      const confirm = screen.getByRole('button', { name: 'Confirm' });

      await user.dblClick(confirm);

      await waitFor(() => expect(confirm).toHaveClass('ant-btn-loading'));
      expect(confirms.count).toBe(1);
      answer();
      await waitFor(() => expect(confirm).not.toHaveClass('ant-btn-loading'));
      expect(confirms.count).toBe(1);
    });

    it('AC-009.9 asks MSG37 on Cancel: Cancel in the dialog stays, OK opens Import Tickets without importing', async () => {
      const confirms = countConfirms();
      const { user, router } = await openPreview();
      // The page's Cancel; the dialog's own Cancel stays in the DOM while it fades out.
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
      expect(confirms.count).toBe(0);
    });

    it('AC-009.10 asks MSG37 before leaving for another screen and leaves only on OK', async () => {
      const { user, router } = await openPreview();

      await act(() => router.navigate('/my-profile'));
      let dialog = await screen.findByRole('dialog');
      expect(dialog).toHaveTextContent(msg('MSG37'));
      await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
      expect(router.state.location.pathname).toBe('/import/preview');

      await act(() => router.navigate('/my-profile'));
      dialog = await screen.findByRole('dialog');
      await user.click(within(dialog).getByRole('button', { name: 'OK' }));

      await waitFor(() => expect(router.state.location.pathname).toBe('/my-profile'));
    });
  });
  describe('Import Ticket Preview popup', () => {
    async function openPopup(rowNo: number) {
      const view = await openPreview();
      await view.user.click(within(rowOf(rowNo)).getByRole('button', { name: 'Preview' }));
      const dialog = await screen.findByRole('dialog', { name: 'Import Ticket Preview' });
      return { ...view, dialog };
    }

    it('AC-009.4 AC-010.1 shows the row read-only: every field, the Solution sections in full, Result and Error details', async () => {
      const { dialog } = await openPopup(2);
      await within(dialog).findByText('ITSUP-0872');
      for (const text of [
        'VPN drops during Teams calls',
        'Users report the problem after the latest Windows update.',
        'Phạm Quốc Bảo',
        '2026-09-14',
        'Outlook asks for the password again every few minutes after the M365 migration.',
        '1. Remove the Outlook profile. 2. Clear the cached credentials. 3. Create the profile again.',
        'Use Outlook on the web until the profile is recreated.',
        'Windows laptops with Outlook 2019 or later.',
        'Error',
        msg('MSG01', { field_name: 'Root cause' }),
      ]) {
        expect(within(dialog).getByText(text)).toBeInTheDocument();
      }
      for (const label of [
        'Ticket ID',
        'Title',
        'Description',
        'Author',
        'Resolved date',
        'Symptoms',
        'Root cause',
        'Resolution steps',
        'Workaround',
        'Scope',
        'Result',
        'Error details',
      ]) {
        expect(within(dialog).getByText(label)).toBeInTheDocument();
      }
      expect(within(dialog).queryByRole('textbox')).not.toBeInTheDocument();
    });

    it('AC-010.2 closes on Close and keeps the Import Preview as it was', async () => {
      const user = userEvent.setup();
      const { router } = renderApp(PATH, { role: 'PROJECT_MANAGER' });
      await screen.findByText('ITSUP-0871', undefined, { timeout: 5000 });
      await user.click(screen.getByLabelText('Select row 1'));
      await user.click(within(rowOf(3)).getByRole('button', { name: 'Preview' }));
      const dialog = await screen.findByRole('dialog', { name: 'Import Ticket Preview' });
      await within(dialog).findByText('Printer on floor 7 shows offline');

      // The header's close icon is also named "Close"; the footer button is the last one.
      const footerClose = within(dialog).getAllByRole('button', { name: 'Close' }).pop();
      if (!footerClose) throw new Error('no Close button');
      await user.click(footerClose);

      // jsdom runs no CSS animation, so antd's fade-out never ends and the popup stays in the DOM, closing.
      await waitFor(() => expect(dialog).toHaveClass('ant-zoom-leave'));
      expect(screen.queryByText(msg('MSG37'))).not.toBeInTheDocument();
      expect(router.state.location.pathname).toBe('/import/preview');
      expect(screen.getByLabelText('Select row 1')).toBeChecked();
    });

    it('AC-010.3 shows the error state inside the popup when the row cannot be read', async () => {
      server.use(http.get('*/api/v1/imports/previews/1/rows/:rowNo', () => problem(500)));
      const { dialog } = await openPopup(1);
      expect(await within(dialog).findByRole('alert')).toHaveTextContent(msg('MSG06'));
      expect(screen.getByRole('table', { name: 'Import rows' })).toBeInTheDocument();
    });
  });

  describe('Remove and Confirm together', () => {
    /** Holds the answer of `path` until the returned function is called. */
    function holdAnswer(path: string) {
      let answer: () => void = () => {};
      const answered = new Promise<void>((resolve) => {
        answer = resolve;
      });
      server.use(
        http.post(`*/api/v1/imports/previews/1/${path}`, async () => {
          await answered;
          return problem(500, 'MSG06');
        }),
      );
      return answer;
    }

    it('disables Confirm while a Remove is running (BUG-108)', async () => {
      const answer = holdAnswer('remove-rows');
      const { user } = await openPreview();
      await user.click(screen.getByLabelText('Select row 1'));

      await user.click(screen.getByRole('button', { name: 'Remove' }));

      await waitFor(() => expect(screen.getByRole('button', { name: /Confirm/ })).toBeDisabled());
      answer();
    });

    it('disables Remove while a Confirm is running (BUG-108)', async () => {
      const answer = holdAnswer('confirm');
      const { user } = await openPreview();
      await user.click(screen.getByLabelText('Select row 1'));

      await user.click(screen.getByRole('button', { name: 'Confirm' }));

      await waitFor(() => expect(screen.getByRole('button', { name: /Remove/ })).toBeDisabled());
      answer();
    });

    it('does not show a confirmed preview again on browser Back (BUG-115)', async () => {
      const { user, router } = await openPreview();
      await user.click(screen.getByRole('button', { name: 'Confirm' }));
      await waitFor(() => expect(router.state.location.pathname).toBe('/import'));

      await act(() => router.navigate(-1));

      // findByText: the MSG32 toast is also an alert.
      expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
      expect(screen.queryByText('itsup-resolved-2026-09.csv')).not.toBeInTheDocument();
    });
  });

  describe('a file without Solutions', () => {
    beforeEach(() => {
      // 20 Needs Solution, 4 Error and 1 Removed row; Solution columns empty
      seedFileWithoutSolutions();
    });

    afterEach(() => {
      server.events.removeAllListeners();
    });

    const solution = {
      symptoms: 'Outlook asks for the password',
      rootCause: 'Stale cached credentials',
      resolutionSteps: 'Recreate the Outlook profile',
      scope: 'Outlook 2019 users',
    } satisfies Omit<FillSolutionRequest, 'rowNumbers'>;

    function recordFills() {
      const saved = vi.fn();
      server.events.on('request:start', ({ request }) => {
        if (request.method === 'POST' && new URL(request.url).pathname.endsWith('/fill-solution')) {
          void request.clone().json().then(saved);
        }
      });
      return saved;
    }

    async function openFillPopup(user: ReturnType<typeof userEvent.setup>) {
      await user.click(screen.getByRole('button', { name: 'Fill solution' }));
      return screen.findByRole('dialog', { name: 'Import Ticket Preview' });
    }

    async function replacementDialog(count: number) {
      // antd uses the same title id for both modals in test mode; find this dialog by its message.
      const dialog = (await screen.findByText(msg('MSG36', { count }))).closest('[role="dialog"]');
      if (!(dialog instanceof HTMLElement)) throw new Error('no replacement dialog');
      return dialog;
    }

    async function openSolutionPopup(rowNo: number) {
      const view = await openPreview();
      await view.user.click(within(rowOf(rowNo)).getByRole('button', { name: 'Preview' }));
      const dialog = await screen.findByRole('dialog', { name: 'Import Ticket Preview' });
      await within(dialog).findByText('ITSUP-0871');
      return { ...view, dialog };
    }

    function writeSolution(dialog: HTMLElement) {
      for (const [label, text] of [
        ['Symptoms', 'Outlook asks for the password'],
        ['Root cause', 'Stale cached credentials'],
        ['Resolution steps', 'Recreate the Outlook profile'],
        ['Scope', 'Outlook 2019 users'],
      ] as const) {
        const field = within(dialog).getByRole('textbox', { name: label });
        fireEvent.change(field, { target: { value: text } });
      }
    }

    it('AC-015.4 hides Fill solution for a file with Solutions', async () => {
      server.use(http.get('*/api/v1/imports/previews/1', () => Response.json(seedPreview())));
      await openPreview();
      expect(screen.queryByRole('button', { name: 'Fill solution' })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Confirm' })).toBeEnabled();
    });

    it('AC-015.4 enables Fill solution only for a nonempty selection without Error or Removed rows', async () => {
      await fillImportPreviewSolution(1, { ...solution, rowNumbers: [7] });
      const { user } = await openPreview();
      const fill = screen.getByRole('button', { name: 'Fill solution' });
      expect(fill).toBeDisabled();
      expect(screen.getByLabelText('Select row 6')).toBeDisabled();

      await user.click(screen.getByLabelText('Select row 1'));
      expect(fill).toBeEnabled();
      await user.click(screen.getByLabelText('Select row 7'));
      expect(fill).toBeEnabled();
      await user.click(screen.getByLabelText('Select row 1'));
      expect(fill).toBeEnabled();
      await user.click(screen.getByLabelText('Select row 2'));
      expect(fill).toBeDisabled();
      await user.click(screen.getByLabelText('Select row 7'));
      expect(fill).toBeDisabled();
    });

    it('AC-015.4 keeps Fill solution disabled when an Error row is selected on another page', async () => {
      const { user } = await openPreview();
      await user.click(screen.getByLabelText('Select row 2'));
      await user.click(screen.getByTitle('2'));
      await screen.findByLabelText('Select row 21');
      await user.click(screen.getByLabelText('Select row 21'));
      expect(screen.getByRole('button', { name: 'Fill solution' })).toBeDisabled();
    });

    it('AC-015.5 opens empty sections, fills selected Needs Solution rows without MSG36 and clears the selection', async () => {
      const saved = recordFills();
      const { user } = await openPreview();
      for (const rowNo of [1, 7]) {
        await user.click(screen.getByLabelText(`Select row ${rowNo}`));
      }
      const dialog = await openFillPopup(user);
      for (const label of ['Symptoms', 'Root cause', 'Resolution steps', 'Workaround', 'Scope']) {
        expect(within(dialog).getByRole('textbox', { name: label })).toHaveValue('');
      }
      expect(within(dialog).queryByText('Ticket ID')).not.toBeInTheDocument();
      writeSolution(dialog);
      await user.click(within(dialog).getByRole('button', { name: 'Apply' }));

      await waitFor(() => expect(statCard('Valid')).toHaveTextContent('2'));
      expect(saved).toHaveBeenCalledExactlyOnceWith({ ...solution, rowNumbers: [1, 7] });
      expect(statCard('Needs Solution')).toHaveTextContent('18');
      expect(screen.queryByText(msg('MSG36', { count: 0 }))).not.toBeInTheDocument();
      expect(dialog).not.toBeInTheDocument();
      expect(within(rowOf(1)).getByText('Valid')).toBeInTheDocument();
      expect(within(rowOf(7)).getByText('Valid')).toBeInTheDocument();
      expect(screen.queryAllByRole('checkbox', { checked: true })).toHaveLength(0);
      expect(screen.getByRole('button', { name: 'Fill solution' })).toBeDisabled();
    });

    it('AC-015.5 counts selected Valid rows across pages in MSG36, saves nothing on refusal and replaces on OK (edge case 50)', async () => {
      await fillImportPreviewSolution(1, {
        ...solution,
        symptoms: 'Previously saved symptoms',
        rowNumbers: [1, 21],
      });
      const saved = recordFills();
      const { user } = await openPreview();
      for (const rowNo of [1, 7]) {
        await user.click(screen.getByLabelText(`Select row ${rowNo}`));
      }
      await user.click(screen.getByTitle('2'));
      await screen.findByLabelText('Select row 21');
      for (const rowNo of [21, 22]) {
        await user.click(screen.getByLabelText(`Select row ${rowNo}`));
      }
      const dialog = await openFillPopup(user);
      expect(within(dialog).getByRole('textbox', { name: 'Symptoms' })).toHaveValue('');
      writeSolution(dialog);
      await user.click(within(dialog).getByRole('button', { name: 'Apply' }));

      let replace = await replacementDialog(2);
      expect(saved).not.toHaveBeenCalled();
      await user.click(within(replace).getByRole('button', { name: 'Cancel' }));
      expect(saved).not.toHaveBeenCalled();
      expect(dialog).not.toHaveClass('ant-zoom-leave');
      expect(within(dialog).getByRole('textbox', { name: 'Symptoms' })).toHaveValue(
        solution.symptoms,
      );
      expect(screen.getByLabelText('Select row 21')).toBeChecked();

      await user.click(within(dialog).getByRole('button', { name: 'Apply' }));
      replace = await replacementDialog(2);
      await user.click(within(replace).getByRole('button', { name: 'OK' }));

      await waitFor(() => expect(statCard('Valid')).toHaveTextContent('4'));
      expect(saved).toHaveBeenCalledExactlyOnceWith({ ...solution, rowNumbers: [1, 7, 21, 22] });
      expect(statCard('Needs Solution')).toHaveTextContent('16');
      expect(rowOf(21)).toHaveTextContent(solution.symptoms);
      expect(rowOf(22)).toHaveTextContent(solution.symptoms);
      expect(screen.getByLabelText('Select row 21')).not.toBeChecked();
      expect(screen.getByLabelText('Select row 22')).not.toBeChecked();
      await user.click(screen.getByTitle('1'));
      await screen.findByLabelText('Select row 1');
      expect(screen.getByLabelText('Select row 1')).not.toBeChecked();
      expect(screen.getByLabelText('Select row 7')).not.toBeChecked();
    });

    it('AC-015.5 counts a selected row as Valid after its Solution is saved through Preview', async () => {
      const { user } = await openPreview();
      await user.click(screen.getByLabelText('Select row 1'));
      await user.click(within(rowOf(1)).getByRole('button', { name: 'Preview' }));
      const rowDialog = await screen.findByRole('dialog', { name: 'Import Ticket Preview' });
      await within(rowDialog).findByText('ITSUP-0871');
      writeSolution(rowDialog);
      await user.click(within(rowDialog).getByRole('button', { name: 'Apply' }));
      await waitFor(() => expect(statCard('Valid')).toHaveTextContent('1'));

      const dialog = await openFillPopup(user);
      expect(within(dialog).getByRole('textbox', { name: 'Symptoms' })).toHaveValue('');
      writeSolution(dialog);
      await user.click(within(dialog).getByRole('button', { name: 'Apply' }));
      expect(await replacementDialog(1)).toHaveTextContent(msg('MSG36', { count: 1 }));
    });

    it('AC-015.5 keeps the bulk Solution and selection when Apply is rejected', async () => {
      server.use(
        http.post('*/api/v1/imports/previews/1/fill-solution', () =>
          problem(400, 'MSG01', {
            fieldErrors: [{ field: 'scope', code: 'MSG01', params: { field_name: 'Scope' } }],
          }),
        ),
      );
      const { user } = await openPreview();
      await user.click(screen.getByLabelText('Select row 1'));
      const dialog = await openFillPopup(user);
      writeSolution(dialog);
      await user.click(within(dialog).getByRole('button', { name: 'Apply' }));

      expect(
        await within(dialog).findByText(msg('MSG01', { field_name: 'Scope' })),
      ).toBeInTheDocument();
      expect(dialog).not.toHaveClass('ant-zoom-leave');
      expect(within(dialog).getByRole('textbox', { name: 'Symptoms' })).toHaveValue(
        solution.symptoms,
      );
      expect(screen.getByLabelText('Select row 1')).toBeChecked();
      expect(statCard('Valid')).toHaveTextContent('0');
    });

    it.each([
      [2, 0],
      [0, 3],
      [2, 3],
    ])(
      'AC-015.6 blocks Confirm and shows MSG_NEW_4 for %i Needs Solution and %i Error rows',
      async (needsSolutionCount, errorCount) => {
        server.use(
          http.get('*/api/v1/imports/previews/1', () =>
            Response.json({
              ...seedPreview(),
              importType: 'WITHOUT_SOLUTION',
              validCount: 1,
              needsSolutionCount,
              errorCount,
            }),
          ),
        );
        await openPreview();
        expect(screen.getByRole('button', { name: 'Confirm' })).toBeDisabled();
        expect(
          screen
            .getByText(msg('MSG_NEW_4', { count: needsSolutionCount + errorCount }))
            .closest('[role="alert"]'),
        ).toBeInTheDocument();
      },
    );

    it.each([1, 0])(
      'AC-015.6 removes the readiness alert when only Valid and Removed rows remain (Valid count %i)',
      async (validCount) => {
        server.use(
          http.get('*/api/v1/imports/previews/1', () =>
            Response.json({
              ...seedPreview(),
              importType: 'WITHOUT_SOLUTION',
              validCount,
              needsSolutionCount: 0,
              errorCount: 0,
              message: null,
            }),
          ),
        );
        await openPreview();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
        const confirm = screen.getByRole('button', { name: 'Confirm' });
        if (validCount > 0) expect(confirm).toBeEnabled();
        else expect(confirm).toBeDisabled();
      },
    );

    it('AC-015.7 shows the Needs Solution count in the Summary and Needs Solution as a Result', async () => {
      await openPreview();
      expect(statCard('Needs Solution')).toHaveTextContent('20');
      expect(statCard('Valid')).toHaveTextContent('0');
      expect(within(rowOf(1)).getByText('Needs Solution')).toBeInTheDocument();
    });

    it('AC-015.7 shows no Needs Solution count for a file with Solutions', async () => {
      server.use(http.get('*/api/v1/imports/previews/1', () => Response.json(seedPreview())));
      await openPreview();
      expect(screen.queryByText('Needs Solution', { selector: '.ant-typography' })).toBeNull();
    });

    it('AC-015.2 opens a Needs Solution row with editable Solution sections and Apply', async () => {
      const { dialog } = await openSolutionPopup(1);

      expect(
        within(dialog).getByText('Outlook keeps asking for password after M365 migration'),
      ).toBeInTheDocument();
      for (const label of ['Symptoms', 'Root cause', 'Resolution steps', 'Workaround', 'Scope']) {
        expect(within(dialog).getByRole('textbox', { name: label })).toHaveValue('');
      }
      expect(within(dialog).getByRole('button', { name: 'Apply' })).toBeEnabled();
    });

    it('AC-015.2 saves the Solution on Apply, closes the popup and refreshes the Summary and rows', async () => {
      const { user, dialog } = await openSolutionPopup(1);
      writeSolution(dialog);

      await user.click(within(dialog).getByRole('button', { name: 'Apply' }));

      // jsdom runs no CSS animation, so antd's fade-out never ends and the popup stays in the DOM, closing.
      await waitFor(() => expect(dialog).toHaveClass('ant-zoom-leave'));
      await waitFor(() => expect(statCard('Valid')).toHaveTextContent('1'));
      expect(statCard('Needs Solution')).toHaveTextContent('19');
      await waitFor(() => expect(within(rowOf(1)).getByText('Valid')).toBeInTheDocument());
    });

    it('AC-015.2 shows the saved Solution again after a reload (edge case 51)', async () => {
      const first = await openSolutionPopup(1);
      writeSolution(first.dialog);
      await first.user.click(within(first.dialog).getByRole('button', { name: 'Apply' }));
      await waitFor(() => expect(statCard('Valid')).toHaveTextContent('1'));
      first.unmount();

      const { dialog } = await openSolutionPopup(1);

      expect(within(dialog).getByRole('textbox', { name: 'Symptoms' })).toHaveValue(
        'Outlook asks for the password',
      );
      expect(within(dialog).getByRole('button', { name: 'Apply' })).toBeInTheDocument();
    });

    it('AC-015.3 shows the backend message at its field and keeps the popup open with what was written', async () => {
      server.use(
        http.post('*/api/v1/imports/previews/1/fill-solution', () =>
          problem(400, 'MSG01', {
            fieldErrors: [{ field: 'scope', code: 'MSG01', params: { field_name: 'Scope' } }],
          }),
        ),
      );
      const { user, dialog } = await openSolutionPopup(1);
      writeSolution(dialog);

      await user.click(within(dialog).getByRole('button', { name: 'Apply' }));

      expect(
        await within(dialog).findByText(msg('MSG01', { field_name: 'Scope' })),
      ).toBeInTheDocument();
      expect(screen.getByRole('dialog', { name: 'Import Ticket Preview' })).toBeInTheDocument();
      expect(within(dialog).getByRole('textbox', { name: 'Symptoms' })).toHaveValue(
        'Outlook asks for the password',
      );
    });

    it('AC-015.2 keeps an Error row read-only', async () => {
      const view = await openPreview();
      await view.user.click(within(rowOf(2)).getByRole('button', { name: 'Preview' }));
      const dialog = await screen.findByRole('dialog', { name: 'Import Ticket Preview' });

      await within(dialog).findByText('ITSUP-0872');
      expect(within(dialog).queryByRole('button', { name: 'Apply' })).toBeNull();
      expect(within(dialog).queryByRole('textbox')).toBeNull();
    });
  });
});
