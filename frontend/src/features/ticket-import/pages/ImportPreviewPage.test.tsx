import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
import { seedPreview } from '../mocks/data';

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
});
